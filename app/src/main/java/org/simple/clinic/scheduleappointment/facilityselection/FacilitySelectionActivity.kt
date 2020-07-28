package org.simple.clinic.scheduleappointment.facilityselection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.screen_patient_facility_change.*
import org.simple.clinic.ClinicApp
import org.simple.clinic.R
import org.simple.clinic.bindUiToController
import org.simple.clinic.di.InjectorProviderContextWrapper
import org.simple.clinic.facility.Facility
import org.simple.clinic.util.LocaleOverrideContextWrapper
import org.simple.clinic.util.wrap
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import java.util.Locale
import javax.inject.Inject

class FacilitySelectionActivity : AppCompatActivity() {

  companion object {
    const val EXTRA_SELECTED_FACILITY = "selected_facility"

    fun selectedFacility(data: Intent): Facility {
      return data.getParcelableExtra(EXTRA_SELECTED_FACILITY)!!
    }
  }

  @Inject
  lateinit var locale: Locale

  @Inject
  lateinit var controller: FacilitySelectionActivityController

  private val onDestroys = PublishSubject.create<ScreenDestroyed>()

  private lateinit var component: FacilitySelectionActivityComponent

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.screen_patient_facility_change)

    bindUiToController(
        ui = this,
        events = Observable.merge(
            screenCreates(),
            facilityClicks()
        ),
        controller = controller,
        screenDestroys = onDestroys
    )

    facilityPickerView.backClicked = this@FacilitySelectionActivity::finish
  }

  override fun attachBaseContext(baseContext: Context) {
    component = ClinicApp
        .appComponent
        .patientFacilityChangeComponentBuilder()
        .activity(this)
        .build()
    component.inject(this)

    val wrappedContext = baseContext
        .wrap { LocaleOverrideContextWrapper.wrap(it, locale) }
        .wrap { ViewPumpContextWrapper.wrap(it) }
        .wrap { InjectorProviderContextWrapper.wrap(it, component) }

    super.attachBaseContext(wrappedContext)
  }

  override fun onDestroy() {
    onDestroys.onNext(ScreenDestroyed())
    super.onDestroy()
  }

  private fun screenCreates() = Observable.just<UiEvent>(ScreenCreated())

  private fun facilityClicks(): Observable<UiEvent> {
    return Observable.create { emitter ->
      facilityPickerView.facilitySelectedCallback = { emitter.onNext(FacilitySelected(it)) }
    }
  }

  fun sendSelectedFacility(selectedFacility: Facility) {
    val intent = Intent()
    intent.putExtra(EXTRA_SELECTED_FACILITY, selectedFacility)
    setResult(Activity.RESULT_OK, intent)
    finish()
  }
}
