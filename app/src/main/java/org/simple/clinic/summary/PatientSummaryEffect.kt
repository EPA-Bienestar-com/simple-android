package org.simple.clinic.summary

import org.simple.clinic.patient.businessid.Identifier
import org.threeten.bp.Instant
import java.util.UUID

sealed class PatientSummaryEffect

data class LoadPatientSummaryProfile(val patientUuid: UUID) : PatientSummaryEffect()

object LoadCurrentFacility : PatientSummaryEffect()

data class HandleEditClick(val patientSummaryProfile: PatientSummaryProfile) : PatientSummaryEffect()

object HandleLinkIdCancelled: PatientSummaryEffect()

object GoBackToPreviousScreen: PatientSummaryEffect()

object GoToHomeScreen: PatientSummaryEffect()

data class CheckForInvalidPhone(val patientUuid: UUID): PatientSummaryEffect()

data class FetchHasShownMissingPhoneReminder_Old(val patientUuid: UUID): PatientSummaryEffect()

data class MarkReminderAsShown(val patientUuid: UUID): PatientSummaryEffect()

data class ShowAddPhonePopup(val patientUuid: UUID): PatientSummaryEffect()

data class ShowLinkIdWithPatientView(val patientUuid: UUID, val identifier: Identifier): PatientSummaryEffect()

object HideLinkIdWithPatientView : PatientSummaryEffect()

data class ReportViewedPatientToAnalytics(val patientUuid: UUID, val openIntention: OpenIntention): PatientSummaryEffect()

data class ShowScheduleAppointmentSheet(val patientUuid: UUID, val sheetOpenedFrom: AppointmentSheetOpenedFrom): PatientSummaryEffect()

data class LoadDataForBackClick(
    val patientUuid: UUID,
    val screenCreatedTimestamp: Instant
) : PatientSummaryEffect()

data class LoadDataForDoneClick(val patientUuid: UUID): PatientSummaryEffect()

data class TriggerSync(val sheetOpenedFrom: AppointmentSheetOpenedFrom): PatientSummaryEffect()

object ShowDiagnosisError : PatientSummaryEffect()
