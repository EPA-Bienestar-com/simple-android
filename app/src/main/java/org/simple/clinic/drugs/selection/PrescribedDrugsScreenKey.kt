package org.simple.clinic.drugs.selection

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.simple.clinic.navigation.v2.ScreenKey
import java.util.UUID

@Parcelize
data class PrescribedDrugsScreenKey(val patientUuid: UUID) : ScreenKey(), Parcelable {

  @IgnoredOnParcel
  override val analyticsName = "Patient Drugs"

  override fun instantiateFragment() = EditMedicinesScreen()
}
