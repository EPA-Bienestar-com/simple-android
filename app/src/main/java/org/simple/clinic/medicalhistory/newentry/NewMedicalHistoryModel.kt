package org.simple.clinic.medicalhistory.newentry

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.facility.Facility
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion
import org.simple.clinic.medicalhistory.OngoingMedicalHistoryEntry
import org.simple.clinic.patient.OngoingNewPatientEntry

@Parcelize
data class NewMedicalHistoryModel(
    val ongoingPatientEntry: OngoingNewPatientEntry?,
    val ongoingMedicalHistoryEntry: OngoingMedicalHistoryEntry,
    val currentFacility: Facility?,
    val showDiagnosisRequiredError: Boolean
) : Parcelable {

  val hasLoadedPatientEntry: Boolean
    get() = ongoingPatientEntry != null

  val hasLoadedCurrentFacility: Boolean
    get() = currentFacility != null

  val hasNotInitialized: Boolean
    get() = ongoingPatientEntry == null && currentFacility == null

  val facilityDiabetesManagementEnabled: Boolean
    get() = currentFacility!!.config.diabetesManagementEnabled

  companion object {
    fun default(): NewMedicalHistoryModel = NewMedicalHistoryModel(
        ongoingPatientEntry = null,
        ongoingMedicalHistoryEntry = OngoingMedicalHistoryEntry(),
        currentFacility = null,
        showDiagnosisRequiredError = false
    )
  }

  fun answerChanged(question: MedicalHistoryQuestion, answer: Answer): NewMedicalHistoryModel {
    return copy(ongoingMedicalHistoryEntry = ongoingMedicalHistoryEntry.answerChanged(question, answer))
  }

  fun ongoingPatientEntryLoaded(entry: OngoingNewPatientEntry): NewMedicalHistoryModel {
    return copy(ongoingPatientEntry = entry)
  }

  fun currentFacilityLoaded(facility: Facility): NewMedicalHistoryModel {
    return copy(currentFacility = facility)
  }
}
