package org.simple.clinic.registration.phone

import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.ObservableTransformer
import io.reactivex.Single
import org.simple.clinic.facility.FacilitySync
import org.simple.clinic.user.OngoingLoginEntry
import org.simple.clinic.user.OngoingRegistrationEntry
import org.simple.clinic.user.UserSession
import org.simple.clinic.user.finduser.UserLookup
import org.simple.clinic.util.Just
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.scheduler.SchedulersProvider
import org.simple.clinic.uuid.UuidGenerator

class RegistrationPhoneEffectHandler @AssistedInject constructor(
    @Assisted private val uiActions: RegistrationPhoneUiActions,
    private val schedulers: SchedulersProvider,
    private val userSession: UserSession,
    private val uuidGenerator: UuidGenerator,
    private val numberValidator: PhoneNumberValidator,
    private val facilitySync: FacilitySync,
    private val userLookup: UserLookup
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(uiActions: RegistrationPhoneUiActions): RegistrationPhoneEffectHandler
  }

  fun build(): ObservableTransformer<RegistrationPhoneEffect, RegistrationPhoneEvent> {
    return RxMobius
        .subtypeEffectHandler<RegistrationPhoneEffect, RegistrationPhoneEvent>()
        .addConsumer(PrefillFields::class.java, { uiActions.preFillUserDetails(it.entry) }, schedulers.ui())
        .addTransformer(LoadCurrentRegistrationEntry::class.java, loadCurrentRegistrationEntry())
        .addTransformer(CreateNewRegistrationEntry::class.java, createNewRegistrationEntry())
        .addTransformer(ValidateEnteredNumber::class.java, validateEnteredPhoneNumber())
        .addTransformer(SyncFacilities::class.java, syncFacilities())
        .addTransformer(SearchForExistingUser::class.java, findUserByPhoneNumber())
        .addConsumer(ShowAccessDeniedScreen::class.java, { uiActions.showAccessDeniedScreen(it.number) }, schedulers.ui())
        .addTransformer(CreateUserLocally::class.java, createUserLocally())
        .addTransformer(ClearCurrentRegistrationEntry::class.java, clearCurrentRegistrationEntry())
        .addAction(ProceedToLogin::class.java, { uiActions.openLoginPinEntryScreen() }, schedulers.ui())
        .build()
  }

  private fun loadCurrentRegistrationEntry(): ObservableTransformer<LoadCurrentRegistrationEntry, RegistrationPhoneEvent> {
    return ObservableTransformer { effects ->
      effects
          .switchMapSingle { currentOngoingRegistrationEntry() }
          .map(::CurrentRegistrationEntryLoaded)
    }
  }

  private fun currentOngoingRegistrationEntry(): Single<Optional<OngoingRegistrationEntry>> {
    return userSession
        .isOngoingRegistrationEntryPresent()
        .flatMap { isRegistrationEntryPresent ->
          // TODO (vs) 04/06/20: This is nasty, make it a synchronous call
          if (isRegistrationEntryPresent)
            userSession
                .ongoingRegistrationEntry()
                .map { Just(it) }
          else
            Single.just(None<OngoingRegistrationEntry>())
        }
  }

  private fun createNewRegistrationEntry(): ObservableTransformer<CreateNewRegistrationEntry, RegistrationPhoneEvent> {
    return ObservableTransformer { effects ->
      effects
          .map { OngoingRegistrationEntry(uuid = uuidGenerator.v4()) }
          .switchMapSingle { registrationEntry ->
            userSession
                .saveOngoingRegistrationEntry(registrationEntry)
                .andThen(Single.just(registrationEntry))
          }
          .map(::NewRegistrationEntryCreated)
    }
  }

  private fun validateEnteredPhoneNumber(): ObservableTransformer<ValidateEnteredNumber, RegistrationPhoneEvent> {
    return ObservableTransformer { effects ->
      effects
          .map { numberValidator.validate(it.number, PhoneNumberValidator.Type.MOBILE) }
          .map { EnteredNumberValidated.fromValidateNumberResult(it) }
    }
  }

  private fun syncFacilities(): ObservableTransformer<SyncFacilities, RegistrationPhoneEvent> {
    return ObservableTransformer { effects ->
      effects
          .switchMapSingle { facilitySync.pullWithResult().subscribeOn(schedulers.io()) }
          .map { FacilitiesSynced.fromFacilityPullResult(it) }
    }
  }

  private fun findUserByPhoneNumber(): ObservableTransformer<SearchForExistingUser, RegistrationPhoneEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(schedulers.io())
          .map { userLookup.find(it.number) }
          .map { SearchForExistingUserCompleted.fromFindUserResult(it) }
    }
  }

  private fun createUserLocally(): ObservableTransformer<CreateUserLocally, RegistrationPhoneEvent> {
    return ObservableTransformer { effects ->
      effects
          .map {
            OngoingLoginEntry(
                uuid = uuidGenerator.v4(),
                phoneNumber = it.number,
                status = it.status
            )
          }
          .flatMapSingle {
            userSession
                .saveOngoingLoginEntry(it)
                .andThen(Single.just(UserCreatedLocally as RegistrationPhoneEvent))
          }
    }
  }

  private fun clearCurrentRegistrationEntry(): ObservableTransformer<ClearCurrentRegistrationEntry, RegistrationPhoneEvent> {
    return ObservableTransformer { effects ->
      effects
          .flatMapSingle {
            userSession
                .clearOngoingRegistrationEntry()
                .andThen(Single.just(CurrentRegistrationEntryCleared as RegistrationPhoneEvent))
          }
    }
  }
}
