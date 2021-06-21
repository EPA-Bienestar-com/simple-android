package org.simple.clinic.introvideoscreen

import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.simple.clinic.R
import org.simple.clinic.router.screen.FullScreenKey
import org.simple.clinic.user.OngoingRegistrationEntry

@Parcelize
data class IntroVideoScreenKey(val registrationEntry: OngoingRegistrationEntry) : FullScreenKey {

  @IgnoredOnParcel
  override val analyticsName = "Onboarding Intro Video"

  override fun layoutRes(): Int = R.layout.screen_intro_video
}
