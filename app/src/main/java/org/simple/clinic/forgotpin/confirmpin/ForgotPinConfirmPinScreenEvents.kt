package org.simple.clinic.forgotpin.confirmpin

import org.simple.clinic.widgets.UiEvent

data class ForgotPinConfirmPinSubmitClicked(val pin: String) : UiEvent {
  override val analyticsName = "Forgot PIN:Confirm PIN:Submit Clicked"
}

data class ForgotPinConfirmPinTextChanged(val text: String) : UiEvent {
  override val analyticsName = "Forgot PIN:Confirm PIN:Text Changed"
}
