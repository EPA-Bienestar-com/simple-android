package org.simple.clinic.bp.entry

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.cast
import io.reactivex.rxkotlin.toObservable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.activity.TheActivity
import org.simple.clinic.bp.entry.BloodPressureEntrySheet.ScreenType.BP_ENTRY
import org.simple.clinic.bp.entry.BloodPressureEntrySheet.ScreenType.DATE_ENTRY
import org.simple.clinic.router.screen.BackPressInterceptCallback
import org.simple.clinic.router.screen.BackPressInterceptor
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.widgets.BottomSheetActivity
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.ViewFlipperWithDebugPreview
import org.simple.clinic.widgets.displayedChildResId
import org.simple.clinic.widgets.setTextAndCursor
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

class BloodPressureEntrySheet : BottomSheetActivity() {

  @Inject
  @field:Named("bp_entry_controller")
  lateinit var controller: ObservableTransformer<UiEvent, UiChange>

  @Inject
  lateinit var screenRouter: ScreenRouter

  private val rootLayout by bindView<LinearLayoutWithPreImeKeyEventListener>(R.id.bloodpressureentry_root)
  private val systolicEditText by bindView<EditText>(R.id.bloodpressureentry_systolic)
  private val diastolicEditText by bindView<EditTextWithBackspaceListener>(R.id.bloodpressureentry_diastolic)
  private val bpErrorTextView by bindView<TextView>(R.id.bloodpressureentry_bp_error)
  private val enterBloodPressureTitleTextView by bindView<TextView>(R.id.bloodpressureentry_enter_blood_pressure)
  private val editBloodPressureTitleTextView by bindView<TextView>(R.id.bloodpressureentry_edit_blood_pressure)
  private val removeBloodPressureButton by bindView<Button>(R.id.bloodpressureentry_remove)
  private val nextArrowButton by bindView<View>(R.id.bloodpressureentry_next_arrow)
  private val previousArrowButton by bindView<View>(R.id.bloodpressureentry_previous_arrow)
  private val dayEditText by bindView<EditText>(R.id.bloodpressureentry_day)
  private val monthEditText by bindView<EditText>(R.id.bloodpressureentry_month)
  private val yearEditText by bindView<EditText>(R.id.bloodpressureentry_year)
  private val viewFlipper by bindView<ViewFlipperWithDebugPreview>(R.id.bloodpressureentry_view_flipper)
  private val dateErrorTextView by bindView<TextView>(R.id.bloodpressureentry_date_error)

  private val screenDestroys = PublishSubject.create<ScreenDestroyed>()

  enum class ScreenType {
    BP_ENTRY,
    DATE_ENTRY
  }

  companion object {
    private const val KEY_OPEN_AS = "openAs"
    private const val EXTRA_WAS_BP_SAVED = "wasBpSaved"

    fun intentForNewBp(context: Context, patientUuid: UUID): Intent {
      return Intent(context, BloodPressureEntrySheet::class.java)
          .putExtra(KEY_OPEN_AS, OpenAs.New(patientUuid))
    }

    fun intentForUpdateBp(context: Context, bloodPressureMeasurementUuid: UUID): Intent {
      return Intent(context, BloodPressureEntrySheet::class.java)
          .putExtra(KEY_OPEN_AS, OpenAs.Update(bloodPressureMeasurementUuid))
    }

    fun wasBloodPressureSaved(data: Intent): Boolean {
      return data.getBooleanExtra(EXTRA_WAS_BP_SAVED, false)
    }
  }

