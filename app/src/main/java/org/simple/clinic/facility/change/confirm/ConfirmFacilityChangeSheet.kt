package org.simple.clinic.facility.change.confirm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import com.jakewharton.rxbinding3.view.clicks
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import org.simple.clinic.ClinicApp
import org.simple.clinic.R
import org.simple.clinic.databinding.SheetConfirmFacilityChangeBinding
import org.simple.clinic.di.InjectorProviderContextWrapper
import org.simple.clinic.facility.Facility
import org.simple.clinic.facility.change.confirm.di.ConfirmFacilityChangeComponent
import org.simple.clinic.feature.Features
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.util.withLocale
import org.simple.clinic.util.wrap
import org.simple.clinic.widgets.BottomSheetActivity
import org.simple.clinic.widgets.UiEvent
import java.util.Locale
import javax.inject.Inject

class ConfirmFacilityChangeSheet : BottomSheetActivity(), ConfirmFacilityChangeUiActions {

  companion object {
    lateinit var component: ConfirmFacilityChangeComponent

    private const val SELECTED_FACILITY = "selected_facility"

    fun intent(
        context: Context,
        facility: Facility
    ): Intent {
      val intent = Intent(context, ConfirmFacilityChangeSheet::class.java)
      intent.putExtra(SELECTED_FACILITY, facility)
      return intent
    }
  }

  @Inject
  lateinit var locale: Locale

  @Inject
  lateinit var effectHandlerFactory: ConfirmFacilityChangeEffectHandler.Factory

  @Inject
  lateinit var features: Features

  private val selectedFacility: Facility by lazy {
    intent.getParcelableExtra<Facility>(SELECTED_FACILITY)!!
  }

  private val events: Observable<UiEvent> by unsafeLazy {
    positiveButtonClicks()
  }

  private val delegate by unsafeLazy {
    MobiusDelegate.forActivity(
        events.ofType(),
        ConfirmFacilityChangeModel.create(),
        ConfirmFacilityChangeUpdate(),
        effectHandlerFactory.create(this).build(),
        ConfirmFacilityChangeInit()
    )
  }

  private lateinit var binding: SheetConfirmFacilityChangeBinding

  private val facilityName
    get() = binding.facilityName

  private val cancelButton
    get() = binding.cancelButton

  private val yesButton
    get() = binding.yesButton

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = SheetConfirmFacilityChangeBinding.inflate(layoutInflater)
    setContentView(binding.root)

    facilityName.text = getString(R.string.confirmfacilitychange_facility_name, selectedFacility.name)
    cancelButton.setOnClickListener {
      closeSheetAfterCancel()
    }

    delegate.onRestoreInstanceState(savedInstanceState)
  }

  private fun closeSheetAfterCancel() {
    val intent = Intent()
    setResult(Activity.RESULT_CANCELED, intent)
    finish()
  }

  override fun attachBaseContext(baseContext: Context) {
    setupDi()

    val wrappedContext = baseContext
        .wrap { InjectorProviderContextWrapper.wrap(it, component) }
        .wrap { ViewPumpContextWrapper.wrap(it) }

    super.attachBaseContext(wrappedContext)
    applyOverrideConfiguration(Configuration())
  }

  override fun applyOverrideConfiguration(overrideConfiguration: Configuration) {
    super.applyOverrideConfiguration(overrideConfiguration.withLocale(locale, features))
  }

  override fun onStart() {
    super.onStart()
    delegate.start()
  }

  override fun onStop() {
    super.onStop()
    delegate.stop()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    delegate.onSaveInstanceState(outState)
    super.onSaveInstanceState(outState)
  }

  private fun setupDi() {
    component = ClinicApp.appComponent
        .confirmFacilityChangeComponent()
        .activity(this)
        .build()

    component.inject(this)
  }

  private fun positiveButtonClicks(): Observable<UiEvent> =
      yesButton.clicks().map { FacilityChangeConfirmed(selectedFacility) }

  override fun closeSheet() {
    val intent = Intent()
    setResult(Activity.RESULT_OK, intent)
    finish()
  }
}
