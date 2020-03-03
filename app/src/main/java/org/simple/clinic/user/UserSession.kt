package org.simple.clinic.user

import android.content.SharedPreferences
import com.f2prateek.rx.preferences2.Preference
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.zipWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.analytics.Analytics
import org.simple.clinic.appconfig.Country
import org.simple.clinic.di.AppScope
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.security.PasswordHasher
import org.simple.clinic.storage.files.ClearAllFilesResult
import org.simple.clinic.storage.files.FileStorage
import org.simple.clinic.user.User.LoggedInStatus.LOGGED_IN
import org.simple.clinic.user.User.LoggedInStatus.NOT_LOGGED_IN
import org.simple.clinic.user.User.LoggedInStatus.UNAUTHORIZED
import org.simple.clinic.user.UserStatus.ApprovedForSyncing
import org.simple.clinic.user.UserStatus.WaitingForApproval
import org.simple.clinic.util.Just
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.filterAndUnwrapJust
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

@AppScope
class UserSession @Inject constructor(
    private val facilityRepository: FacilityRepository,
    private val sharedPreferences: SharedPreferences,
    private val appDatabase: AppDatabase,
    private val passwordHasher: PasswordHasher,
    private val ongoingLoginEntryRepository: OngoingLoginEntryRepository,
    private val fileStorage: FileStorage,
    private val reportPendingRecords: ReportPendingRecordsToAnalytics,
    private val selectedCountryPreference: Preference<Optional<Country>>,
    @Named("preference_access_token") private val accessTokenPreference: Preference<Optional<String>>,
    @Named("onboarding_complete") private val onboardingComplete: Preference<Boolean>
) {

  private var ongoingRegistrationEntry: OngoingRegistrationEntry? = null

  fun saveOngoingLoginEntry(entry: OngoingLoginEntry): Completable {
    return ongoingLoginEntryRepository.saveLoginEntry(entry)
  }

  fun ongoingLoginEntry(): Single<OngoingLoginEntry> {
    return ongoingLoginEntryRepository.entry()
  }

  fun clearOngoingLoginEntry() {
    ongoingLoginEntryRepository.clearLoginEntry()
  }

  fun saveOngoingRegistrationEntryAsUser(): Completable {
    val ongoingEntry = ongoingRegistrationEntry().cache()

    return ongoingEntry
        .doOnSubscribe { Timber.i("Logging in from ongoing registration entry") }
        .zipWith(ongoingEntry.flatMap { entry -> passwordHasher.hash(entry.pin!!) })
        .flatMapCompletable { (entry, passwordDigest) ->
          val user = User(
              uuid = entry.uuid!!,
              fullName = entry.fullName!!,
              phoneNumber = entry.phoneNumber!!,
              pinDigest = passwordDigest,
              createdAt = entry.createdAt!!,
              updatedAt = entry.createdAt,
              status = WaitingForApproval,
              loggedInStatus = NOT_LOGGED_IN)
          storeUser(user, entry.facilityId!!)
        }
  }

  private fun userFromPayload(payload: LoggedInUserPayload, status: User.LoggedInStatus): User {
    return payload.run {
      User(
          uuid = uuid,
          fullName = fullName,
          phoneNumber = phoneNumber,
          pinDigest = pinDigest,
          status = this.status,
          createdAt = createdAt,
          updatedAt = updatedAt,
          loggedInStatus = status)
    }
  }

  fun saveOngoingRegistrationEntry(entry: OngoingRegistrationEntry): Completable {
    return Completable.fromAction {
      this.ongoingRegistrationEntry = entry
    }
  }

  fun ongoingRegistrationEntry(): Single<OngoingRegistrationEntry> {
    return Single.fromCallable { ongoingRegistrationEntry }
  }

  fun clearOngoingRegistrationEntry(): Completable {
    return Completable.fromAction {
      ongoingRegistrationEntry = null
    }
  }

  fun isOngoingRegistrationEntryPresent(): Single<Boolean> =
      Single.fromCallable { ongoingRegistrationEntry != null }

  fun storeUserAndAccessToken(userPayload: LoggedInUserPayload, accessToken: String): Completable {
    accessTokenPreference.set(Just(accessToken))
    return storeUser(
        userFromPayload(userPayload, LOGGED_IN),
        userPayload.registrationFacilityId
    )
  }

  fun storeUser(user: User, facilityUuid: UUID): Completable {
    return Completable
        .fromAction { appDatabase.userDao().createOrUpdate(user) }
        .doOnSubscribe { Timber.i("Storing user") }
        .andThen(facilityRepository.associateUserWithFacilities(user, listOf(facilityUuid), currentFacility = facilityUuid))
        .doOnError { Timber.e(it) }
  }

  fun clearLoggedInUser(): Completable {
    Timber.i("Clearing logged-in user")
    return loggedInUser()
        .firstOrError()
        .filter { it is Just<User> }
        .map { (user) -> user!! }
        .flatMapCompletable {
          Completable.fromAction {
            appDatabase.userDao().deleteUserAndFacilityMappings(it, appDatabase.userFacilityMappingDao())
          }
        }
  }

  fun logout(): Single<LogoutResult> {
    return Completable
        .concatArray(
            reportPendingRecords.report().onErrorComplete(),
            clearLocalDatabase(),
            clearSharedPreferences(),
            clearPrivateFiles()
        )
        .toSingleDefault(LogoutResult.Success as LogoutResult)
        .onErrorReturn { cause -> LogoutResult.Failure(cause) }
        .doOnSuccess { Analytics.clearUser() }
  }

  private fun clearLocalDatabase(): Completable {
    return Completable.fromAction { appDatabase.clearAllTables() }
  }

  private fun clearSharedPreferences(): Completable {
    return Completable.fromAction {
      // Retain the saved country when clearing the shared preferences
      val savedCountryData = sharedPreferences.getString(selectedCountryPreference.key(), "")

      sharedPreferences.edit().clear().apply()
      // When we clear all shared preferences, we also end up clearing the flag that states whether
      // the user has completed the onboarding flow or not. This means that if the user opens the
      // again after getting logged out and before logging in, they will be shown the Onboarding
      // screen instead of the Registration phone screen. This is a workaround that sets the flag
      // again after clearing the shared preferences to fix this.
      onboardingComplete.set(true)
      sharedPreferences.edit().putString(selectedCountryPreference.key(), savedCountryData).apply()
    }
  }

  private fun clearPrivateFiles(): Completable {
    return Single
        .fromCallable { fileStorage.clearAllFiles() }
        .flatMapCompletable { result ->
          when (result) {
            is ClearAllFilesResult.Failure -> Completable.error(result.cause)
            else -> Completable.complete()
          }
        }
  }

  fun loggedInUser(): Observable<Optional<User>> {
    return appDatabase.userDao().user()
        .toObservable()
        .map { if (it.isEmpty()) None else Just(it.first()) }
  }

  @Deprecated(
      message = "User can get logged out now and cleared; Don't use this function anymore",
      replaceWith = ReplaceWith("loggedInUser()")
  )
  fun requireLoggedInUser(): Observable<User> {
    /*
    * The earlier version of this method used to throw an error if the returned value from
    * loggedInUser() was None, but we have added the logout feature which clears the database in the
    * background.
    *
    * This means that subscribers can no longer assume that the user will ALWAYS be present in their
    * specific use case, and must explicitly choose to either ignore or handle a User being None.
    *
    * Since this is not a very frequent case that a user will be logged out, we can change this
    * function to instead just not emit anything (instead of throwing an exception) if the user gets
    * cleared and deprecate this function. Callers can choose to migrate in stages since this
    * function is being used EVERYWHERE.
    **/
    return loggedInUser().filterAndUnwrapJust()
  }

  // FIXME: Figure out a better way to add access tokens to network calls in a reactive fashion
  // FIXME: Maybe add a separate RxCallAdapterFactory that lets us transform requests without interceptors?
  // This was added because we needed to call it from the OkHttp Interceptor
  // in a synchronous fashion because the Rx - blocking() call used earlier
  // was causing a deadlock in the Room threads when data sync happened
  fun loggedInUserImmediate() = appDatabase.userDao().userImmediate()

  fun isUserLoggedIn(): Boolean {
    // TODO: This is bad. Make this function return Single<Boolean> instead.
    val user = loggedInUser().blockingFirst()
    return user is Just
  }

  fun accessToken(): Optional<String> {
    return accessTokenPreference.get()
  }

  fun canSyncData(): Observable<Boolean> {
    return loggedInUser()
        .map { (user) ->
          when {
            user?.loggedInStatus == LOGGED_IN && user.status == ApprovedForSyncing -> true
            else -> false
          }
        }
  }

  fun unauthorize(): Completable {
    return loggedInUser()
        .filterAndUnwrapJust()
        .firstOrError()
        .flatMapCompletable { user -> updateLoggedInStatusForUser(user.uuid, UNAUTHORIZED) }
  }

  fun isUserUnauthorized(): Observable<Boolean> {
    return loggedInUser()
        .filterAndUnwrapJust()
        .map { user -> user.loggedInStatus == UNAUTHORIZED }
        .distinctUntilChanged()
  }

  fun isUserDisapproved(): Observable<Boolean> {
    return loggedInUser()
        .filterAndUnwrapJust()
        .map { user -> user.status == UserStatus.DisapprovedForSyncing }
        .distinctUntilChanged()
  }

  fun updateLoggedInStatusForUser(
      userUuid: UUID,
      newLoggedInStatus: User.LoggedInStatus
  ): Completable {
    return Completable.fromAction {
      appDatabase
          .userDao()
          .updateLoggedInStatusForUser(userUuid, newLoggedInStatus)
    }
  }

  sealed class LogoutResult {
    object Success : LogoutResult()
    data class Failure(val cause: Throwable) : LogoutResult()
  }
}
