package org.simple.clinic.summary

import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion
import org.simple.clinic.widgets.UiEvent

class PatientSummaryBackClicked : UiEvent {
  override val analyticsName = "Patient Summary:Back Clicked"
}

class PatientSummaryDoneClicked : UiEvent {
  override val analyticsName = "Patient Summary:Done Clicked"
}

class PatientSummaryUpdateDrugsClicked : UiEvent {
  override val analyticsName = "Patient Summary:Update Drugs Clicked"
}

class ScheduleAppointmentSheetClosed : UiEvent {
  override val analyticsName = "Patient Summary:Schedule Appointment Sheet Closed"
}

data class SummaryMedicalHistoryAnswerToggled(val question: MedicalHistoryQuestion, val answer: Answer) : UiEvent {
  override val analyticsName = "Patient Summary:Answer for $question set to $answer"
}

data class PatientSummaryBpClicked(val bloodPressureMeasurement: BloodPressureMeasurement) : UiEvent

data class PatientSummaryItemChanged(val patientSummaryItems: PatientSummaryItems) : UiEvent

object PatientSummaryBloodPressureSaved : UiEvent

object PatientSummaryLinkIdCancelled : UiEvent

object PatientSummaryLinkIdCompleted : UiEvent
