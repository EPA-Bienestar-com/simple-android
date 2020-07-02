package org.simple.clinic.drugs.selection.entry.confirmremovedialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.FragmentManager
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.schedulers.Schedulers.io
import io.reactivex.subjects.PublishSubject
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.bp.entry.confirmremovebloodpressure.RemovePrescriptionClicked
import org.simple.clinic.di.injector
import org.simple.clinic.drugs.PrescriptionRepository
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import java.util.UUID
import javax.inject.Inject

class ConfirmRemovePrescriptionDialog : AppCompatDialogFragment(), UiActions {

  @Inject
  lateinit var prescriptionRepository: PrescriptionRepository

  @Inject
  lateinit var controller: ConfirmRemovePrescriptionDialogController.Factory

  private val onStarts = PublishSubject.create<Any>()
  private val screenDestroys = PublishSubject.create<ScreenDestroyed>()

  private val prescriptionUuidToDelete by unsafeLazy {
    requireArguments().getSerializable(KEY_PRESCRIPTION_UUID) as UUID
  }

  private val events by unsafeLazy {
    Observable
        .merge(
            screenDestroys,
            removeClicks()
        )
        .compose(ReportAnalyticsEvents())
        .share()
  }

  @SuppressLint("CheckResult")
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = AlertDialog.Builder(requireContext(), R.style.Clinic_V2_DialogStyle_Destructive)
        .setTitle(R.string.customprescription_delete_title)
        .setMessage(R.string.customprescription_delete_message)
        .setPositiveButton(R.string.customprescription_delete_confirm, null)
        .setNegativeButton(R.string.customprescription_delete_cancel, null)
        .create()

    onStarts
        .take(1)
        .flatMap { setupDialog() }
        .takeUntil(screenDestroys)
        .subscribe { uiChange -> uiChange(this) }

    return dialog
  }

  private fun setupDialog(): Observable<UiChange> {
    return events
        .observeOn(io())
        .compose(controller.create(prescriptionUuidToDelete))
        .observeOn(mainThread())
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    context.injector<Injector>().inject(this)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    screenDestroys.onNext(ScreenDestroyed())
  }

  override fun onStart() {
    super.onStart()
    onStarts.onNext(Any())
  }

  override fun closeDialog() {
    dismiss()
  }

  private fun removeClicks(): Observable<UiEvent> {
    val button = (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE)
    return RxView.clicks(button).map { RemovePrescriptionClicked }
  }

  companion object {
    private const val TAG = "ConfirmRemovePrescriptionDialog"
    private const val KEY_PRESCRIPTION_UUID = "prescriptionUuid"

    fun showForPrescription(prescriptionUuid: UUID, fragmentManager: FragmentManager) {
      val existingFragment = fragmentManager.findFragmentByTag(TAG)

      if (existingFragment != null) {
        fragmentManager
            .beginTransaction()
            .remove(existingFragment)
            .commitNowAllowingStateLoss()
      }

      val fragment = ConfirmRemovePrescriptionDialog()
      val args = Bundle(1)
      args.putSerializable(KEY_PRESCRIPTION_UUID, prescriptionUuid)
      fragment.arguments = args

      fragmentManager
          .beginTransaction()
          .add(fragment, TAG)
          .commitNowAllowingStateLoss()
    }
  }

  interface Injector {
    fun inject(target: ConfirmRemovePrescriptionDialog)
  }
}
