package org.simple.clinic.registration.pin

import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.R
import org.simple.clinic.router.screen.FullScreenKey

@Parcelize
class RegistrationPinScreenKey : FullScreenKey {

  @IgnoredOnParcel
  override val analyticsName = "Registration PIN Entry"

  override fun layoutRes() = R.layout.screen_registration_pin
}
