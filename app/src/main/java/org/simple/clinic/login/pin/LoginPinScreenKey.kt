package org.simple.clinic.login.pin

import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.simple.clinic.navigation.v2.ScreenKey

@Parcelize
class LoginPinScreenKey : ScreenKey() {

  @IgnoredOnParcel
  override val analyticsName = "Login PIN Entry"

  override fun instantiateFragment() = LoginPinScreen()
}
