package org.simple.clinic.registration.name

import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.R
import org.simple.clinic.router.screen.FullScreenKey

@Parcelize
class RegistrationNameScreenKey : FullScreenKey {

  @IgnoredOnParcel
  override val analyticsName = "Registration Name Entry"

  override fun layoutRes() = R.layout.screen_registration_name
}
