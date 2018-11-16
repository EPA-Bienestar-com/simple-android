package org.simple.clinic.login.pin

import org.simple.clinic.widgets.UiEvent

class PinScreenCreated : UiEvent

class PinBackClicked : UiEvent {
  override val analyticsName = "Login:Pin Entry:Back Clicked"
}

data class LoginPinAuthenticated(val pin: String) : UiEvent {
  override val analyticsName = "Login:Pin authenticated"
}

// This event does not need an analytics name because it is
// part of the old, deprecated login flow and will be removed
data class LoginPinOtpReceived(val otp: String) : UiEvent
