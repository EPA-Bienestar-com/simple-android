package org.simple.clinic.facility.alertchange

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import com.f2prateek.rx.preferences2.Preference
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import kotlinx.android.synthetic.main.sheet_alert_facility_change.*
import org.simple.clinic.ClinicApp
import org.simple.clinic.R
import org.simple.clinic.facility.change.FacilityChangeActivity
import org.simple.clinic.router.screen.FullScreenKey
import org.simple.clinic.util.LocaleOverrideContextWrapper
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.util.wrap
import org.simple.clinic.widgets.BottomSheetActivity
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

class AlertFacilityChangeSheet : BottomSheetActivity() {

  @Inject
  lateinit var locale: Locale

  @field:[Inject Named("is_facility_switched")]
  lateinit var isFacilitySwitchedPreference: Preference<Boolean>

  private lateinit var component: AlertFacilityChangeComponent

  companion object {
    const val FACILITY_CHANGE = 101
    private const val CURRENT_FACILITY_NAME = "current_facility"
    private const val CONTINUE_TO_SCREEN = "continue_to_screen"

    private const val EXTRA_CONTINUE_SCREEN = "extra_continue_screen"

    fun intentForScreen(
        context: Context,
        currentFacilityName: String,
        continueToScreen: FullScreenKey
    ): Intent {
      val intent = Intent(context, AlertFacilityChangeSheet::class.java)
      intent.putExtra(CURRENT_FACILITY_NAME, currentFacilityName)
      intent.putExtra(CONTINUE_TO_SCREEN, continueToScreen)
      return intent
    }

    fun <T : Parcelable> readContinuationExtra(intent: Intent): T {
      return intent.getParcelableExtra(EXTRA_CONTINUE_SCREEN) as T
    }
  }

  private val currentFacilityName by unsafeLazy {
    intent.getStringExtra(CURRENT_FACILITY_NAME)!!
  }

  private val continueToScreen by unsafeLazy {
    intent.getParcelableExtra(CONTINUE_TO_SCREEN) as FullScreenKey
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (isFacilitySwitchedPreference.get().not()) {
      closeSheetWithContinuation()
    } else {
      setContentView(R.layout.sheet_alert_facility_change)

      facilityName.text = getString(R.string.alertfacilitychange_facility_name, currentFacilityName)
      yesButton.setOnClickListener {
        closeSheetWithResult(Activity.RESULT_OK)
      }

      changeButton.setOnClickListener {
        openFacilityChangeScreen()
      }
    }
  }

  override fun attachBaseContext(baseContext: Context) {
    setupDI()

    val wrappedContext = baseContext
        .wrap { LocaleOverrideContextWrapper.wrap(it, locale) }
        .wrap { ViewPumpContextWrapper.wrap(it) }

    super.attachBaseContext(wrappedContext)
  }

  private fun setupDI() {
    component = ClinicApp.appComponent
        .alertFacilityChangeComponent()
        .activity(this)
        .build()
    component.inject(this)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == FACILITY_CHANGE) {
      closeSheetWithResult(resultCode)
    }
  }

  private fun closeSheetWithResult(resultCode: Int) {
    if (resultCode == Activity.RESULT_OK) {
      isFacilitySwitchedPreference.set(false)
      closeSheetWithContinuation()
    } else {
      val intent = Intent()
      setResult(resultCode, intent)
      finish()
    }
  }

  private fun closeSheetWithContinuation() {
    val intent = Intent()
    intent.putExtra(EXTRA_CONTINUE_SCREEN, continueToScreen)
    setResult(Activity.RESULT_OK, intent)
    finish()
  }

  private fun openFacilityChangeScreen() {
    startActivityForResult(FacilityChangeActivity.intent(this), FACILITY_CHANGE)
  }
}
