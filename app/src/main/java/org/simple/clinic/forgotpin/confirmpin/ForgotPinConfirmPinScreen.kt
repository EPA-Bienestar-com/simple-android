package org.simple.clinic.forgotpin.confirmpin

import android.content.Context
import android.support.annotation.StringRes
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.schedulers.Schedulers.io
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.activity.TheActivity
import org.simple.clinic.facility.change.FacilityChangeScreenKey
import org.simple.clinic.home.HomeScreen
import org.simple.clinic.router.screen.RouterDirection
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.hideKeyboard
import org.simple.clinic.widgets.showKeyboard
import javax.inject.Inject

class ForgotPinConfirmPinScreen(context: Context, attributeSet: AttributeSet?) : RelativeLayout(context, attributeSet) {

  companion object {
    @JvmField
    val KEY = ::ForgotPinConfirmPinScreenKey
  }

  @Inject
  lateinit var controller: ForgotPinConfirmPinScreenController

  @Inject
  lateinit var screenRouter: ScreenRouter

  private val backButton by bindView<ImageButton>(R.id.forgotpin_confirmpin_back)
  private val progressBar by bindView<ProgressBar>(R.id.forgotpin_confirmpin_progress)
  private val facilityNameTextView by bindView<TextView>(R.id.forgotpin_confirmpin_facility_name)
  private val userNameTextView by bindView<TextView>(R.id.forgotpin_confirmpin_user_fullname)
  private val pinEntryEditText by bindView<EditText>(R.id.forgotpin_confirmpin_pin)
  private val pinErrorTextView by bindView<TextView>(R.id.forgotpin_confirmpin_error)
  private val pinEntryContainer by bindView<ViewGroup>(R.id.forgotpin_confirmpin_pin_container)
  private val pinEntryHintTextView by bindView<TextView>(R.id.forgotpin_confirmpin_confirm_message)

  override fun onFinishInflate() {
    super.onFinishInflate()

    TheActivity.component.inject(this)

    Observable.mergeArray(screenCreates(), facilityClicks(), pinSubmits(), pinTextChanges())
        .observeOn(io())
        .compose(controller)
        .observeOn(mainThread())
        .takeUntil(RxView.detaches(this))
        .subscribe { it.invoke(this) }

    pinEntryEditText.showKeyboard()

    backButton.setOnClickListener { goBack() }
  }

  private fun screenCreates(): Observable<UiEvent> {
    val screenKey = screenRouter.key<ForgotPinConfirmPinScreenKey>(this)
    return Observable.just(ForgotPinConfirmPinScreenCreated(screenKey.enteredPin))
  }

  private fun facilityClicks() =
      RxView.clicks(facilityNameTextView)
          .map { ForgotPinConfirmPinScreenFacilityClicked }

  private fun pinSubmits() =
      RxTextView.editorActions(pinEntryEditText)
          .filter { it == EditorInfo.IME_ACTION_DONE }
          .map { ForgotPinConfirmPinSubmitClicked(pinEntryEditText.text.toString()) }

  private fun pinTextChanges() =
      RxTextView.textChanges(pinEntryEditText)
          .map { ForgotPinConfirmPinTextChanged(it.toString()) }

  fun showUserName(name: String) {
    userNameTextView.text = name
  }

  fun showFacility(name: String) {
    facilityNameTextView.text = name
  }

  fun openFacilityChangeScreen() {
    screenRouter.push(FacilityChangeScreenKey())
  }

  private fun goBack() {
    screenRouter.pop()
  }

  fun showPinMismatchedError() {
    showError(R.string.forgotpin_error_pin_mismatch)
  }

  fun showUnexpectedError() {
    showError(R.string.api_unexpected_error)
  }

  fun showNetworkError() {
    showError(R.string.api_network_error)
  }

  fun hideError() {
    pinErrorTextView.visibility = GONE
    pinEntryHintTextView.visibility = VISIBLE
  }

  fun showProgress() {
    progressBar.visibility = VISIBLE
    pinEntryContainer.visibility = INVISIBLE
    hideKeyboard()
  }

  private fun hideProgress() {
    progressBar.visibility = INVISIBLE
    pinEntryContainer.visibility = VISIBLE
  }

  fun goToHomeScreen() {
    screenRouter.clearHistoryAndPush(HomeScreen.KEY, RouterDirection.FORWARD)
  }

  private fun showError(@StringRes errorMessageResId: Int) {
    hideProgress()
    pinEntryHintTextView.visibility = GONE
    pinErrorTextView.setText(errorMessageResId)
    pinErrorTextView.visibility = VISIBLE
    pinEntryEditText.showKeyboard()
  }
}
