package org.simple.clinic.forgotpin.confirmpin

import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.withLatestFrom
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.user.ForgotPinResult
import org.simple.clinic.user.ForgotPinResult.NetworkError
import org.simple.clinic.user.ForgotPinResult.Success
import org.simple.clinic.user.ForgotPinResult.UnexpectedError
import org.simple.clinic.user.ForgotPinResult.UserNotFound
import org.simple.clinic.user.UserSession
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

typealias Ui = ForgotPinConfirmPinScreen
typealias UiChange = (Ui) -> Unit

class ForgotPinConfirmPinScreenController @Inject constructor(
    private val userSession: UserSession,
    private val facilityRepository: FacilityRepository,
    private val patientRepository: PatientRepository
) : ObservableTransformer<UiEvent, UiChange> {

  override fun apply(upstream: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = upstream.compose(ReportAnalyticsEvents()).replay().refCount()

    return Observable.mergeArray(
        showUserNameOnScreenStarted(replayedEvents),
        showFacilityOnScreenCreated(replayedEvents),
        openFacilityChangeScreen(replayedEvents),
        hideErrorsOnPinTextChanged(replayedEvents),
        showMismatchedPinErrors(replayedEvents),
        showProgress(replayedEvents),
        syncPatientDataAndResetPin(replayedEvents)
    )
  }

  private fun showUserNameOnScreenStarted(events: Observable<UiEvent>): Observable<UiChange> {
    return events.ofType<ForgotPinConfirmPinScreenCreated>()
        .flatMap { userSession.requireLoggedInUser() }
        .map { user -> { ui: Ui -> ui.showUserName(user.fullName) } }
  }

  private fun showFacilityOnScreenCreated(events: Observable<UiEvent>): Observable<UiChange> {
    return events.ofType<ForgotPinConfirmPinScreenCreated>()
        .flatMap { userSession.requireLoggedInUser() }
        .switchMap { facilityRepository.currentFacility(it) }
        .map { facility -> { ui: Ui -> ui.showFacility(facility.name) } }
  }

  private fun openFacilityChangeScreen(events: Observable<UiEvent>): Observable<UiChange> {
    return events.ofType<ForgotPinConfirmPinScreenFacilityClicked>()
        .map { { ui: Ui -> ui.openFacilityChangeScreen() } }
  }

  private fun hideErrorsOnPinTextChanged(events: Observable<UiEvent>): Observable<UiChange> {
    return events.ofType<ForgotPinConfirmPinTextChanged>()
        .map { { ui: Ui -> ui.hideError() } }
  }

  private fun showMismatchedPinErrors(events: Observable<UiEvent>): Observable<UiChange> {
    val previouslyEnteredPin = events.ofType<ForgotPinConfirmPinScreenCreated>()
        .map { it.pin }

    return events.ofType<ForgotPinConfirmPinSubmitClicked>()
        .map { it.pin }
        .withLatestFrom(previouslyEnteredPin)
        .filter { (enteredPin, previousPin) -> enteredPin != previousPin }
        .map { { ui: Ui -> ui.showPinMismatchedError() } }
  }

  private fun showProgress(events: Observable<UiEvent>): Observable<UiChange> {
    val previouslyEnteredPin = events.ofType<ForgotPinConfirmPinScreenCreated>()
        .map { it.pin }

    return events.ofType<ForgotPinConfirmPinSubmitClicked>()
        .map { it.pin }
        .withLatestFrom(previouslyEnteredPin)
        .filter { (enteredPin, previousPin) -> enteredPin == previousPin }
        .map { { ui: Ui -> ui.showProgress() } }
  }

  private fun syncPatientDataAndResetPin(events: Observable<UiEvent>): Observable<UiChange> {
    val previouslyEnteredPin = events.ofType<ForgotPinConfirmPinScreenCreated>()
        .map { it.pin }

    val validPin = events.ofType<ForgotPinConfirmPinSubmitClicked>()
        .map { it.pin }
        .withLatestFrom(previouslyEnteredPin)
        .filter { (enteredPin, previousPin) -> enteredPin == previousPin }
        .map { (enteredPin, _) -> enteredPin }
        .share()

    val makeResetPinCall = validPin
        .flatMapSingle { userSession.syncAndClearData(patientRepository, syncRetryCount = 1).toSingleDefault(it) }
        .flatMapSingle { userSession.resetPin(it) }
        .onErrorReturn { ForgotPinResult.UnexpectedError(it) }
        .share()

    val showErrorsOnResetPinCall = makeResetPinCall
        .filter { it !is Success }
        .map { result ->
          when (result) {
            is NetworkError -> { ui: Ui -> ui.showNetworkError() }
            is UserNotFound, is UnexpectedError -> { ui: Ui -> ui.showUnexpectedError() }
            is Success -> { _: Ui -> }
          }
        }

    val openHomeOnResetPinCallSuccess = makeResetPinCall
        .filter { it is Success }
        .map { { ui: Ui -> ui.goToHomeScreen() } }

    return Observable.mergeArray(
        showErrorsOnResetPinCall,
        openHomeOnResetPinCallSuccess
    )
  }
}
