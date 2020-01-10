package org.simple.clinic.summary.prescribeddrugs

import org.simple.clinic.widgets.UiEvent

sealed class DrugSummaryEvent : UiEvent

class PatientSummaryUpdateDrugsClicked : DrugSummaryEvent() {
  override val analyticsName = "Patient Summary:Update Drugs Clicked"
}
