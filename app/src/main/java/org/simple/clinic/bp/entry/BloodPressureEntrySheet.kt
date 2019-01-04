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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.cast
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.activity.TheActivity
import org.simple.clinic.widgets.BottomSheetActivity
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.setTextAndCursor
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BloodPressureEntrySheet : BottomSheetActivity() {

  @Inject
  lateinit var controller: BloodPressureEntrySheetController

  private val rootLayout by bindView<LinearLayoutWithPreImeKeyEventListener>(R.id.bloodpressureentry_root)
  private val systolicEditText by bindView<EditText>(R.id.bloodpressureentry_systolic)
  private val diastolicEditText by bindView<EditTextWithBackspaceListener>(R.id.bloodpressureentry_diastolic)
  private val errorTextView by bindView<TextView>(R.id.bloodpressureentry_error)
  private val enterBloodPressureTitleTextView by bindView<TextView>(R.id.bloodpressureentry_enter_blood_pressure)
  private val editBloodPressureTitleTextView by bindView<TextView>(R.id.bloodpressureentry_edit_blood_pressure)
  private val removeBloodPressureButton by bindView<Button>(R.id.bloodpressureentry_remove)

  private val onDestroys = PublishSubject.create<Any>()

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
            systolicTextChanges(),
            diastolicTextChanges(),
            diastolicImeOptionClicks(),
            diastolicBackspaceClicks(),
            removeClicks())
        .observeOn(Schedulers.io())
        .compose(controller)
        .observeOn(AndroidSchedulers.mainThread())
        .takeUntil(onDestroys)
        .subscribe { uiChange -> uiChange(this) }

    // Dismiss this sheet when the keyboard is dismissed.
    rootLayout.backKeyPressInterceptor = { super.onBackgroundClick() }
  }

  override fun onDestroy() {
    onDestroys.onNext(Any())
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
    return Observable
        .merge(
            RxTextView.editorActions(systolicEditText) { actionId -> actionId == EditorInfo.IME_ACTION_DONE },
            RxTextView.editorActions(diastolicEditText) { actionId -> actionId == EditorInfo.IME_ACTION_DONE })
        .map { BloodPressureSaveClicked() }
  }

  private fun diastolicBackspaceClicks(): Observable<UiEvent> {
    return diastolicEditText
        .backspaceClicks
        .map { BloodPressureDiastolicBackspaceClicked }
  }

  private fun removeClicks(): Observable<UiEvent> =
      RxView.clicks(removeBloodPressureButton).map { BloodPressureRemoveClicked }

  fun changeFocusToDiastolic() {
    diastolicEditText.requestFocus()
  }

  fun changeFocusToSystolic() {
    systolicEditText.requestFocus()
  }

  fun setBPSavedResultAndFinish() {
    val intent = Intent()
    intent.putExtra(EXTRA_WAS_BP_SAVED, true)
    setResult(Activity.RESULT_OK, intent)
    finish()
  }

  fun hideErrorMessage() {
    errorTextView.visibility = View.GONE
  }

  fun showSystolicLessThanDiastolicError() {
    errorTextView.text = getString(R.string.bloodpressureentry_error_systolic_more)
    errorTextView.visibility = View.VISIBLE
  }

  fun showSystolicLowError() {
    errorTextView.text = getString(R.string.bloodpressureentry_error_systolic_70)
    errorTextView.visibility = View.VISIBLE
  }

  fun showSystolicHighError() {
    errorTextView.text = getString(R.string.bloodpressureentry_error_systolic_300)
    errorTextView.visibility = View.VISIBLE
  }

  fun showDiastolicLowError() {
    errorTextView.text = getString(R.string.bloodpressureentry_error_diastolic_40)
    errorTextView.visibility = View.VISIBLE
  }

  fun showDiastolicHighError() {
    errorTextView.text = getString(R.string.bloodpressureentry_error_diastolic_180)
    errorTextView.visibility = View.VISIBLE
  }

  fun showSystolicEmptyError() {
    errorTextView.text = getString(R.string.bloodpressureentry_error_systolic_empty)
    errorTextView.visibility = View.VISIBLE
  }

  fun showDiastolicEmptyError() {
    errorTextView.text = getString(R.string.bloodpressureentry_error_diastolic_empty)
    errorTextView.visibility = View.VISIBLE
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
}
