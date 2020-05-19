package org.simple.clinic.summary

import com.spotify.mobius.Next
import com.spotify.mobius.Next.noChange
import com.spotify.mobius.Update
import org.simple.clinic.facility.Facility
import org.simple.clinic.mobius.dispatch
import org.simple.clinic.mobius.next
import org.simple.clinic.summary.AppointmentSheetOpenedFrom.BACK_CLICK
import org.simple.clinic.summary.AppointmentSheetOpenedFrom.DONE_CLICK
import org.simple.clinic.summary.OpenIntention.LinkIdWithPatient
import org.simple.clinic.summary.OpenIntention.ViewExistingPatient
import org.simple.clinic.summary.OpenIntention.ViewNewPatient
import org.simple.clinic.summary.teleconsultation.api.TeleconsultInfo
import java.util.UUID

class PatientSummaryUpdate : Update<PatientSummaryModel, PatientSummaryEvent, PatientSummaryEffect> {

  override fun update(model: PatientSummaryModel, event: PatientSummaryEvent): Next<PatientSummaryModel, PatientSummaryEffect> {
    return when (event) {
      is PatientSummaryProfileLoaded -> next(model.patientSummaryProfileLoaded(event.patientSummaryProfile))
      is PatientSummaryBackClicked -> dispatch(LoadDataForBackClick(model.patientUuid, event.screenCreatedTimestamp))
      is PatientSummaryDoneClicked -> dispatch(LoadDataForDoneClick(model.patientUuid))
      is CurrentFacilityLoaded -> currentFacilityLoaded(model, event)
      PatientSummaryEditClicked -> dispatch(HandleEditClick(model.patientSummaryProfile!!, model.currentFacility!!))
      is PatientSummaryLinkIdCancelled -> dispatch(HandleLinkIdCancelled)
      is ScheduledAppointment -> dispatch(TriggerSync(event.sheetOpenedFrom))
      is CompletedCheckForInvalidPhone -> next(model.completedCheckForInvalidPhone())
      is PatientSummaryBloodPressureSaved -> bloodPressureSaved(model.openIntention, model.patientSummaryProfile!!)
      is FetchedHasShownMissingPhoneReminder -> fetchedHasShownMissingReminder(event.hasShownReminder, model.patientUuid)
      is LinkIdWithPatientSheetShown -> next(model.shownLinkIdWithPatientView())
      is PatientSummaryLinkIdCompleted -> dispatch(HideLinkIdWithPatientView)
      is DataForBackClickLoaded -> dataForHandlingBackLoaded(
          patientUuid = model.patientUuid,
          hasPatientDataChanged = event.hasPatientDataChangedSinceScreenCreated,
          countOfRecordedMeasurements = event.countOfRecordedMeasurements,
          openIntention = model.openIntention,
          diagnosisRecorded = event.diagnosisRecorded,
          isDiabetesManagementEnabled = model.isDiabetesManagementEnabled,
          currentFacility = model.currentFacility!!
      )
      is DataForDoneClickLoaded -> dataForHandlingDoneClickLoaded(
          patientUuid = model.patientUuid,
          countOfRecordedMeasurements = event.countOfRecordedMeasurements,
          diagnosisRecorded = event.diagnosisRecorded,
          isDiabetesManagementEnabled = model.isDiabetesManagementEnabled,
          currentFacility = model.currentFacility!!
      )
      is SyncTriggered -> scheduleAppointmentSheetClosed(model, event.sheetOpenedFrom)
      is ContactPatientClicked -> dispatch(OpenContactPatientScreen(model.patientUuid))
      is PatientTeleconsultationInfoLoaded -> patientInformationLoaded(event, model)
      ContactDoctorClicked -> contactDoctorClicked(model)
      is FetchedTeleconsultationInfo -> fetchedTeleconsultationInfo(model, event)
      RetryFetchTeleconsultInfo -> retryFetchTeleconsultInfo(model)
      is UserLoggedInStatusLoaded -> next(model.userLoggedInStatusLoaded(event.loggedInStatus))
    }
  }

  private fun retryFetchTeleconsultInfo(model: PatientSummaryModel): Next<PatientSummaryModel, PatientSummaryEffect> {
    return next(
        model.fetchingTeleconsultationInfo(),
        FetchTeleconsultationInfo(model.currentFacility!!.uuid)
    )
  }

  private fun currentFacilityLoaded(model: PatientSummaryModel, event: CurrentFacilityLoaded): Next<PatientSummaryModel, PatientSummaryEffect> {
    val updatedModel = model.currentFacilityLoaded(event.facility)

    return if (updatedModel.isTeleconsultationEnabled && model.isUserLoggedIn) {
      next(
          updatedModel.fetchingTeleconsultationInfo(),
          FetchTeleconsultationInfo(event.facility.uuid)
      )
    } else {
      next(updatedModel)
    }
  }

