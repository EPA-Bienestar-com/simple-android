package org.simple.clinic.summary

import android.os.Parcelable
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.R
import org.simple.clinic.router.screen.FullScreenKey
import org.threeten.bp.Instant
import java.util.Objects
import java.util.UUID

@Parcelize
data class PatientSummaryScreenKey(
    val patientUuid: UUID,
    val intention: OpenIntention,
    // TODO(vs): 2019-10-18 Move this to the UI model when migrating to Mobius
    val screenCreatedTimestamp: Instant
) : FullScreenKey, Parcelable {

  @IgnoredOnParcel
  override val analyticsName = "Patient Summary"

  override fun layoutRes(): Int {
    return R.layout.screen_patient_summary
  }

  override fun equals(other: Any?): Boolean {
    return when {
      this === other -> true
      other == null || this.javaClass != other.javaClass -> false
      else -> {
        val that = other as PatientSummaryScreenKey

        patientUuid == that.patientUuid && intention == that.intention
      }
    }
  }

  override fun hashCode(): Int {
    return Objects.hash(patientUuid, intention)
  }
}
