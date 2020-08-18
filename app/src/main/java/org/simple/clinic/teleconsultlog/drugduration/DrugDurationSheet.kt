package org.simple.clinic.teleconsultlog.drugduration

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import com.jakewharton.rxbinding3.widget.editorActions
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import kotlinx.android.synthetic.main.sheet_drug_duration.*
import org.simple.clinic.ClinicApp
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.di.InjectorProviderContextWrapper
import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.teleconsultlog.drugduration.di.DrugDurationComponent
import org.simple.clinic.util.LocaleOverrideContextWrapper
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.util.wrap
import org.simple.clinic.widgets.BottomSheetActivity
import org.simple.clinic.widgets.setTextAndCursor
import org.simple.clinic.widgets.textChanges
import java.util.Locale
import javax.inject.Inject

class DrugDurationSheet : BottomSheetActivity(), DrugDurationUi, DrugDurationUiActions {

  @Inject
  lateinit var locale: Locale

  @Inject
  lateinit var effectHandlerFactory: DrugDurationEffectHandler.Factory

  companion object {
    private const val EXTRA_DRUG_UUID = "prescribedDrugUuid"
    private const val EXTRA_DRUG_NAME = "prescribedDrugTitle"
    private const val EXTRA_DRUG_DOSAGE = "prescribedDrugDosage"
    private const val EXTRA_DURATION = "drugDuration"
    private const val EXTRA_SAVED_DRUG_UUID = "savedDrugUuid"
    private const val EXTRA_SAVED_DURATION = "savedDrugDuration"

    fun intent(
        context: Context,
        drug: PrescribedDrug,
        drugDuration: String
    ): Intent {
      return Intent(context, DrugDurationSheet::class.java).apply {
        putExtra(EXTRA_DRUG_UUID, drug.uuid)
        putExtra(EXTRA_DRUG_NAME, drug.name)
        putExtra(EXTRA_DRUG_DOSAGE, drug.dosage)
        putExtra(EXTRA_DURATION, drugDuration)
      }
    }
  }

  private lateinit var component: DrugDurationComponent

  private val events by unsafeLazy {
    Observable
        .merge(
            imeClicks(),
            durationChanges()
        )
        .compose(ReportAnalyticsEvents())
  }

  private val delegate by unsafeLazy {
    val duration = intent.getStringExtra(EXTRA_DURATION)!!
    val uiRenderer = DrugDurationUiRenderer(this)

    MobiusDelegate.forActivity(
        events = events.ofType(),
        defaultModel = DrugDurationModel.create(duration),
        init = DrugDurationInit(),
        update = DrugDurationUpdate(),
        effectHandler = effectHandlerFactory.create(this).build(),
        modelUpdateListener = uiRenderer::render
    )
  }

  override fun onStart() {
    super.onStart()
    delegate.start()
  }

  override fun onStop() {
    delegate.stop()
    super.onStop()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.sheet_drug_duration)
    delegate.onRestoreInstanceState(savedInstanceState)

    val drugName = intent.getStringExtra(EXTRA_DRUG_NAME)!!
    val drugDosage = intent.getStringExtra(EXTRA_DRUG_NAME)!!

    drugDurationTitleTextView.text = getString(R.string.drug_duration_title, drugName, drugDosage)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    delegate.onSaveInstanceState(outState)
  }

  override fun attachBaseContext(baseContext: Context) {
    setupDi()

    val wrappedContext = baseContext
        .wrap { LocaleOverrideContextWrapper.wrap(it, locale) }
        .wrap { InjectorProviderContextWrapper.wrap(it, component) }
        .wrap { ViewPumpContextWrapper.wrap(it) }

    super.attachBaseContext(wrappedContext)
  }

  private fun setupDi() {
    component = ClinicApp.appComponent
        .drugDurationComponent()
        .activity(this)
        .build()

    component.inject(this)
  }

  private fun imeClicks(): Observable<DrugDurationEvent> {
    return drugDurationEditText
        .editorActions { it == EditorInfo.IME_ACTION_DONE }
        .map { DrugDurationSaveClicked }
  }

  private fun durationChanges(): Observable<DrugDurationEvent> {
    return drugDurationEditText
        .textChanges { DurationChanged(it) }
  }

  override fun showBlankDurationError() {
    drugDurationErrorTextView.text = getString(R.string.drug_duration_empty_error)
    drugDurationErrorTextView.visibility = View.VISIBLE
  }

  override fun hideDurationError() {
    drugDurationErrorTextView.text = null
    drugDurationErrorTextView.visibility = View.GONE
  }

  override fun saveDrugDuration(duration: Int) {
    val drugUuid = intent.getStringExtra(EXTRA_DRUG_UUID)!!
    val intent = Intent().apply {
      putExtra(EXTRA_SAVED_DRUG_UUID, drugUuid)
      putExtra(EXTRA_SAVED_DURATION, duration)
    }
    setResult(Activity.RESULT_OK, intent)
    finish()
  }

  override fun setDrugDuration(duration: String?) {
    drugDurationEditText.setTextAndCursor(duration)
  }
}
