package org.simple.clinic.registration.name

import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.simple.clinic.navigation.v2.ScreenKey
import org.simple.clinic.user.OngoingRegistrationEntry

@Parcelize
data class RegistrationNameScreenKey(
    val registrationEntry: OngoingRegistrationEntry
) : ScreenKey() {

  @IgnoredOnParcel
  override val analyticsName = "Registration Name Entry"

  override fun instantiateFragment() = RegistrationFullNameScreen()
}
