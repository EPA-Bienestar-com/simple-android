package org.simple.clinic.registration.confirmpin

import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.withLatestFrom
import org.simple.clinic.ReplayUntilScreenIsDestroyed
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.SECURITY_PIN_LENGTH
import org.simple.clinic.user.OngoingRegistrationEntry
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.Just
import org.simple.clinic.util.UtcClock
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

typealias Ui = RegistrationConfirmPinScreen
typealias UiChange = (Ui) -> Unit

class RegistrationConfirmPinScreenController @Inject constructor(
    private val userSession: UserSession,
    private val utcClock: UtcClock
) : ObservableTransformer<UiEvent, UiChange> {

  override fun apply(events: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = ReplayUntilScreenIsDestroyed(events)
        .compose(autoSubmitPin())
        .compose(validatePin())
        .compose(ReportAnalyticsEvents())
        .replay()

    return Observable.merge(
        showValidationError(replayedEvents),
        resetPins(replayedEvents),
        saveConfirmPinAndProceed(replayedEvents))
  }

  private fun autoSubmitPin(): ObservableTransformer<UiEvent, UiEvent> {
    return ObservableTransformer { upstream ->
      val autoSubmits = upstream
          .ofType<RegistrationConfirmPinTextChanged>()
          .distinctUntilChanged()
          .filter { it.confirmPin.length == SECURITY_PIN_LENGTH }
          .map { RegistrationConfirmPinDoneClicked() }
      upstream.mergeWith(autoSubmits)
    }
  }

  private fun validatePin(): ObservableTransformer<UiEvent, UiEvent> {
    return ObservableTransformer { upstream ->
      val doneClicks = upstream.ofType<RegistrationConfirmPinDoneClicked>()

      val pinTextChanges = upstream
          .ofType<RegistrationConfirmPinTextChanged>()
          .map { it.confirmPin }

      val validations = doneClicks
          .withLatestFrom(pinTextChanges) { _, confirmPin -> ongoingRegistrationEntry() to confirmPin }
          .map { (currentEntry, confirmPin) ->
            val valid = currentEntry.pin == confirmPin
            RegistrationConfirmPinValidated(confirmPin, valid)
          }

      upstream.mergeWith(validations)
    }
  }

  private fun showValidationError(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<RegistrationConfirmPinValidated>()
        .filter { it.valid.not() }
        .map {
          { ui: Ui ->
            ui.showPinMismatchError()
            ui.clearPin()
          }
        }
  }

  private fun saveConfirmPinAndProceed(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<RegistrationConfirmPinValidated>()
        .filter { it.valid }
        .map { confirmPinValidated ->
          ongoingRegistrationEntry().withPinConfirmation(
              pinConfirmation = confirmPinValidated.enteredPin,
              clock = utcClock
          )
        }
        .doOnNext(userSession::saveOngoingRegistrationEntry)
        .map { { ui: Ui -> ui.openFacilitySelectionScreen() } }
  }

  private fun resetPins(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<RegistrationResetPinClicked>()
        .map { ongoingRegistrationEntry() }
        .map(OngoingRegistrationEntry::resetPin)
        .doOnNext(userSession::saveOngoingRegistrationEntry)
        .map { { ui: Ui -> ui.goBackToPinScreen() } }
  }

  private fun ongoingRegistrationEntry(): OngoingRegistrationEntry = (userSession.ongoingRegistrationEntry() as Just).value
}
