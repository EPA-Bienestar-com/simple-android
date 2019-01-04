package org.simple.clinic.bp.entry

import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.Single
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.withLatestFrom
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.bp.BloodPressureConfig
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.bp.entry.BloodPressureEntrySheetController.Validation.ERROR_DIASTOLIC_EMPTY
import org.simple.clinic.bp.entry.BloodPressureEntrySheetController.Validation.ERROR_DIASTOLIC_TOO_HIGH
import org.simple.clinic.bp.entry.BloodPressureEntrySheetController.Validation.ERROR_DIASTOLIC_TOO_LOW
import org.simple.clinic.bp.entry.BloodPressureEntrySheetController.Validation.ERROR_SYSTOLIC_EMPTY
import org.simple.clinic.bp.entry.BloodPressureEntrySheetController.Validation.ERROR_SYSTOLIC_LESS_THAN_DIASTOLIC
import org.simple.clinic.bp.entry.BloodPressureEntrySheetController.Validation.ERROR_SYSTOLIC_TOO_HIGH
import org.simple.clinic.bp.entry.BloodPressureEntrySheetController.Validation.ERROR_SYSTOLIC_TOO_LOW
import org.simple.clinic.bp.entry.BloodPressureEntrySheetController.Validation.SUCCESS
import org.simple.clinic.util.exhaustive
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

typealias Ui = BloodPressureEntrySheet
typealias UiChange = (Ui) -> Unit