  private fun fetchedTeleconsultationInfo(
      model: PatientSummaryModel,
      event: FetchedTeleconsultationInfo
  ): Next<PatientSummaryModel, PatientSummaryEffect> {
    if (event.teleconsultInfo is TeleconsultInfo.NetworkError) {
      return next(model.failedToFetchTeleconsultationInfo(), ShowTeleconsultInfoError)
    }

    return next(model.fetchedTeleconsultationInfo(event.teleconsultInfo))
  }

  private fun patientInformationLoaded(
      event: PatientTeleconsultationInfoLoaded,
      model: PatientSummaryModel
  ): Next<PatientSummaryModel, PatientSummaryEffect> {
    val teleconsultInfo = model.teleconsultInfo as TeleconsultInfo.Fetched
    return dispatch(ContactDoctor(event.patientTeleconsultationInfo, teleconsultInfo.doctorPhoneNumber))
  }

  private fun contactDoctorClicked(model: PatientSummaryModel): Next<PatientSummaryModel, PatientSummaryEffect> {
    return when (model.teleconsultInfo) {
      is TeleconsultInfo.Fetched -> dispatch(LoadPatientTeleconsultationInfo(
          model.patientUuid,
          model.patientSummaryProfile?.bpPassport,
          model.currentFacility
      ) as PatientSummaryEffect)
      else -> noChange()
    }
  }

  private fun dataForHandlingDoneClickLoaded(
      patientUuid: UUID,
      countOfRecordedMeasurements: Int,
      diagnosisRecorded: Boolean,
      isDiabetesManagementEnabled: Boolean,
      currentFacility: Facility
  ): Next<PatientSummaryModel, PatientSummaryEffect> {
    val hasAtLeastOneMeasurementRecorded = countOfRecordedMeasurements > 0
    val shouldShowDiagnosisError = hasAtLeastOneMeasurementRecorded && diagnosisRecorded.not() && isDiabetesManagementEnabled

    val effect = when {
      shouldShowDiagnosisError -> ShowDiagnosisError
      hasAtLeastOneMeasurementRecorded -> ShowScheduleAppointmentSheet(patientUuid, DONE_CLICK, currentFacility)
      else -> GoToHomeScreen
    }

    return dispatch(effect)
  }

  private fun dataForHandlingBackLoaded(
      patientUuid: UUID,
      hasPatientDataChanged: Boolean,
      countOfRecordedMeasurements: Int,
      openIntention: OpenIntention,
      diagnosisRecorded: Boolean,
      isDiabetesManagementEnabled: Boolean,
      currentFacility: Facility
  ): Next<PatientSummaryModel, PatientSummaryEffect> {
    val shouldShowScheduleAppointmentSheet = if (countOfRecordedMeasurements == 0) false else hasPatientDataChanged
    val shouldShowDiagnosisError = shouldShowScheduleAppointmentSheet && diagnosisRecorded.not() && isDiabetesManagementEnabled
    val shouldGoToPreviousScreen = openIntention is ViewExistingPatient
    val shouldGoToHomeScreen = openIntention is LinkIdWithPatient || openIntention is ViewNewPatient

    val effect = when {
      shouldShowDiagnosisError -> ShowDiagnosisError
      shouldShowScheduleAppointmentSheet -> ShowScheduleAppointmentSheet(patientUuid, BACK_CLICK, currentFacility)
      shouldGoToPreviousScreen -> GoBackToPreviousScreen
      shouldGoToHomeScreen -> GoToHomeScreen
      else -> throw IllegalStateException("This should not happen!")
    }

    return dispatch(effect)
  }

  private fun fetchedHasShownMissingReminder(
      hasShownReminder: Boolean,
      patientUuid: UUID
  ): Next<PatientSummaryModel, PatientSummaryEffect> {
    return if (!hasShownReminder) {
      dispatch(MarkReminderAsShown(patientUuid), ShowAddPhonePopup(patientUuid))
    } else {
      noChange()
    }
  }

  private fun bloodPressureSaved(
      openIntention: OpenIntention,
      patientSummaryProfile: PatientSummaryProfile
  ): Next<PatientSummaryModel, PatientSummaryEffect> {
    return when (openIntention) {
      ViewNewPatient -> noChange()
      else -> if (!patientSummaryProfile.hasPhoneNumber) {
        dispatch(FetchHasShownMissingPhoneReminder(patientSummaryProfile.patient.uuid) as PatientSummaryEffect)
      } else {
        noChange()
      }
    }
  }

  private fun scheduleAppointmentSheetClosed(
      model: PatientSummaryModel,
      appointmentScheduledFrom: AppointmentSheetOpenedFrom
  ): Next<PatientSummaryModel, PatientSummaryEffect> {
    val effect = when (appointmentScheduledFrom) {
      BACK_CLICK -> when (model.openIntention) {
        ViewExistingPatient -> GoBackToPreviousScreen
        ViewNewPatient, is LinkIdWithPatient -> GoToHomeScreen
      }
      DONE_CLICK -> GoToHomeScreen
    }

    return dispatch(effect)
  }
}
