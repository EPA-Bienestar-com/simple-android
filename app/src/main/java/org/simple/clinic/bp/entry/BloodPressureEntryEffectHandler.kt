package org.simple.clinic.bp.entry

import com.spotify.mobius.rx2.RxMobius
import io.reactivex.Completable
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.rxkotlin.cast
import io.reactivex.rxkotlin.zipWith
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.bp.entry.BpValidator.Validation
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicEmpty
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicTooLow
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicEmpty
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicLessThanDiastolic
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicTooLow
import org.simple.clinic.bp.entry.BpValidator.Validation.Success
import org.simple.clinic.bp.entry.PrefillDate.UpdateEntryPrefill
import org.simple.clinic.facility.Facility
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.user.User
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.UserInputDatePaddingCharacter
import org.simple.clinic.util.exhaustive
import org.simple.clinic.util.scheduler.SchedulersProvider
import org.simple.clinic.util.toLocalDateAtZone
import org.simple.clinic.util.toUtcInstant
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result.Invalid.DateIsInFuture
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result.Invalid.InvalidPattern
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result.Valid
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.UUID

class BloodPressureEntryEffectHandler(
    private val ui: BloodPressureEntryUi,
    private val userSession: UserSession,
    private val facilityRepository: FacilityRepository,
    private val patientRepository: PatientRepository,
    private val bloodPressureRepository: BloodPressureRepository,
    private val appointmentsRepository: AppointmentRepository,
    private val userClock: UserClock,
    private val paddingCharacter: UserInputDatePaddingCharacter,
    private val schedulersProvider: SchedulersProvider
) {
  private val reportAnalyticsEvents = ReportAnalyticsEvents()

  fun create(): ObservableTransformer<BloodPressureEntryEffect, BloodPressureEntryEvent> {
    return RxMobius
        .subtypeEffectHandler<BloodPressureEntryEffect, BloodPressureEntryEvent>()
        .addTransformer(PrefillDate::class.java, prefillDate(schedulersProvider.ui()))
        .addAction(HideBpErrorMessage::class.java, ui::hideBpErrorMessage, schedulersProvider.ui())
        .addAction(ChangeFocusToDiastolic::class.java, ui::changeFocusToDiastolic, schedulersProvider.ui())
        .addAction(ChangeFocusToSystolic::class.java, ui::changeFocusToSystolic, schedulersProvider.ui())
        .addConsumer(SetSystolic::class.java, { ui.setSystolic(it.systolic) }, schedulersProvider.ui())
        .addTransformer(FetchBloodPressureMeasurement::class.java, fetchBloodPressureMeasurement(schedulersProvider.io()))
        .addConsumer(SetDiastolic::class.java, { ui.setDiastolic(it.diastolic) }, schedulersProvider.ui())
        .addConsumer(ShowConfirmRemoveBloodPressureDialog::class.java, { ui.showConfirmRemoveBloodPressureDialog(it.bpUuid) }, schedulersProvider.ui())
        .addAction(Dismiss::class.java, ui::dismiss, schedulersProvider.ui())
        .addAction(HideDateErrorMessage::class.java, ui::hideDateErrorMessage, schedulersProvider.ui())
        .addConsumer(ShowBpValidationError::class.java, { showBpValidationError(it.result) }, schedulersProvider.ui())
        .addAction(ShowDateEntryScreen::class.java, ui::showDateEntryScreen, schedulersProvider.ui())
        .addConsumer(ShowBpEntryScreen::class.java, { showBpEntryScreen(it.date) }, schedulersProvider.ui())
        .addConsumer(ShowDateValidationError::class.java, { showDateValidationError(it.result) }, schedulersProvider.ui())
        .addTransformer(CreateNewBpEntry::class.java, createNewBpEntryTransformer(schedulersProvider.io()))
        .addAction(SetBpSavedResultAndFinish::class.java, ui::setBpSavedResultAndFinish, schedulersProvider.ui())
        .addTransformer(UpdateBpEntry::class.java, updateBpEntryTransformer())
        .build()
  }

  private fun prefillDate(scheduler: Scheduler): ObservableTransformer<PrefillDate, BloodPressureEntryEvent> {
    return ObservableTransformer { prefillDates ->
      prefillDates
          .map(::convertToLocalDate)
          .observeOn(scheduler)
          .doOnNext { setDateOnInputFields(it) }
          .doOnNext { ui.showDateOnDateButton(it) }
          .map { DatePrefilled(it) }
    }
  }

  private fun convertToLocalDate(prefillDate: PrefillDate): LocalDate {
    val instant = if (prefillDate is UpdateEntryPrefill) prefillDate.date else Instant.now(userClock)
    return instant.toLocalDateAtZone(userClock.zone)
  }

  private fun setDateOnInputFields(dateToSet: LocalDate) {
    ui.setDateOnInputFields(
        getPaddedString(dateToSet.dayOfMonth),
        getPaddedString(dateToSet.monthValue),
        getYear(dateToSet)
    )
  }

  private fun getPaddedString(value: Int): String =
      value.toString().padStart(length = 2, padChar = paddingCharacter.value)

  private fun getYear(date: LocalDate): String =
      date.year.toString().substring(startIndex = 2, endIndex = 4)

  private fun fetchBloodPressureMeasurement(
      scheduler: Scheduler
  ): ObservableTransformer<FetchBloodPressureMeasurement, BloodPressureEntryEvent> {
    return ObservableTransformer { bloodPressureMeasurements ->
      bloodPressureMeasurements
          .flatMapSingle { getExistingBloodPressureMeasurement(it.bpUuid).subscribeOn(scheduler) }
          .map { BloodPressureMeasurementFetched(it.systolic, it.diastolic, it.recordedAt) }
    }
  }

  private fun showBpValidationError(bpValidation: Validation) {
    when (bpValidation) {
      is ErrorSystolicLessThanDiastolic -> ui.showSystolicLessThanDiastolicError()
      is ErrorSystolicTooHigh -> ui.showSystolicHighError()
      is ErrorSystolicTooLow -> ui.showSystolicLowError()
      is ErrorDiastolicTooHigh -> ui.showDiastolicHighError()
      is ErrorDiastolicTooLow -> ui.showDiastolicLowError()
      is ErrorSystolicEmpty -> ui.showSystolicEmptyError()
      is ErrorDiastolicEmpty -> ui.showDiastolicEmptyError()
      is Success -> {
        /* Nothing to do here. */
      }
    }.exhaustive()
  }

  private fun showBpEntryScreen(entryDate: LocalDate) {
    with(ui) {
      showBpEntryScreen()
      showDateOnDateButton(entryDate)
    }
  }

  private fun showDateValidationError(result: Result) {
    when (result) {
      is InvalidPattern -> ui.showInvalidDateError()
      is DateIsInFuture -> ui.showDateIsInFutureError()
      is Valid -> throw IllegalStateException("Date validation error cannot be $result")
    }.exhaustive()
  }

  private fun createNewBpEntryTransformer(scheduler: Scheduler): ObservableTransformer<CreateNewBpEntry, BloodPressureEntryEvent> {
    return ObservableTransformer { createNewBpEntries ->
      createNewBpEntries
          .subscribeOn(scheduler)
          .flatMapSingle { createNewBpEntry ->
            userAndCurrentFacility()
                .flatMap { (user, facility) -> storeNewBloodPressureMeasurement(user, facility, createNewBpEntry) }
                .flatMap { updateAppointmentsAsVisited(createNewBpEntry, it) }
          }
          .compose(reportAnalyticsEvents)
          .cast()
    }
  }

  private fun updateAppointmentsAsVisited(
      createNewBpEntry: CreateNewBpEntry,
      bloodPressureMeasurement: BloodPressureMeasurement
  ): Single<BloodPressureSaved> {
    val entryDate = createNewBpEntry.parsedDateFromForm.toUtcInstant(userClock)
    val compareAndUpdateRecordedAt = patientRepository
        .compareAndUpdateRecordedAt(bloodPressureMeasurement.patientUuid, entryDate)

    return appointmentsRepository
        .markAppointmentsCreatedBeforeTodayAsVisited(bloodPressureMeasurement.patientUuid)
        .andThen(compareAndUpdateRecordedAt)
        .toSingleDefault(BloodPressureSaved(createNewBpEntry.wasDateChanged))
  }

  private fun updateBpEntryTransformer(): ObservableTransformer<UpdateBpEntry, BloodPressureEntryEvent> {
    return ObservableTransformer { updateBpEntries ->
      updateBpEntries
          .flatMapSingle { updateBpEntry ->
            getUpdatedBloodPressureMeasurement(updateBpEntry)
                .map { bloodPressureMeasurement -> bloodPressureMeasurement to updateBpEntry.wasDateChanged }
          }
          .flatMapSingle { (bloodPressureMeasurement, wasDateChanged) ->
            storeUpdateBloodPressureMeasurement(bloodPressureMeasurement)
                .toSingleDefault(BloodPressureSaved(wasDateChanged))
          }
          .compose(reportAnalyticsEvents)
          .cast()
    }
  }

  private fun getUpdatedBloodPressureMeasurement(
      updateBpEntry: UpdateBpEntry
  ): Single<BloodPressureMeasurement> {
    return getExistingBloodPressureMeasurement(updateBpEntry.bpUuid)
        .zipWith(userAndCurrentFacility())
        .map { (existingBloodPressureMeasurement, userFacilityPair) ->
          val (user, facility) = userFacilityPair
          updateBloodPressureMeasurementValues(existingBloodPressureMeasurement, user.uuid, facility.uuid, updateBpEntry)
        }
  }

  private fun storeUpdateBloodPressureMeasurement(
      bloodPressureMeasurement: BloodPressureMeasurement
  ): Completable {
    val compareAndUpdateRecordedAt = patientRepository
        .compareAndUpdateRecordedAt(bloodPressureMeasurement.patientUuid, bloodPressureMeasurement.recordedAt)

    return bloodPressureRepository
        .updateMeasurement(bloodPressureMeasurement)
        .andThen(compareAndUpdateRecordedAt)
  }

  private fun updateBloodPressureMeasurementValues(
      existingMeasurement: BloodPressureMeasurement,
      userUuid: UUID,
      facilityUuid: UUID,
      updateBpEntry: UpdateBpEntry
  ): BloodPressureMeasurement {
    val (_, systolic, diastolic, parsedDateFromForm, _) = updateBpEntry

    return existingMeasurement.copy(
        userUuid = userUuid,
        facilityUuid = facilityUuid,
        systolic = systolic,
        diastolic = diastolic,
        recordedAt = parsedDateFromForm.toUtcInstant(userClock)
    )
  }

  private fun userAndCurrentFacility(): Single<Pair<User, Facility>> {
    return userSession
        .requireLoggedInUser()
        .flatMap { user ->
          facilityRepository
              .currentFacility(user)
              .map { facility -> user to facility }
        }
        .firstOrError()
  }

  private fun storeNewBloodPressureMeasurement(
      user: User,
      currentFacility: Facility,
      entry: CreateNewBpEntry
  ): Single<BloodPressureMeasurement> {
    val (patientUuid, systolic, diastolic, date) = entry
    return bloodPressureRepository.saveMeasurement(patientUuid, systolic, diastolic, user, currentFacility, date.toUtcInstant(userClock))
  }

  private fun getExistingBloodPressureMeasurement(bpUuid: UUID): Single<BloodPressureMeasurement> =
      bloodPressureRepository.measurement(bpUuid).firstOrError()
}