class BloodPressureEntrySheetController @Inject constructor(
    private val bloodPressureRepository: BloodPressureRepository,
    private val configProvider: Single<BloodPressureConfig>
) : ObservableTransformer<UiEvent, UiChange> {

  override fun apply(events: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = events.compose(ReportAnalyticsEvents()).replay().refCount()

    return Observable.mergeArray(
        automaticFocusChanges(replayedEvents),
        validationErrorResets(replayedEvents),
        prefillWhenUpdatingABloodPressure(replayedEvents),
        bpValidations(replayedEvents),
        saveNewBp(replayedEvents),
        updateBp(replayedEvents),
        toggleRemoveBloodPressureButton(replayedEvents))
  }

  private fun automaticFocusChanges(events: Observable<UiEvent>): Observable<UiChange> {
    val isSystolicValueComplete = { systolicText: String ->
      (systolicText.length == 3 && systolicText.matches("^[123].*$".toRegex()))
          || (systolicText.length == 2 && systolicText.matches("^[789].*$".toRegex()))
    }

    val moveFocusToDiastolic = events.ofType<BloodPressureSystolicTextChanged>()
        .filter { isSystolicValueComplete(it.systolic) }
        .distinctUntilChanged()
        .map { { ui: Ui -> ui.changeFocusToDiastolic() } }

    data class BloodPressure(val systolic: String, val diastolic: String)

    val systolicChanges = events.ofType<BloodPressureSystolicTextChanged>().map { it.systolic }
    val diastolicChanges = events.ofType<BloodPressureDiastolicTextChanged>().map { it.diastolic }

    val bpChanges = Observables
        .combineLatest(systolicChanges, diastolicChanges)
        .map { (systolic, diastolic) -> BloodPressure(systolic, diastolic) }

    val diastolicBackspaceClicksWithEmptyText = events.ofType<BloodPressureDiastolicBackspaceClicked>()
        .withLatestFrom(bpChanges) { _, bp -> bp }
        .filter { bp -> bp.diastolic.isEmpty() }

    val moveFocusBackToSystolic = diastolicBackspaceClicksWithEmptyText
        .map { { ui: Ui -> ui.changeFocusToSystolic() } }

    val deleteLastDigitOfSystolic = diastolicBackspaceClicksWithEmptyText
        .filter { bp -> bp.systolic.isNotBlank() }
        .map { bp -> bp.systolic.substring(0, bp.systolic.length - 1) }
        .map { truncatedSystolic -> { ui: Ui -> ui.setSystolic(truncatedSystolic) } }

    return moveFocusToDiastolic
        .mergeWith(moveFocusBackToSystolic)
        .mergeWith(deleteLastDigitOfSystolic)
  }

  private fun validationErrorResets(events: Observable<UiEvent>): Observable<UiChange> {
    val systolicChanges = events.ofType<BloodPressureSystolicTextChanged>()
        .distinctUntilChanged()
        .map { { ui: Ui -> ui.hideErrorMessage() } }

    val diastolicChanges = events.ofType<BloodPressureDiastolicTextChanged>()
        .distinctUntilChanged()
        .map { { ui: Ui -> ui.hideErrorMessage() } }

    return Observable.merge(systolicChanges, diastolicChanges)
  }

  private fun prefillWhenUpdatingABloodPressure(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<BloodPressureEntrySheetCreated>()
        .filter { it.openAs is OpenAs.Update }
        .map { it.openAs as OpenAs.Update }
        .flatMapSingle { bloodPressureRepository.measurement(it.bpUuid) }
        .map { bloodPressure ->
          { ui: Ui ->
            ui.setSystolic(bloodPressure.systolic.toString())
            ui.setDiastolic(bloodPressure.diastolic.toString())
          }
        }
  }

  private fun bpValidations(events: Observable<UiEvent>): Observable<UiChange> {
    val imeDoneClicks = events.ofType<BloodPressureSaveClicked>()

    val systolicChanges = events
        .ofType<BloodPressureSystolicTextChanged>()
        .map { it.systolic }

    val diastolicChanges = events
        .ofType<BloodPressureDiastolicTextChanged>()
        .map { it.diastolic }

    return imeDoneClicks
        .withLatestFrom(systolicChanges, diastolicChanges) { _, systolic, diastolic -> systolic to diastolic }
        .map { (systolic, diastolic) ->
          { ui: Ui ->
            when (validateInput(systolic, diastolic)) {
              ERROR_SYSTOLIC_LESS_THAN_DIASTOLIC -> ui.showSystolicLessThanDiastolicError()
              ERROR_SYSTOLIC_TOO_HIGH -> ui.showSystolicHighError()
              ERROR_SYSTOLIC_TOO_LOW -> ui.showSystolicLowError()
              ERROR_DIASTOLIC_TOO_HIGH -> ui.showDiastolicHighError()
              ERROR_DIASTOLIC_TOO_LOW -> ui.showDiastolicLowError()
              ERROR_SYSTOLIC_EMPTY -> ui.showSystolicEmptyError()
              ERROR_DIASTOLIC_EMPTY -> ui.showDiastolicEmptyError()
              SUCCESS -> {
                // Nothing to do here, SUCCESS handled below separately!
              }
            }.exhaustive()
          }
        }
  }

  private fun saveNewBp(events: Observable<UiEvent>): Observable<UiChange> {
    val imeDoneClicks = events.ofType<BloodPressureSaveClicked>()

    val systolicChanges = events
        .ofType<BloodPressureSystolicTextChanged>()
        .map { it.systolic }

    val diastolicChanges = events
        .ofType<BloodPressureDiastolicTextChanged>()
        .map { it.diastolic }

    val validBpEntry = imeDoneClicks
        .withLatestFrom(systolicChanges, diastolicChanges) { _, systolic, diastolic -> systolic to diastolic }
        .map { (systolic, diastolic) -> Triple(systolic, diastolic, validateInput(systolic, diastolic)) }
        .filter { (_, _, validation) -> validation == SUCCESS }
        .map { (systolic, diastolic, _) -> systolic to diastolic }

    val patientUuid = events
        .ofType<BloodPressureEntrySheetCreated>()
        .filter { it.openAs is OpenAs.New }
        .map { (it.openAs as OpenAs.New).patientUuid }

    return imeDoneClicks
        .withLatestFrom(validBpEntry, patientUuid) { _, (systolic, diastolic), patientId -> Triple(patientId, systolic, diastolic) }
        .distinctUntilChanged()
        .flatMapSingle { (patientId, systolic, diastolic) ->
          bloodPressureRepository
              .saveMeasurement(
                  patientUuid = patientId,
                  systolic = systolic.toInt(),
                  diastolic = diastolic.toInt()
              )
        }
        .map { { ui: Ui -> ui.setBPSavedResultAndFinish() } }
  }

  private fun updateBp(events: Observable<UiEvent>): Observable<UiChange> {
    val imeDoneClicks = events.ofType<BloodPressureSaveClicked>()

    val systolicChanges = events
        .ofType<BloodPressureSystolicTextChanged>()
        .map { it.systolic }

    val diastolicChanges = events
        .ofType<BloodPressureDiastolicTextChanged>()
        .map { it.diastolic }

    val validBpEntry = imeDoneClicks
        .withLatestFrom(systolicChanges, diastolicChanges) { _, systolic, diastolic -> systolic to diastolic }
        .map { (systolic, diastolic) -> Triple(systolic, diastolic, validateInput(systolic, diastolic)) }
        .filter { (_, _, validation) -> validation == SUCCESS }
        .map { (systolic, diastolic, _) -> systolic to diastolic }

    val bloodPressure = events
        .ofType<BloodPressureEntrySheetCreated>()
        .filter { it.openAs is OpenAs.Update }
        .map { it.openAs as OpenAs.Update }
        .flatMapSingle { bloodPressureRepository.measurement(it.bpUuid) }
        .take(1)

    return imeDoneClicks
        .withLatestFrom(validBpEntry, bloodPressure) { _, (systolic, diastolic), savedBp -> Triple(savedBp, systolic, diastolic) }
        .distinctUntilChanged()
        .map { (savedBp, systolic, diastolic) -> savedBp.copy(systolic = systolic.toInt(), diastolic = diastolic.toInt()) }
        .flatMapSingle { bloodPressureMeasurement ->
          bloodPressureRepository.updateMeasurement(bloodPressureMeasurement)
              .toSingleDefault({ ui: Ui -> ui.setBPSavedResultAndFinish() })
        }
  }

  private fun validateInput(systolic: String, diastolic: String): Validation {
    if (systolic.isBlank()) {
      return ERROR_SYSTOLIC_EMPTY
    }
    if (diastolic.isBlank()) {
      return ERROR_DIASTOLIC_EMPTY
    }

    val systolicNumber = systolic.trim().toInt()
    val diastolicNumber = diastolic.trim().toInt()

    return when {
      systolicNumber < 70 -> ERROR_SYSTOLIC_TOO_LOW
      systolicNumber > 300 -> ERROR_SYSTOLIC_TOO_HIGH
      diastolicNumber < 40 -> ERROR_DIASTOLIC_TOO_LOW
      diastolicNumber > 180 -> ERROR_DIASTOLIC_TOO_HIGH
      systolicNumber < diastolicNumber -> ERROR_SYSTOLIC_LESS_THAN_DIASTOLIC
      else -> SUCCESS
    }
  }

  enum class Validation {
    SUCCESS,
    ERROR_SYSTOLIC_EMPTY,
    ERROR_DIASTOLIC_EMPTY,
    ERROR_SYSTOLIC_TOO_HIGH,
    ERROR_SYSTOLIC_TOO_LOW,
    ERROR_DIASTOLIC_TOO_HIGH,
    ERROR_DIASTOLIC_TOO_LOW,
    ERROR_SYSTOLIC_LESS_THAN_DIASTOLIC
  }

  private fun toggleRemoveBloodPressureButton(events: Observable<UiEvent>): Observable<UiChange> {
    val featureEnabledStream = configProvider
        .map { it.deleteBloodPressureFeatureEnabled }
        .toObservable()

    val openAsStream = events
        .ofType<BloodPressureEntrySheetCreated>()
        .map { it.openAs }

    val hideRemoveBpButton = Observables
        .combineLatest(featureEnabledStream, openAsStream)
        .filter { (featureEnabled, openAs) -> featureEnabled.not() || openAs is OpenAs.New }
        .map { { ui: Ui -> ui.hideRemoveBpButton() } }

    val showRemoveBpButton = Observables
        .combineLatest(featureEnabledStream, openAsStream)
        .filter { (featureEnabled, openAs) -> featureEnabled && openAs is OpenAs.Update }
        .map { { ui: Ui -> ui.showRemoveBpButton() } }

    return hideRemoveBpButton.mergeWith(showRemoveBpButton)
  }
}
