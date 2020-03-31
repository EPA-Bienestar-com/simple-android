package org.simple.clinic.security.pin

import android.annotation.SuppressLint
import android.content.Context
import android.os.CountDownTimer
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.cardview.widget.CardView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.pin_entry_card.view.*
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.di.injector
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.security.di.Method
import org.simple.clinic.security.pin.verification.PinVerificationMethod
import org.simple.clinic.util.UtcClock
import org.simple.clinic.util.exhaustive
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.displayedChildResId
import org.simple.clinic.widgets.hideKeyboard
import org.simple.clinic.widgets.setPaddingBottom
import org.simple.clinic.widgets.showKeyboard
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import javax.inject.Inject

class PinEntryCardView(context: Context, attrs: AttributeSet) : CardView(context, attrs), PinEntryUi, UiActions {

  companion object {
    private const val MILLIS_IN_SECOND = 1000L
    private const val SECONDS_IN_HOUR = 3600L
    private const val SECONDS_IN_MINUTE = 60L
  }

  @Inject
  lateinit var effectHandlerFactory: PinEntryEffectHandler.Factory

  @Inject
  lateinit var clock: UtcClock

  @Inject
  lateinit var verificationMethods: Map<Method, @JvmSuppressWildcards PinVerificationMethod>

  val downstreamUiEvents: PublishSubject<UiEvent> = PublishSubject.create<UiEvent>()

  private val method: Method

  init {
    LayoutInflater.from(context).inflate(R.layout.pin_entry_card, this, true)
    setPinEntryMode(PinEntryUi.Mode.PinEntry)
    setForgotButtonVisible(true)

    with(context.obtainStyledAttributes(attrs, R.styleable.PinEntryCardView)) {
      val methodIndex = getInt(R.styleable.PinEntryCardView_verificationMethod, -1)

      if(methodIndex < 0) {
        throw RuntimeException("No verification method defined!")
      }
      method = Method.values()[methodIndex]

      recycle()
    }
  }

  private val events by unsafeLazy {
    pinTextChanges()
        .compose(ReportAnalyticsEvents())
  }

  private val uiRenderer = PinEntryUiRenderer(this)

  private val delegate: MobiusDelegate<PinEntryModel, PinEntryEvent, PinEntryEffect> by unsafeLazy {
    MobiusDelegate.forView(
        events = events.ofType(),
        defaultModel = PinEntryModel.default(),
        init = PinEntryInit(),
        update = PinEntryUpdate(submitPinAtLength = 4),
        effectHandler = effectHandlerFactory.create(
            uiActions = this,
            pinVerificationMethod = verificationMethods.getValue(method)
        ).build(),
        modelUpdateListener = uiRenderer::render
    )
  }

