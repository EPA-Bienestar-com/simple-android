package org.simple.clinic.editpatient

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.PatientAddress
import org.simple.clinic.patient.PatientPhoneNumber
import org.simple.clinic.patient.businessid.BusinessId
import org.threeten.bp.format.DateTimeFormatter

@Parcelize
data class EditPatientModel(
    val savedEntry: EditablePatientEntry,
    val ongoingEntry: EditablePatientEntry,

    // TODO(rj): 2019-09-27 Do we really need these properties to update
    // patient information? Revisit these properties after migrating the feature
    val savedPatient: Patient,
    val savedAddress: PatientAddress,
    val savedPhoneNumber: PatientPhoneNumber?
) : Parcelable {
  companion object {
    fun from(
        patient: Patient,
        address: PatientAddress,
        phoneNumber: PatientPhoneNumber?,
        dateOfBirthFormatter: DateTimeFormatter
    ): EditPatientModel {
      val savedEntry = EditablePatientEntry.from(patient, address, phoneNumber, dateOfBirthFormatter)
      val ongoingEntry = savedEntry.copy()
      return EditPatientModel(savedEntry, ongoingEntry, patient, address, phoneNumber)
    }
  }

  fun updateName(name: String): EditPatientModel =
      copy(ongoingEntry = ongoingEntry.updateName(name))

  fun updateGender(gender: Gender): EditPatientModel =
      copy(ongoingEntry = ongoingEntry.updateGender(gender))

  fun updatePhoneNumber(phoneNumber: String): EditPatientModel =
      copy(ongoingEntry = ongoingEntry.updatePhoneNumber(phoneNumber))

  fun updateColonyOrVillage(colonyOrVillage: String): EditPatientModel =
      copy(ongoingEntry = ongoingEntry.updateColonyOrVillage(colonyOrVillage))

  fun updateDistrict(district: String): EditPatientModel =
      copy(ongoingEntry = ongoingEntry.updateDistrict(district))

  fun updateState(state: String): EditPatientModel =
      copy(ongoingEntry = ongoingEntry.updateState(state))

  fun updateAge(age: String): EditPatientModel =
      copy(ongoingEntry = ongoingEntry.updateAge(age))

  fun updateDateOfBirth(dateOfBirth: String): EditPatientModel =
      copy(ongoingEntry = ongoingEntry.updateDateOfBirth(dateOfBirth))

  fun updateBangladeshNationalId(bangladeshNationalId: BusinessId): EditPatientModel =
      copy(ongoingEntry = ongoingEntry.updateBangladeshNationalId(bangladeshNationalId))

  fun updateBangladeshNationalIdIdentifier(bangladeshNationalIdIdentifier: String): EditPatientModel {
    return copy(ongoingEntry = ongoingEntry.run {
      copy(bangladeshNationalId = bangladeshNationalId?.run {
        copy(identifier = identifier.copy(value = bangladeshNationalIdIdentifier))
      }
      )
    })
  }

  fun updateZone(zone: String): EditPatientModel =
      copy(ongoingEntry = ongoingEntry.updateZone(zone))

  fun updateStreetAddress(streetAddress: String): EditPatientModel =
      copy(ongoingEntry = ongoingEntry.updateStreetAddress(streetAddress))
}