  @SuppressLint("CheckResult")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.sheet_blood_pressure_entry)
    TheActivity.component.inject(this)

    Observable
        .mergeArray(
            sheetCreates(),
            screenDestroys,
            systolicTextChanges(),
            diastolicTextChanges(),
            diastolicImeOptionClicks(),
            diastolicBackspaceClicks(),
            removeClicks(),
            nextArrowClicks(),
            previousArrowClicks(),
            hardwareBackPresses(),
            screenTypeChanges(),
            dayTextChanges(),
            monthTextChanges(),
            yearTextChanges())
        .observeOn(Schedulers.io())
        .compose(controller)
        .observeOn(AndroidSchedulers.mainThread())
        .takeUntil(screenDestroys)
        .subscribe { uiChange -> uiChange(this) }

    // Dismiss this sheet when the keyboard is dismissed.
    rootLayout.backKeyPressInterceptor = { super.onBackgroundClick() }
  }

  override fun onDestroy() {
    screenDestroys.onNext(ScreenDestroyed())
    super.onDestroy()
  }

  override fun onBackgroundClick() {
    if (systolicEditText.text.isBlank() && diastolicEditText.text.isBlank()) {
      super.onBackgroundClick()
    }
  }

  private fun sheetCreates(): Observable<UiEvent> {
    val openAs = intent.extras!!.getParcelable(KEY_OPEN_AS) as OpenAs
    return Observable
        .just(BloodPressureEntrySheetCreated(openAs))
        // TODO: Update: Now that we've moved to ReplayUntilScreenIsDestroyed, is this still required?
        // This delay stops the race condition (?) that happens frequently with replay().refCount()
        // in the controller. Temporary workaround until we figure out what exactly is going on.
        .delay(100L, TimeUnit.MILLISECONDS)
        .cast()
  }

  private fun systolicTextChanges() = RxTextView.textChanges(systolicEditText)
      .map(CharSequence::toString)
      .map(::BloodPressureSystolicTextChanged)

  private fun diastolicTextChanges() = RxTextView.textChanges(diastolicEditText)
      .map(CharSequence::toString)
      .map(::BloodPressureDiastolicTextChanged)

  private fun diastolicImeOptionClicks(): Observable<BloodPressureSaveClicked> {
    return listOf(systolicEditText, diastolicEditText, dayEditText, monthEditText, yearEditText)
        .map { RxTextView.editorActions(it) { actionId -> actionId == EditorInfo.IME_ACTION_DONE } }
        .toObservable()
        .flatMap { it }
        .map { BloodPressureSaveClicked }
  }

  private fun diastolicBackspaceClicks(): Observable<UiEvent> {
    return diastolicEditText
        .backspaceClicks
        .map { BloodPressureDiastolicBackspaceClicked }
  }

  private fun removeClicks(): Observable<UiEvent> =
      RxView
          .clicks(removeBloodPressureButton)
          .map { BloodPressureRemoveClicked }

  private fun nextArrowClicks(): Observable<UiEvent> =
      RxView
          .clicks(nextArrowButton)
          .map { BloodPressureNextArrowClicked }

  private fun previousArrowClicks(): Observable<UiEvent> =
      RxView
          .clicks(previousArrowButton)
          .map { BloodPressurePreviousArrowClicked }

  private fun hardwareBackPresses(): Observable<UiEvent> {
    return Observable.create { emitter ->
      val interceptor = object : BackPressInterceptor {
        override fun onInterceptBackPress(callback: BackPressInterceptCallback) {
          emitter.onNext(BloodPressureBackPressed)
          callback.markBackPressIntercepted()
        }
      }
      emitter.setCancellable { screenRouter.unregisterBackPressInterceptor(interceptor) }
      screenRouter.registerBackPressInterceptor(interceptor)
    }
  }

  private fun screenTypeChanges(): Observable<UiEvent> =
      viewFlipper
          .displayedChildChanges
          .map {
            BloodPressureScreenChanged(when (viewFlipper.displayedChildResId) {
              R.id.bloodpressureentry_flipper_bp_entry -> BP_ENTRY
              R.id.bloodpressureentry_flipper_date_entry -> DATE_ENTRY
              else -> throw AssertionError()
            })
          }

  private fun dayTextChanges() =
      RxTextView.textChanges(dayEditText)
          .map(CharSequence::toString)
          .map(::BloodPressureDayChanged)

  private fun monthTextChanges() =
      RxTextView.textChanges(monthEditText)
          .map(CharSequence::toString)
          .map(::BloodPressureMonthChanged)

  private fun yearTextChanges() =
      RxTextView.textChanges(yearEditText)
          .map(CharSequence::toString)
          .map(::BloodPressureYearChanged)

  fun changeFocusToDiastolic() {
    diastolicEditText.requestFocus()
  }

  fun changeFocusToSystolic() {
    systolicEditText.requestFocus()
  }

  fun setBpSavedResultAndFinish() {
    val intent = Intent()
    intent.putExtra(EXTRA_WAS_BP_SAVED, true)
    setResult(Activity.RESULT_OK, intent)
    finish()
  }

  fun hideBpErrorMessage() {
    bpErrorTextView.visibility = View.GONE
  }

  fun showSystolicLessThanDiastolicError() {
    bpErrorTextView.text = getString(R.string.bloodpressureentry_error_systolic_more)
    bpErrorTextView.visibility = View.VISIBLE
  }

  fun showSystolicLowError() {
    bpErrorTextView.text = getString(R.string.bloodpressureentry_error_systolic_70)
    bpErrorTextView.visibility = View.VISIBLE
  }

  fun showSystolicHighError() {
    bpErrorTextView.text = getString(R.string.bloodpressureentry_error_systolic_300)
    bpErrorTextView.visibility = View.VISIBLE
  }

  fun showDiastolicLowError() {
    bpErrorTextView.text = getString(R.string.bloodpressureentry_error_diastolic_40)
    bpErrorTextView.visibility = View.VISIBLE
  }

  fun showDiastolicHighError() {
    bpErrorTextView.text = getString(R.string.bloodpressureentry_error_diastolic_180)
    bpErrorTextView.visibility = View.VISIBLE
  }

  fun showSystolicEmptyError() {
    bpErrorTextView.text = getString(R.string.bloodpressureentry_error_systolic_empty)
    bpErrorTextView.visibility = View.VISIBLE
  }

  fun showDiastolicEmptyError() {
    bpErrorTextView.text = getString(R.string.bloodpressureentry_error_diastolic_empty)
    bpErrorTextView.visibility = View.VISIBLE
  }

  fun setSystolic(systolic: String) {
    systolicEditText.setTextAndCursor(systolic)
  }

  fun setDiastolic(diastolic: String) {
    diastolicEditText.setTextAndCursor(diastolic)
  }

  fun showRemoveBpButton() {
    removeBloodPressureButton.visibility = View.VISIBLE
    removeBloodPressureButton.isEnabled = true
  }

  fun hideRemoveBpButton() {
    removeBloodPressureButton.visibility = View.GONE
    removeBloodPressureButton.isEnabled = false
  }

  fun showEnterNewBloodPressureTitle() {
    enterBloodPressureTitleTextView.visibility = View.VISIBLE
  }

  fun showEditBloodPressureTitle() {
    editBloodPressureTitleTextView.visibility = View.VISIBLE
  }

  fun showConfirmRemoveBloodPressureDialog(uuid: UUID) {
    ConfirmRemoveBloodPressureDialog.show(uuid, supportFragmentManager)
  }

  fun showBpEntryScreen() {
    viewFlipper.displayedChildResId = R.id.bloodpressureentry_flipper_bp_entry
  }

  fun showDateEntryScreen() {
    viewFlipper.displayedChildResId = R.id.bloodpressureentry_flipper_date_entry
    yearEditText.requestFocus()
  }

  fun showInvalidDateError() {
    dateErrorTextView.setText(R.string.bloodpressureentry_error_date_invalid_pattern)
    dateErrorTextView.visibility = View.VISIBLE
  }

  fun showDateIsInFutureError() {
    dateErrorTextView.setText(R.string.bloodpressureentry_error_date_is_in_future)
    dateErrorTextView.visibility = View.VISIBLE
  }

  fun hideDateErrorMessage() {
    dateErrorTextView.visibility = View.GONE
  }

  fun setDate(dayOfMonth: String, month: String, twoDigitYear: String) {
    dayEditText.setTextAndCursor(dayOfMonth)
    monthEditText.setTextAndCursor(month)
    yearEditText.setTextAndCursor(twoDigitYear)
  }

  fun setNextArrowEnabled(enabled: Boolean) {
    nextArrowButton.isEnabled = enabled
  }
}
