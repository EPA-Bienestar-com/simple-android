package org.simple.clinic.summary.addphone

import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.withLatestFrom
import org.simple.clinic.ReplayUntilScreenIsDestroyed
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.patient.PatientPhoneNumberType.Mobile
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.registration.phone.PhoneNumberValidator
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.BLANK
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.LENGTH_TOO_LONG
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.LENGTH_TOO_SHORT
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.VALID
import org.simple.clinic.registration.phone.PhoneNumberValidator.Type.LANDLINE_OR_MOBILE
import org.simple.clinic.uuid.UuidGenerator
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

typealias Ui = AddPhoneNumberDialog
typealias UiChange = (Ui) -> Unit

class AddPhoneNumberDialogController @Inject constructor(
    private val repository: PatientRepository,
    private val validator: PhoneNumberValidator,
    private val uuidGenerator: UuidGenerator
) : ObservableTransformer<UiEvent, UiChange> {

  override fun apply(events: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = ReplayUntilScreenIsDestroyed(events)
        .compose(ReportAnalyticsEvents())
        .replay()

    return addPhoneNumberToPatient(replayedEvents)
  }

  private fun addPhoneNumberToPatient(events: Observable<UiEvent>): Observable<UiChange> {
    val patientUuidStream = events
        .ofType<AddPhoneNumberDialogCreated>()
        .map { it.patientUuid }

    val newNumberAndValidationResult = events
        .ofType<AddPhoneNumberSaveClicked>()
        .map { it.number to validator.validate(it.number, type = LANDLINE_OR_MOBILE) }

    val showValidationError = newNumberAndValidationResult
        .map<UiChange> { (_, result) ->
          when (result) {
            VALID -> { _: Ui -> }
            BLANK, LENGTH_TOO_SHORT -> { ui: Ui -> ui.showPhoneNumberTooShortError() }
            LENGTH_TOO_LONG -> { ui: Ui -> ui.showPhoneNumberTooLongError() }
          }
        }

    val saveNumber = newNumberAndValidationResult
        .filter { (_, result) -> result == VALID }
        .map { (newNumber, _) -> newNumber }
        .withLatestFrom(patientUuidStream)
        .flatMap { (newNumber, patientUuid) ->
          repository
              .createPhoneNumberForPatient(
                  uuid = uuidGenerator.v4(),
                  patientUuid = patientUuid,
                  number = newNumber,
                  phoneNumberType = Mobile,
                  active = true
              )
              .andThen(Observable.just({ ui: Ui -> ui.dismiss() }))
        }

    return saveNumber.mergeWith(showValidationError)
  }
}
