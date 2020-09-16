package org.simple.clinic.teleconsultlog.prescription.patientinfo

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.patient.PatientProfile
import java.time.LocalDate
import java.util.UUID

@Parcelize
data class TeleconsultPatientInfoModel(
    val patientUuid: UUID,
    val prescriptionDate: LocalDate,
    val patientProfile: PatientProfile?
) : Parcelable {

  companion object {

    fun create(patientUuid: UUID, prescriptionDate: LocalDate) = TeleconsultPatientInfoModel(
        patientUuid = patientUuid,
        prescriptionDate = prescriptionDate,
        patientProfile = null
    )
  }

  val hasPatientProfile: Boolean
    get() = patientProfile != null

  fun patientProfileLoaded(patientProfile: PatientProfile): TeleconsultPatientInfoModel {
    return copy(patientProfile = patientProfile)
  }
}
