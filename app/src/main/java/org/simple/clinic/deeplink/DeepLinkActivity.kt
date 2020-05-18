package org.simple.clinic.deeplink

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.reactivex.Observable
import org.simple.clinic.ClinicApp
import org.simple.clinic.deeplink.di.DeepLinkComponent
import org.simple.clinic.di.InjectorProviderContextWrapper
import org.simple.clinic.main.TheActivity
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.setup.SetupActivity
import org.simple.clinic.util.LocaleOverrideContextWrapper
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.util.wrap
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class DeepLinkActivity : AppCompatActivity(), DeepLinkUiActions {

  @Inject
  lateinit var effectHandler: DeepLinkEffectHandler.Factory

  @Inject
  lateinit var locale: Locale

  private val delegate: MobiusDelegate<DeepLinkModel, DeepLinkEvent, DeepLinkEffect> by unsafeLazy {
    val patientUuid = try {
      UUID.fromString(intent.data?.lastPathSegment)
    } catch (e: IllegalArgumentException) {
      null
    }

    MobiusDelegate.forActivity(
        events = Observable.empty(),
        defaultModel = DeepLinkModel.default(patientUuid),
        update = DeepLinkUpdate(),
        init = DeepLinkInit(),
        effectHandler = effectHandler.create(this).build()
    )
  }

  private lateinit var component: DeepLinkComponent

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
        .deepLinkComponent()
        .activity(this)
        .build()

    component.inject(this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
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
    super.onSaveInstanceState(outState)
    delegate.onSaveInstanceState(outState)
  }

  override fun navigateToSetupActivity() {
    val intent = Intent(this, SetupActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
    }
    startActivity(intent)
    finish()
  }

  override fun navigateToMainActivity() {
    val intent = TheActivity.newIntent(this)
    startActivity(intent)
    finish()
  }

  override fun navigateToPatientSummary(patientUuid: UUID) {
    val intent = TheActivity.intentForOpenPatientSummary(this, patientUuid)
    startActivity(intent)
    finish()
  }

  override fun showPatientDoesNotExist() {
    val intent = TheActivity.intentForShowPatientNotFoundError(this)
    startActivity(intent)
    finish()
  }
}
