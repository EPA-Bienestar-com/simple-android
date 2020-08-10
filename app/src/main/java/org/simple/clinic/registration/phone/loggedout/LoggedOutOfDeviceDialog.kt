package org.simple.clinic.registration.phone.loggedout

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.FragmentManager
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.bindUiToController
import org.simple.clinic.di.injector
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

class LoggedOutOfDeviceDialog : AppCompatDialogFragment(), LoggedOutOfDeviceDialogUi {

  companion object {
    private const val FRAGMENT_TAG = "LoggedOutOfDeviceDialog"

    fun show(fragmentManager: FragmentManager) {
      val existingFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG)

      if (existingFragment != null) {
        fragmentManager
            .beginTransaction()
            .remove(existingFragment)
            .commit()
      }

      val fragment = LoggedOutOfDeviceDialog().apply {
        isCancelable = false
      }

      fragmentManager
          .beginTransaction()
          .add(fragment, FRAGMENT_TAG)
          .commit()
    }
  }

  @Inject
  lateinit var controller: LoggedOutOfDeviceDialogController

  @Inject
  lateinit var effectHandler: LoggedOutOfDeviceEffectHandler

  private val okayButton: Button by unsafeLazy {
    (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE)
  }

  private val screenDestroys = PublishSubject.create<ScreenDestroyed>()
  private val onStarts = PublishSubject.create<Any>()

  private val events by unsafeLazy {
    screenCreates()
        .compose(ReportAnalyticsEvents())
        .share()
  }

  private val delegate by unsafeLazy {
    val uiRenderer = LoggedOutOfDeviceUiRenderer(this)

    MobiusDelegate.forActivity(
        events = events.ofType(),
        defaultModel = LoggedOutOfDeviceModel.create(),
        init = LoggedOutOfDeviceInit(),
        update = LoggedOutOfDeviceUpdate(),
        effectHandler = effectHandler.build(),
        modelUpdateListener = uiRenderer::render
    )
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    context.injector<Injector>().inject(this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    delegate.onRestoreInstanceState(savedInstanceState)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    delegate.onSaveInstanceState(outState)
    super.onSaveInstanceState(outState)
  }

  @SuppressLint("CheckResult")
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = AlertDialog.Builder(requireContext())
        .setTitle(R.string.registration_loggedout_dialog_title)
        .setMessage(R.string.registration_loggedout_dialog_message)
        .setPositiveButton(R.string.registration_loggedout_dialog_confirm, null)
        .create()

    onStarts
        .take(1)
        .subscribe { setupDialog() }

    return dialog
  }

  private fun setupDialog() {
    bindUiToController(
        ui = this,
        events = events,
        controller = controller,
        screenDestroys = screenDestroys
    )
  }

  private fun screenCreates(): Observable<UiEvent> = Observable.just(ScreenCreated())

  override fun onStart() {
    super.onStart()
    onStarts.onNext(Any())
    delegate.start()
  }

  override fun onStop() {
    delegate.stop()
    super.onStop()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    screenDestroys.onNext(ScreenDestroyed())
  }

  override fun enableOkayButton() {
    okayButton.isEnabled = true
  }

  override fun disableOkayButton() {
    okayButton.isEnabled = false
  }

  interface Injector {
    fun inject(target: LoggedOutOfDeviceDialog)
  }
}
