package org.simple.clinic.facility.change.confirm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import kotlinx.android.synthetic.main.sheet_confirm_facility_change.*
import org.simple.clinic.ClinicApp
import org.simple.clinic.R
import org.simple.clinic.facility.Facility
import org.simple.clinic.facility.change.confirm.di.ConfirmFacilityChangeComponent
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.BottomSheetActivity
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

class ConfirmFacilityChangeSheet : BottomSheetActivity(), ConfirmFacilityChangeUiActions {

  companion object {
    lateinit var component: ConfirmFacilityChangeComponent

    private const val SELECTED_FACILITY = "seleceted_facility"

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
  lateinit var effectHandlerFactory: ConfirmFacilityChangeEffectHandler.Factory

  private val selectedFacility: Facility by lazy {
    intent.getParcelableExtra<Facility>(SELECTED_FACILITY)!!
  }

  private val events: Observable<UiEvent> by unsafeLazy {
    positiveButtonClicks()
  }

  private val delegate by unsafeLazy {
    MobiusDelegate.forActivity(
        events.ofType(),
        ConfirmFacilityChangeModel(),
        ConfirmFacilityChangeUpdate(),
        effectHandlerFactory.create(this).build(),
        ConfirmFacilityChangeInit(),
        { /* No-op, there's nothing to render */ }
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.sheet_confirm_facility_change)
    setupDi()

    delegate.onRestoreInstanceState(savedInstanceState)
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
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}