  private var pinEntryLockedCountdown: CountDownTimer? = null
    set(value) {
      field?.cancel()
      field = value
    }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    context.injector<Injector>().inject(this)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    delegate.start()
  }

  override fun onDetachedFromWindow() {
    pinEntryLockedCountdown?.cancel()
    delegate.stop()
    super.onDetachedFromWindow()
  }

  override fun onSaveInstanceState(): Parcelable? {
    return delegate.onSaveInstanceState(super.onSaveInstanceState())
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    super.onRestoreInstanceState(delegate.onRestoreInstanceState(state))
  }

  private fun pinTextChanges() =
      pinEditText.textChanges()
          .map(CharSequence::toString)
          .map(::PinTextChanged)

  override fun setPinEntryMode(mode: PinEntryUi.Mode) {
    val transition = AutoTransition()
        .setOrdering(AutoTransition.ORDERING_TOGETHER)
        .setDuration(200)
        .setInterpolator(FastOutSlowInInterpolator())
        .removeTarget(errorTextView)
    TransitionManager.beginDelayedTransition(this, transition)

    contentContainer.visibility = when (mode) {
      is PinEntryUi.Mode.PinEntry -> VISIBLE
      is PinEntryUi.Mode.BruteForceLocked -> VISIBLE
      is PinEntryUi.Mode.Progress -> INVISIBLE
    }

    progressView.visibility = when (mode) {
      is PinEntryUi.Mode.PinEntry -> GONE
      is PinEntryUi.Mode.BruteForceLocked -> GONE
      is PinEntryUi.Mode.Progress -> VISIBLE
    }

    pinAndLockViewFlipper.displayedChildResId = when (mode) {
      is PinEntryUi.Mode.PinEntry -> R.id.pinEditText
      is PinEntryUi.Mode.BruteForceLocked -> R.id.pinentry_bruteforcelock
      is PinEntryUi.Mode.Progress -> pinAndLockViewFlipper.displayedChildResId
    }

    when (mode) {
      is PinEntryUi.Mode.PinEntry -> pinEditText.showKeyboard()
      is PinEntryUi.Mode.Progress -> hideKeyboard()
      is PinEntryUi.Mode.BruteForceLocked -> hideKeyboard()
    }.exhaustive()

    if (mode is PinEntryUi.Mode.BruteForceLocked) {
      val timer = startTimerCountdown(mode.lockUntil)
      timer.start()
      pinEntryLockedCountdown = timer
    }
  }

  private fun startTimerCountdown(until: Instant): CountDownTimer {
    val timeRemaining = Duration.between(Instant.now(clock), until).toMillis()

    return object : CountDownTimer(timeRemaining, MILLIS_IN_SECOND) {

      override fun onFinish() {
        /* Nothing to do here */
      }

      override fun onTick(millisUntilFinished: Long) {
        val secondsRemaining = millisUntilFinished / MILLIS_IN_SECOND

        val minutes = (secondsRemaining % SECONDS_IN_HOUR / SECONDS_IN_MINUTE).toString()
        val seconds = (secondsRemaining % SECONDS_IN_MINUTE).toString()

        val minutesWithPadding = minutes.padStart(2, padChar = '0')
        val secondsWithPadding = seconds.padStart(2, padChar = '0')

        timeRemainingTillUnlockTextView.text = resources.getString(
            R.string.pinentry_bruteforcelock_timer,
            minutesWithPadding,
            secondsWithPadding
        )
      }
    }
  }

  fun showError(error: String) {
    errorTextView.text = error
    errorTextView.visibility = View.VISIBLE
    clearPin()
  }

  override fun hideError() {
    errorTextView.visibility = View.GONE
  }

  override fun showIncorrectPinErrorForFirstAttempt() {
    showError(resources.getString(R.string.pinentry_error_incorrect_pin_on_first_attempt))
  }

  @SuppressLint("StringFormatMatches")
  override fun showIncorrectPinErrorOnSubsequentAttempts(remaining: Int) {
    showError(resources.getString(R.string.pinentry_error_incorrect_pin_attempts_remaining, remaining.toString()))
  }

  @SuppressLint("StringFormatMatches")
  override fun showIncorrectAttemptsLimitReachedError(attemptsMade: Int) {
    showError(resources.getString(R.string.pinentry_error_incorrect_pin_attempts_limit_reached, attemptsMade.toString()))
  }

  override fun showNetworkError() {
    showError(resources.getString(R.string.api_network_error))
  }

  override fun showServerError() {
    showError(resources.getString(R.string.api_server_error))
  }

  override fun showUnexpectedError() {
    showError(resources.getString(R.string.api_unexpected_error))
  }

  override fun clearPin() {
    pinEditText.text = null
  }

  override fun pinVerified(data: Any?) {
    // TODO: Change the type to an `Any?`
    downstreamUiEvents.onNext(PinAuthenticated(data as String))
  }

  /** Defaults to visible. */
  fun setForgotButtonVisible(visible: Boolean) {
    if (visible) {
      forgotPinButton.visibility = View.VISIBLE
      contentContainer.setPaddingBottom(R.dimen.pinentry_content_bottom_spacing_with_forgot_pin)

    } else {
      forgotPinButton.visibility = View.GONE
      contentContainer.setPaddingBottom(R.dimen.pinentry_content_bottom_spacing_without_forgot_pin)
    }
  }

  interface Injector {
    fun inject(target: PinEntryCardView)
  }
}

