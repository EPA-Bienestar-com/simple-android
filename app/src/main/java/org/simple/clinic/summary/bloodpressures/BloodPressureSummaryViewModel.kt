package org.simple.clinic.summary.bloodpressures

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.facility.Facility
import java.util.UUID

@Parcelize
data class BloodPressureSummaryViewModel(
    val patientUuid: UUID,
    val latestBloodPressuresToDisplay: List<BloodPressureMeasurement>?,
    val totalRecordedBloodPressureCount: Int?,
    val facility: Facility?
) : Parcelable {
  companion object {
    fun create(patientUuid: UUID) = BloodPressureSummaryViewModel(patientUuid, null, null, null)
  }

  val hasLoadedCountOfBloodSugars: Boolean
    get() = totalRecordedBloodPressureCount != null

  val hasLoadedFacility: Boolean
    get() = facility != null

  val isDiabetesManagementEnabled: Boolean
    get() = facility!!.config.diabetesManagementEnabled

  fun bloodPressuresLoaded(bloodPressures: List<BloodPressureMeasurement>): BloodPressureSummaryViewModel =
      copy(latestBloodPressuresToDisplay = bloodPressures)

  fun bloodPressuresCountLoaded(bloodPressuresCount: Int): BloodPressureSummaryViewModel =
      copy(totalRecordedBloodPressureCount = bloodPressuresCount)

  fun currentFacilityLoaded(facility: Facility): BloodPressureSummaryViewModel =
      copy(facility = facility)
}
