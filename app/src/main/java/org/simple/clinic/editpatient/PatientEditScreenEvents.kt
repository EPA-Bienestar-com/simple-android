package org.simple.clinic.editpatient

import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.OngoingEditPatientEntry
import org.simple.clinic.widgets.UiEvent
import java.util.UUID

data class PatientEditScreenCreated(val patientUuid: UUID) : UiEvent

data class PatientEditPatientNameTextChanged(val name: String) : UiEvent {
  override val analyticsName = "Edit Patient Entry:Patient Name Text Changed"
}

data class PatientEditPhoneNumberTextChanged(val phoneNumber: String) : UiEvent {
  override val analyticsName = "Edit Patient Entry:Phone Number Text Changed"
}

data class PatientEditGenderChanged(val gender: Gender) : UiEvent {
  override val analyticsName = "Edit Patient Entry:Changed Gender"
}

data class PatientEditColonyOrVillageChanged(val colonyOrVillage: String): UiEvent {
  override val analyticsName = "Edit Patient Entry:Colony Or Village Text Changed"
}

data class PatientEditDistrictTextChanged(val district: String): UiEvent {
  override val analyticsName = "Edit Patient Entry:District Text Changed"
}

data class PatientEditStateTextChanged(val state: String): UiEvent {
  override val analyticsName = "Edit Patient Entry:State Text Changed"
}

data class OngoingEditPatientEntryChanged(val ongoingEditPatientEntry: OngoingEditPatientEntry): UiEvent

class PatientEditSaveClicked: UiEvent {
  override val analyticsName = "Edit Patient Entry:Save Clicked"
}

data class PatientEditDateOfBirthFocusChanged(val hasFocus: Boolean): UiEvent {
  override val analyticsName = "Edit Patient Entry:Focused on DOB Text Field"
}

data class PatientEditDateOfBirthTextChanged(val dateOfBirth: String): UiEvent {
  override val analyticsName = "Edit Patient Entry:DOB Text Changed"
}

data class PatientEditAgeTextChanged(val age: String): UiEvent {
  override val analyticsName = "Edit Patient Entry:Age Text Changed"
}
