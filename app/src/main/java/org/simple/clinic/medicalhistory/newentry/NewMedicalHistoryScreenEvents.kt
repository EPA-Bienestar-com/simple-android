package org.simple.clinic.medicalhistory.newentry

import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion
import org.simple.clinic.widgets.UiEvent

data class NewMedicalHistoryAnswerToggled(val question: MedicalHistoryQuestion, val answer: Answer) : UiEvent {
  override val analyticsName = "New Medical History:Answer for $question set to $answer"
}
