package org.simple.clinic.scanid

import android.content.Context
import android.graphics.Rect
import android.text.InputFilter.LengthFilter
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.constraintlayout.widget.ConstraintLayout
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.Observable
import kotlinx.android.synthetic.main.screen_scan_simple.view.*
import org.simple.clinic.R
import org.simple.clinic.addidtopatient.searchforpatient.AddIdToPatientSearchScreenKey
import org.simple.clinic.bindUiToController
import org.simple.clinic.feature.Feature.CameraXQrScanner
import org.simple.clinic.feature.Features
import org.simple.clinic.main.TheActivity
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.patient.businessid.Identifier.IdentifierType.BpPassport.SHORT_CODE_LENGTH
import org.simple.clinic.router.screen.RouterDirection
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.scanid.ShortCodeValidationResult.Failure.Empty
import org.simple.clinic.scanid.ui.ShortCodeSpanWatcher
import org.simple.clinic.shortcodesearchresult.ShortCodeSearchResultScreenKey
import org.simple.clinic.summary.OpenIntention
import org.simple.clinic.summary.PatientSummaryScreenKey
import org.simple.clinic.util.UtcClock
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.hideKeyboard
import org.simple.clinic.widgets.qrcodescanner.IQrCodeScannerView
import org.simple.clinic.widgets.qrcodescanner.QrCodeScannerView
import org.simple.clinic.widgets.qrcodescanner.QrCodeScannerView_Old
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class ScanSimpleIdScreen(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var controller: ScanSimpleIdScreenController

  @Inject
  lateinit var utcClock: UtcClock

  @Inject
  lateinit var features: Features

  private val keyboardVisibilityDetector = KeyboardVisibilityDetector()

  private lateinit var qrCodeScannerView: IQrCodeScannerView

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    TheActivity.component.inject(this)
    // It is possible that going back via the app bar from future screens will come back to this
    // screen with the keyboard open. So, we hide it here.
    hideKeyboard()
    toolBar.setNavigationOnClickListener { screenRouter.pop() }
    setupShortCodeTextField()

    qrCodeScannerView = if (features.isEnabled(CameraXQrScanner)) {
      QrCodeScannerView(context)
    } else {
      QrCodeScannerView_Old(context)
    }

    qrCodeScannerViewContainer.addView(qrCodeScannerView as View)

    bindUiToController(
        ui = this,
        events = Observable.mergeArray(qrScans(), keyboardEvents(), qrCodeChanges(), doneClicks()),
        controller = controller,
        screenDestroys = RxView.detaches(this).map { ScreenDestroyed() }
    )
  }

  private fun setupShortCodeTextField() {
    with(shortCodeText) {
      filters = arrayOf(LengthFilter(SHORT_CODE_LENGTH))
      addTextChangedListener(ShortCodeSpanWatcher())
    }
  }

  private fun qrScans(): Observable<UiEvent> {
    val scans = qrCodeScannerView
        .scans()
    return scans.map(::ScanSimpleIdScreenQrCodeScanned)
  }

  private fun qrCodeChanges(): Observable<UiEvent> {
    return RxTextView
        .textChangeEvents(shortCodeText)
        .map { ShortCodeChanged }
  }

  private fun doneClicks(): Observable<UiEvent> {
    return RxTextView
        .editorActionEvents(shortCodeText)
        .map { ShortCodeSearched(ShortCodeInput(shortCodeText.text.toString())) }
  }

  private fun keyboardEvents(): Observable<UiEvent> {
    return Observable.create<UiEvent> { emitter ->
      keyboardVisibilityDetector.registerListener(this) { isVisible ->
        val keyboardEvent = if (isVisible) ShowKeyboard else HideKeyboard
        emitter.onNext(keyboardEvent)
      }

      emitter.setCancellable {
        keyboardVisibilityDetector.unregisterListener(this)
      }
    }
  }

  fun openPatientSummary(patientUuid: UUID) {
    screenRouter.popAndPush(
        PatientSummaryScreenKey(
            patientUuid = patientUuid,
            intention = OpenIntention.ViewExistingPatient,
            screenCreatedTimestamp = Instant.now(utcClock)
        ),
        RouterDirection.FORWARD
    )
  }

  fun openAddIdToPatientScreen(identifier: Identifier) {
    screenRouter.popAndPush(AddIdToPatientSearchScreenKey(identifier), RouterDirection.FORWARD)
  }

  fun showShortCodeValidationError(failure: ShortCodeValidationResult) {
    shortCodeErrorText.visibility = View.VISIBLE
    val validationErrorMessage = if (failure == Empty) {
      R.string.scansimpleid_shortcode_error_empty
    } else {
      R.string.scansimpleid_shortcode_error_not_required_length
    }
    shortCodeErrorText.text = resources.getString(validationErrorMessage)
  }

  fun hideShortCodeValidationError() {
    shortCodeErrorText.visibility = View.GONE
  }

  fun openPatientShortCodeSearch(validShortCode: String) {
    screenRouter.popAndPush(ShortCodeSearchResultScreenKey(validShortCode), RouterDirection.FORWARD)
  }

  fun hideQrCodeScannerView() {
    qrCodeScannerView.hideQrCodeScanner()
  }

  fun showQrCodeScannerView() {
    qrCodeScannerView.showQrCodeScanner()
  }
}

class KeyboardVisibilityDetector {
  private var isKeyboardShowing = false
  private var layoutListener: OnGlobalLayoutListener? = null

  fun registerListener(view: View, visibilityChangeListener: (Boolean) -> Unit) {
    check(layoutListener == null) {
      "A listener is already registered. You must call `unregisterListener` before calling `registerListener` again."
    }

    layoutListener = OnGlobalLayoutListener {
      val rect = Rect()
      view.getWindowVisibleDisplayFrame(rect)
      val screenHeight = view.rootView.height

      val keypadHeight = screenHeight - rect.bottom

      if (keypadHeight > screenHeight * 0.15) { // 0.15 is a magic number, completely irrational
        if (!isKeyboardShowing) {
          isKeyboardShowing = true
          visibilityChangeListener(true)
        }
      } else {
        if (isKeyboardShowing) {
          isKeyboardShowing = false
          visibilityChangeListener(false)
        }
      }
    }
    view.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
  }

  fun unregisterListener(view: View) {
    view.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
  }
}
