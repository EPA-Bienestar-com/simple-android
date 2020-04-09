package org.simple.clinic.summary.prescribeddrugs

import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.facility.Facility
import java.util.UUID

interface DrugSummaryUi {
  fun populatePrescribedDrugs(prescribedDrugs: List<PrescribedDrug>)
  fun showUpdatePrescribedDrugsScreen(patientUuid: UUID, currentFacility: Facility)
}
