package org.simple.clinic.drugs.selection

import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.widgets.UiEvent
import java.util.UUID

data class PrescribedDrugsScreenCreated(val patientUuid: UUID) : UiEvent

data class ProtocolDrugClicked(val drugName: String, val prescriptionForProtocolDrug: PrescribedDrug?) : UiEvent {
  override val analyticsName = "Drugs:Protocol:Selected"
}

data class CustomPrescriptionClicked(val prescribedDrug: PrescribedDrug) : UiEvent {
  override val analyticsName = "Drugs:Protocol:Edit CustomPrescription Clicked"
}

object AddNewPrescriptionClicked : UiEvent {
  override val analyticsName = "Drugs:Protocol:Add Custom Clicked"
}

object PrescribedDrugsDoneClicked : UiEvent {
  override val analyticsName = "Drugs:Protocol:Save Clicked"
}
