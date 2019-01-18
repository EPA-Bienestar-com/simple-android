package org.simple.clinic.login.pin

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import android.util.AttributeSet
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.activity.TheActivity
import org.simple.clinic.home.HomeScreen
import org.simple.clinic.router.screen.BackPressInterceptCallback
import org.simple.clinic.router.screen.BackPressInterceptor
import org.simple.clinic.router.screen.RouterDirection
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.security.pin.PinEntryCardView
import org.simple.clinic.security.pin.PinEntryCardView.State
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

class LoginPinScreen(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs) {

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var controller: LoginPinScreenController

  private val phoneNumberTextView by bindView<TextView>(R.id.loginpin_phone_number)
  private val backButton by bindView<ImageButton>(R.id.loginpin_back)
  private val pinEntryCardView by bindView<PinEntryCardView>(R.id.loginpin_pin_entry_card)

  @SuppressLint("CheckResult")
  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }

    TheActivity.component.inject(this)

    pinEntryCardView.setForgotButtonVisible(false)

    Observable.mergeArray(screenCreates(), pinAuthentications(), backClicks(), otpReceived())
        .observeOn(Schedulers.io())
        .compose(controller)
        .observeOn(AndroidSchedulers.mainThread())
        .takeUntil(RxView.detaches(this))
        .subscribe { uiChange -> uiChange(this) }
  }

  private fun screenCreates(): Observable<UiEvent> {
    return Observable.just(PinScreenCreated())
  }

  private fun pinAuthentications() =
      pinEntryCardView
          .successfulAuthentications
          .map { LoginPinAuthenticated(it.pin) }

  private fun backClicks(): Observable<PinBackClicked> {
    val backClicksFromView = RxView.clicks(backButton).map { PinBackClicked() }

    val backClicksFromSystem = Observable.create<PinBackClicked> { emitter ->
      val backPressInterceptor = object : BackPressInterceptor {
        override fun onInterceptBackPress(callback: BackPressInterceptCallback) {
          emitter.onNext(PinBackClicked())
        }
      }

      screenRouter.registerBackPressInterceptor(backPressInterceptor)

      emitter.setCancellable { screenRouter.unregisterBackPressInterceptor(backPressInterceptor) }
    }

    return backClicksFromView.mergeWith(backClicksFromSystem)
  }

  private fun otpReceived(): Observable<LoginPinOtpReceived>? {
    val key = screenRouter.key<LoginPinScreenKey>(this)
    return Observable.just(LoginPinOtpReceived(key.otp))
  }

  fun showPhoneNumber(phoneNumber: String) {
    phoneNumberTextView.text = phoneNumber
  }

  private fun showError(@StringRes errorRes: Int) {
    pinEntryCardView.moveToState(State.PinEntry)
    pinEntryCardView.showError(context.getString(errorRes))
  }

  fun showNetworkError() {
    showError(R.string.loginpin_error_check_internet_connection)
  }

  fun showUnexpectedError() {
    showError(R.string.api_unexpected_error)
  }

  fun openHomeScreen() {
    screenRouter.clearHistoryAndPush(HomeScreen.KEY, RouterDirection.REPLACE)
  }

  fun goBackToRegistrationScreen() {
    screenRouter.pop()
  }
}
