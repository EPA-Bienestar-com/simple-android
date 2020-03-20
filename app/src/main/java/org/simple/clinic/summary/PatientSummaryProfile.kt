package org.simple.clinic.summary

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.PatientAddress
import org.simple.clinic.patient.PatientPhoneNumber
import org.simple.clinic.patient.businessid.BusinessId

@Parcelize
data class PatientSummaryProfile(
    val patient: Patient,
    val address: PatientAddress,
    val phoneNumber: PatientPhoneNumber?,
    val bpPassport: BusinessId?,
    val bangladeshNationalId: BusinessId?
): Parcelable {

  val hasPhoneNumber: Boolean
    get() = phoneNumber != null
}
