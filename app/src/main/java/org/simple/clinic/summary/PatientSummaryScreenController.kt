package org.simple.clinic.summary

import com.f2prateek.rx.preferences2.Preference
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.Single
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.withLatestFrom
import org.simple.clinic.ReplayUntilScreenIsDestroyed
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.analytics.Analytics
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.drugs.PrescriptionRepository
import org.simple.clinic.medicalhistory.MedicalHistory
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.DIAGNOSED_WITH_HYPERTENSION
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_DIABETES
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_HEART_ATTACK
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_KIDNEY_DISEASE
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_STROKE
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.IS_ON_TREATMENT_FOR_HYPERTENSION
import org.simple.clinic.medicalhistory.MedicalHistoryRepository
import org.simple.clinic.overdue.Appointment.Status.CANCELLED
import org.simple.clinic.overdue.AppointmentCancelReason.InvalidPhoneNumber
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.PatientSummaryResult
import org.simple.clinic.patient.PatientSummaryResult.Saved
import org.simple.clinic.patient.PatientSummaryResult.Scheduled
import org.simple.clinic.summary.PatientSummaryCaller.NEW_PATIENT
import org.simple.clinic.summary.PatientSummaryCaller.SEARCH
import org.simple.clinic.util.Just
import org.simple.clinic.util.exhaustive
import org.simple.clinic.util.toOptional
import org.simple.clinic.util.filterAndUnwrapJust
import org.simple.clinic.util.unwrapJust
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Clock
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named

typealias Ui = PatientSummaryScreen
typealias UiChange = (Ui) -> Unit

