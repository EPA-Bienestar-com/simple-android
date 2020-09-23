package org.simple.clinic.summary.teleconsultation.contactdoctor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.reactivex.Observable
import kotlinx.android.synthetic.main.sheet_contact_doctor_new.*
import org.simple.clinic.ClinicApp
import org.simple.clinic.R
import org.simple.clinic.di.InjectorProviderContextWrapper
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.summary.teleconsultation.sync.MedicalOfficer
import org.simple.clinic.util.LocaleOverrideContextWrapper
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.util.wrap
import org.simple.clinic.widgets.BottomSheetActivity
import org.simple.clinic.widgets.DividerItemDecorator
import org.simple.clinic.widgets.ItemAdapter
import org.simple.clinic.widgets.dp
import java.util.Locale
import javax.inject.Inject

class ContactDoctorSheet : BottomSheetActivity(), ContactDoctorUi {

  companion object {
    fun intent(context: Context): Intent {
      return Intent(context, ContactDoctorSheet::class.java)
    }
  }

  @Inject
  lateinit var effectHandler: ContactDoctorEffectHandler

  @Inject
  lateinit var locale: Locale

  private val itemAdapter = ItemAdapter(DoctorListItem.DiffCallback())

  private val delegate by unsafeLazy {
    val uiRenderer = ContactDoctorUiRenderer(this)

    MobiusDelegate.forActivity(
        events = Observable.never(),
        defaultModel = ContactDoctorModel.create(),
        init = ContactDoctorInit(),
        update = ContactDoctorUpdate(),
        effectHandler = effectHandler.build(),
        modelUpdateListener = uiRenderer::render
    )
  }

  private lateinit var component: ContactDoctorComponent

  override fun onStart() {
    super.onStart()
    delegate.start()
  }

  override fun onStop() {
    delegate.stop()
    super.onStop()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    delegate.onSaveInstanceState(outState)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.sheet_contact_doctor_new)
    delegate.onRestoreInstanceState(savedInstanceState)

    doctorsRecyclerView.adapter = itemAdapter
    doctorsRecyclerView.addItemDecoration(DividerItemDecorator(
        context = this,
        marginStart = 16.dp,
        marginEnd = 16.dp
    ))
  }

  override fun showMedicalOfficers(medicalOfficers: List<MedicalOfficer>) {
    itemAdapter.submitList(DoctorListItem.from(medicalOfficers))
  }

  override fun attachBaseContext(baseContext: Context) {
    setupDiGraph()

    val wrappedContext = baseContext
        .wrap { LocaleOverrideContextWrapper.wrap(it, locale) }
        .wrap { InjectorProviderContextWrapper.wrap(it, component) }
        .wrap { ViewPumpContextWrapper.wrap(it) }

    super.attachBaseContext(wrappedContext)
  }

  private fun setupDiGraph() {
    component = ClinicApp.appComponent
        .contactDoctorComponent()
        .activity(this)
        .build()

    component.inject(this)
  }
}
