package org.simple.clinic.activity

import com.f2prateek.rx.preferences2.Preference
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.Single
import io.reactivex.rxkotlin.ofType
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.forgotpin.createnewpin.ForgotPinCreateNewPinScreen
import org.simple.clinic.home.HomeScreen
import org.simple.clinic.login.applock.AppLockConfig
import org.simple.clinic.onboarding.OnboardingScreen
import org.simple.clinic.registration.phone.RegistrationPhoneScreen
import org.simple.clinic.router.screen.FullScreenKey
import org.simple.clinic.user.NewlyVerifiedUser
import org.simple.clinic.user.User
import org.simple.clinic.user.User.LoggedInStatus.LOGGED_IN
import org.simple.clinic.user.User.LoggedInStatus.NOT_LOGGED_IN
import org.simple.clinic.user.User.LoggedInStatus.OTP_REQUESTED
import org.simple.clinic.user.User.LoggedInStatus.RESET_PIN_REQUESTED
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.Just
import org.simple.clinic.widgets.TheActivityLifecycle
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Instant
import javax.inject.Inject
import javax.inject.Named

typealias Ui = TheActivity
typealias UiChange = (Ui) -> Unit

class TheActivityController @Inject constructor(
    private val userSession: UserSession,
    private val appLockConfig: Single<AppLockConfig>,
    @Named("should_lock_after") private val lockAfterTimestamp: Preference<Instant>,
    @Named("onboarding_complete") private val hasUserCompletedOnboarding: Preference<Boolean>
) : ObservableTransformer<UiEvent, UiChange> {

  private val showAppLockForUserStates = setOf(OTP_REQUESTED, LOGGED_IN, RESET_PIN_REQUESTED)

  override fun apply(events: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = events.compose(ReportAnalyticsEvents()).replay().refCount()

    return Observable.mergeArray(
        showAppLock(replayedEvents),
        updateLockTime(replayedEvents),
        displayUserLoggedOutOnOtherDevice(replayedEvents))
  }

  private fun showAppLock(events: Observable<UiEvent>): Observable<UiChange> {
    val replayedCanShowAppLock = events
        .ofType<TheActivityLifecycle.Started>()
        .flatMapMaybe { _ ->
          userSession.loggedInUser()
              .firstElement()
              .filter { it is Just<User> }
              .map { (user) -> user!!.loggedInStatus }
              .defaultIfEmpty(NOT_LOGGED_IN)
        }
        .filter { it in showAppLockForUserStates }
        .map { Instant.now() > lockAfterTimestamp.get() }
        .replay()
        .refCount()

    val showAppLock = replayedCanShowAppLock
        .filter { show -> show }
        .map { { ui: Ui -> ui.showAppLockScreen() } }

    val unsetLockTime = replayedCanShowAppLock
        .filter { show -> !show }
        .flatMap {
          lockAfterTimestamp.delete()
          Observable.empty<UiChange>()
        }

    return unsetLockTime.mergeWith(showAppLock)
  }

  private fun updateLockTime(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<TheActivityLifecycle.Stopped>()
        .filter { userSession.isUserLoggedIn() }
        .filter { !lockAfterTimestamp.isSet }
        .flatMap { _ ->
          appLockConfig
              .flatMapObservable {
                lockAfterTimestamp.set(Instant.now().plusMillis(it.lockAfterTimeMillis))
                Observable.empty<UiChange>()
              }
        }
  }

  private fun displayUserLoggedOutOnOtherDevice(events: Observable<UiEvent>): Observable<UiChange> {
    return events.ofType<TheActivityLifecycle.Started>()
        .flatMap { userSession.loggedInUser() }
        .compose(NewlyVerifiedUser())
        .map { { ui: Ui -> ui.showUserLoggedOutOnOtherDeviceAlert() } }
  }

  fun initialScreenKey(): FullScreenKey {
    val localUser = userSession.loggedInUser().blockingFirst().toNullable()

    val canMoveToHomeScreen = when (localUser?.loggedInStatus) {
      NOT_LOGGED_IN, User.LoggedInStatus.RESETTING_PIN -> false
      LOGGED_IN, OTP_REQUESTED, RESET_PIN_REQUESTED -> true
      null -> false
    }

    return when {
      canMoveToHomeScreen -> HomeScreen.KEY
      hasUserCompletedOnboarding.get().not() -> OnboardingScreen.KEY
      else -> {
        return if (localUser?.loggedInStatus == User.LoggedInStatus.RESETTING_PIN) {
          ForgotPinCreateNewPinScreen.KEY()
        } else {
          RegistrationPhoneScreen.KEY
        }
      }
    }
  }
}