class PatientSummaryScreenController @Inject constructor(
    private val patientRepository: PatientRepository,
    private val bpRepository: BloodPressureRepository,
    private val prescriptionRepository: PrescriptionRepository,
    private val medicalHistoryRepository: MedicalHistoryRepository,
    private val appointmentRepository: AppointmentRepository,
    private val timestampGenerator: RelativeTimestampGenerator,
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val configProvider: Single<PatientSummaryConfig>,
    @Named("patient_summary_result") private val patientSummaryResult: Preference<PatientSummaryResult>,
    @Named("time_for_bps_recorded") private val timeFormatterForBp: DateTimeFormatter
) : ObservableTransformer<UiEvent, UiChange> {

  override fun apply(events: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = ReplayUntilScreenIsDestroyed(events)
        .compose(ReportAnalyticsEvents())
        .compose(mergeWithPatientSummaryChanges())
        .replay()

    return Observable.mergeArray(
        populateList(replayedEvents),
        reportViewedPatientEvent(replayedEvents),
        populatePatientProfile(replayedEvents),
        updateMedicalHistory(replayedEvents),
        openBloodPressureBottomSheet(replayedEvents),
        openPrescribedDrugsScreen(replayedEvents),
        handleBackAndDoneClicks(replayedEvents),
        exitScreenAfterSchedulingAppointment(replayedEvents),
        openBloodPressureUpdateSheet(replayedEvents),
        patientSummaryResultChanged(replayedEvents),
        showUpdatePhoneDialog(replayedEvents))
  }

  private fun reportViewedPatientEvent(events: Observable<UiEvent>): Observable<UiChange> {
    return events.ofType<PatientSummaryScreenCreated>()
        .take(1L)
        .doOnNext { (patientUuid, caller) -> Analytics.reportViewedPatient(patientUuid, caller.name) }
        .flatMap { Observable.empty<UiChange>() }
  }

  private fun populatePatientProfile(events: Observable<UiEvent>): Observable<UiChange> {
    val patientUuid = events
        .ofType<PatientSummaryScreenCreated>()
        .map { it.patientUuid }

    val sharedPatients = patientUuid
        .flatMap { patientRepository.patient(it) }
        .map {
          // We do not expect the patient to get deleted while this screen is already open.
          (it as Just).value
        }
        .replay(1)
        .refCount()

    val addresses = sharedPatients
        .flatMap { patient -> patientRepository.address(patient.addressUuid) }
        .map { (it as Just).value }

    val phoneNumbers = patientUuid
        .flatMap { patientRepository.phoneNumber(it) }

    return Observables.combineLatest(sharedPatients, addresses, phoneNumbers)
        .map { (patient, address, phoneNumber) -> { ui: Ui -> ui.populatePatientProfile(patient, address, phoneNumber) } }
  }

  private fun mergeWithPatientSummaryChanges(): ObservableTransformer<UiEvent, UiEvent> {
    return ObservableTransformer { events ->
      val patientUuids = events
          .ofType<PatientSummaryScreenCreated>()
          .map { it.patientUuid }
          .distinctUntilChanged()

      val prescriptionItems = patientUuids
          .flatMap { prescriptionRepository.newestPrescriptionsForPatient(it) }
          .map(::SummaryPrescribedDrugsItem)

      val bloodPressures = patientUuids
          .withLatestFrom(configProvider.toObservable())
          .flatMap { (patientUuid, configProvider) -> bpRepository.newestMeasurementsForPatient(patientUuid, configProvider.numberOfBpsToDisplay) }
          .replay(1)
          .refCount()

      val displayTime = { instant: Instant ->
        instant.atZone(zoneId).format(timeFormatterForBp).toString().toOptional()
      }


      val bloodPressureItems = bloodPressures
          .map { bps ->
            val measurementsByDate = bps.groupBy { item -> item.createdAt.atZone(clock.zone).toLocalDate() }
            measurementsByDate.mapValues { (_, measurementList) ->
              val lastElement = measurementList.last()
              measurementList.map { measurement ->
                val timestamp = timestampGenerator.generate(measurement.createdAt)
                SummaryBloodPressureListItem(
                    measurement = measurement,
                    timestamp = timestamp,
                    showDivider = measurement.uuid == lastElement.uuid,
                    displayTime = displayTime(measurement.createdAt)
                )
              }
            }
          }
          .map { it.values.flatten() }

      val medicalHistoryItems = patientUuids
          .flatMap { medicalHistoryRepository.historyForPatientOrDefault(it) }
          .map { history ->
            val lastSyncTimestamp = timestampGenerator.generate(history.updatedAt)
            SummaryMedicalHistoryItem(history, lastSyncTimestamp)
          }

      // combineLatest() is important here so that the first data-set for the list
      // is dispatched in one go instead of them appearing one after another on the UI.
      val summaryItemChanges = Observables
          .combineLatest(
              prescriptionItems,
              bloodPressures,
              bloodPressureItems,
              medicalHistoryItems) { prescriptions, _, bpSummary, history ->
            PatientSummaryItemChanged(PatientSummaryItems(
                prescriptionItems = prescriptions,
                bloodPressureListItems = bpSummary,
                medicalHistoryItems = history
            ))
          }
          .distinctUntilChanged()

      events.mergeWith(summaryItemChanges)
    }
  }

  private fun populateList(events: Observable<UiEvent>): Observable<UiChange> {
    val bloodPressurePlaceholders = events.ofType<PatientSummaryItemChanged>()
        .map { it ->
          val bpList = it.patientSummaryItems.bloodPressureListItems
          bpList.groupBy { item -> item.measurement.createdAt.atZone(clock.zone).toLocalDate() }
        }
        .map { it.size }
        .withLatestFrom(configProvider.toObservable())
        .map { (numberOfBloodPressures, config) ->
          val numberOfPlaceholders = 0.coerceAtLeast(config.numberOfBpPlaceholders - numberOfBloodPressures)

          (1..numberOfPlaceholders).map { placeholderNumber ->
            val shouldShowHint = numberOfBloodPressures == 0 && placeholderNumber == 1
            SummaryBloodPressurePlaceholderListItem(placeholderNumber, shouldShowHint)
          }
        }

    val patientSummaryListItem = events.ofType<PatientSummaryItemChanged>()
        .map { it.patientSummaryItems }

    return Observables.combineLatest(
        patientSummaryListItem,
        bloodPressurePlaceholders) { patientSummary, placeHolders ->
      { ui: Ui ->
        ui.populateList(patientSummary.prescriptionItems, placeHolders, patientSummary.bloodPressureListItems, patientSummary.medicalHistoryItems)
      }
    }
  }

  private fun updateMedicalHistory(events: Observable<UiEvent>): Observable<UiChange> {
    val patientUuids = events
        .ofType<PatientSummaryScreenCreated>()
        .map { it.patientUuid }

    val medicalHistories = patientUuids
        .flatMap { medicalHistoryRepository.historyForPatientOrDefault(it) }

    val updateHistory = { medicalHistory: MedicalHistory, question: MedicalHistoryQuestion, answer: MedicalHistory.Answer ->
      when (question) {
        DIAGNOSED_WITH_HYPERTENSION -> medicalHistory.copy(diagnosedWithHypertension = answer)
        IS_ON_TREATMENT_FOR_HYPERTENSION -> medicalHistory.copy(isOnTreatmentForHypertension = answer)
        HAS_HAD_A_HEART_ATTACK -> medicalHistory.copy(hasHadHeartAttack = answer)
        HAS_HAD_A_STROKE -> medicalHistory.copy(hasHadStroke = answer)
        HAS_HAD_A_KIDNEY_DISEASE -> medicalHistory.copy(hasHadKidneyDisease = answer)
        HAS_DIABETES -> medicalHistory.copy(hasDiabetes = answer)
      }
    }

    return events.ofType<SummaryMedicalHistoryAnswerToggled>()
        .withLatestFrom(medicalHistories)
        .map { (toggleEvent, medicalHistory) ->
          updateHistory(medicalHistory, toggleEvent.question, toggleEvent.answer)
        }
        .flatMap {
          medicalHistoryRepository
              .save(it)
              .andThen(Observable.never<UiChange>())
        }
  }

  private fun openBloodPressureBottomSheet(events: Observable<UiEvent>): Observable<UiChange> {
    val patientUuid = events
        .ofType<PatientSummaryScreenCreated>()
        .map { it.patientUuid }

    val autoShows = events
        .ofType<PatientSummaryScreenCreated>()
        .filter { it.caller == NEW_PATIENT }
        .withLatestFrom(patientUuid)
        .map { (_, patientUuid) -> { ui: Ui -> ui.showBloodPressureEntrySheetIfNotShownAlready(patientUuid) } }

    val newBpClicks = events
        .ofType<PatientSummaryNewBpClicked>()
        .withLatestFrom(patientUuid)
        .map { (_, patientUuid) -> { ui: Ui -> ui.showBloodPressureEntrySheet(patientUuid) } }

    return autoShows.mergeWith(newBpClicks)
  }

  private fun openPrescribedDrugsScreen(events: Observable<UiEvent>): Observable<UiChange> {
    val patientUuid = events
        .ofType<PatientSummaryScreenCreated>()
        .map { it.patientUuid }

    return events
        .ofType<PatientSummaryUpdateDrugsClicked>()
        .withLatestFrom(patientUuid)
        .map { (_, patientUuid) -> { ui: Ui -> ui.showUpdatePrescribedDrugsScreen(patientUuid) } }
  }

  private fun handleBackAndDoneClicks(events: Observable<UiEvent>): Observable<UiChange> {
    val callers = events
        .ofType<PatientSummaryScreenCreated>()
        .map { it.caller }

    val patientUuids = events
        .ofType<PatientSummaryScreenCreated>()
        .map { it.patientUuid }

    val bloodPressureSaves = events
        .ofType<PatientSummaryBloodPressureClosed>()
        .startWith(PatientSummaryBloodPressureClosed(false))
        .map { it.wasBloodPressureSaved }

    val bloodPressureSaveRestores = events
        .ofType<PatientSummaryRestoredWithBPSaved>()
        .map { it.wasBloodPressureSaved }

    val mergedBpSaves = Observable.merge(bloodPressureSaves, bloodPressureSaveRestores)

    val backClicks = events
        .ofType<PatientSummaryBackClicked>()

    val doneClicks = events
        .ofType<PatientSummaryDoneClicked>()

    val doneOrBackClicksWithBpSaved = Observable.merge(doneClicks, backClicks)
        .withLatestFrom(mergedBpSaves, patientUuids)
        .filter { (_, saved, _) -> saved }
        .map { (_, _, uuid) -> { ui: Ui -> ui.showScheduleAppointmentSheet(patientUuid = uuid) } }

    val backClicksWithBpNotSaved = backClicks
        .withLatestFrom(mergedBpSaves, callers)
        .filter { (_, saved, _) -> saved.not() }
        .map { (_, _, caller) ->
          { ui: Ui ->
            when (caller!!) {
              SEARCH -> ui.goBackToPatientSearch()
              NEW_PATIENT -> ui.goBackToHome()
            }.exhaustive()
          }
        }

    val doneClicksWithBpNotSaved = doneClicks
        .withLatestFrom(mergedBpSaves)
        .filter { (_, saved) -> saved.not() }
        .map { { ui: Ui -> ui.goBackToHome() } }

    return Observable.mergeArray(
        doneOrBackClicksWithBpSaved,
        backClicksWithBpNotSaved,
        doneClicksWithBpNotSaved)
  }

  private fun exitScreenAfterSchedulingAppointment(events: Observable<UiEvent>): Observable<UiChange> {
    val callers = events
        .ofType<PatientSummaryScreenCreated>()
        .map { it.caller }

    val scheduleAppointmentCloses = events
        .ofType<ScheduleAppointmentSheetClosed>()

    val backClicks = events
        .ofType<PatientSummaryBackClicked>()

    val doneClicks = events
        .ofType<PatientSummaryDoneClicked>()

    val afterBackClicks = scheduleAppointmentCloses
        .withLatestFrom(backClicks, callers)
        .map { (_, _, caller) ->
          { ui: Ui ->
            when (caller!!) {
              SEARCH -> ui.goBackToPatientSearch()
              NEW_PATIENT -> ui.goBackToHome()
            }.exhaustive()
          }
        }

    val afterDoneClicks = scheduleAppointmentCloses
        .withLatestFrom(doneClicks)
        .map { { ui: Ui -> ui.goBackToHome() } }

    return afterBackClicks.mergeWith(afterDoneClicks)
  }

  private fun openBloodPressureUpdateSheet(events: Observable<UiEvent>): Observable<UiChange> {
    return events.ofType<PatientSummaryBpClicked>()
        .map { it.bloodPressureMeasurement }
        .map { bp -> { ui: Ui -> ui.showBloodPressureUpdateSheet(bp.uuid) } }
  }

  private fun patientSummaryResultChanged(events: Observable<UiEvent>): Observable<UiChange> {
    val screenCreates = events
        .ofType<PatientSummaryScreenCreated>()

    val isPatientNew = screenCreates
        .filter { it.caller == NEW_PATIENT }
        .map { Saved(it.patientUuid) as PatientSummaryResult }

    val appointmentScheduled = events.ofType<AppointmentScheduled>()
        .withLatestFrom(screenCreates)
        .map { (_, createdEvent) -> Scheduled(createdEvent.patientUuid) as PatientSummaryResult }

    val wasPatientSummaryItemsChanged = events.ofType<PatientSummaryItemChanged>()
        .map { it.patientSummaryItems }
        .withLatestFrom(screenCreates)
        .filter { (item, createdEvent) -> item.hasItemChangedSince(createdEvent.screenCreatedTimestamp) }
        .map { (_, createdEvent) -> Saved(createdEvent.patientUuid) as PatientSummaryResult }

    return Observable.merge(isPatientNew, wasPatientSummaryItemsChanged, appointmentScheduled)
        .flatMap {
          patientSummaryResult.set(it)
          Observable.never<UiChange>()
        }
  }

  private fun showUpdatePhoneDialog(events: Observable<UiEvent>): Observable<UiChange> {
    val patientUuidStream = events
        .ofType<PatientSummaryScreenCreated>()
        .map { it.patientUuid }

    return Observables.combineLatest(patientUuidStream, configProvider.toObservable())
        .filter { (_, config) -> config.isUpdatePhoneDialogEnabled }
        .flatMap { (patientUuid) ->
          val lastCancelledAppointment = appointmentRepository
              .lastCreatedAppointmentForPatient(patientUuid)
              .filterAndUnwrapJust()
              .filter { it.status == CANCELLED && it.cancelReason == InvalidPhoneNumber }

          val patientPhoneNumber = patientRepository
              .phoneNumber(patientUuid)
              .unwrapJust()

          Observables.combineLatest(lastCancelledAppointment, patientPhoneNumber)
              .filter { (appointment, number) -> appointment.updatedAt > number.updatedAt }
              .map { patientUuid }
        }
        .take(1)
        .map { patientUuid -> { ui: Ui -> ui.showUpdatePhoneDialog(patientUuid) } }
  }
}
