package org.simple.clinic.editpatient.deletepatient

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import io.reactivex.Observable
import io.reactivex.rxkotlin.cast
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.screen_delete_patient.view.*
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.di.injector
import org.simple.clinic.home.HomeScreenKey
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.patient.DeletedReason
import org.simple.clinic.router.screen.RouterDirection
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.DividerItemDecorator
import org.simple.clinic.widgets.ItemAdapter
import org.simple.clinic.widgets.dp
import javax.inject.Inject

class DeletePatientScreen(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs), UiActions, DeletePatientUi {

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var effectHandlerFactory: DeletePatientEffectHandler.Factory

  private val viewRenderer = DeletePatientViewRenderer(this)

  private val screenKey by unsafeLazy {
    screenRouter.key<DeletePatientScreenKey>(this)
  }

  private val deleteReasonsAdapter = ItemAdapter(DeleteReasonItem.DiffCallback())
  private val dialogEvents = PublishSubject.create<DeletePatientEvent>()
  private val events: Observable<DeletePatientEvent> by unsafeLazy {
    Observable
        .mergeArray(
            dialogEvents,
            adapterEvents()
        )
        .compose(ReportAnalyticsEvents())
        .cast<DeletePatientEvent>()
  }

  private val delegate by unsafeLazy {
    MobiusDelegate.forView(
        events = events,
        defaultModel = DeletePatientModel.default(screenKey.patientUuid),
        init = DeletePatientInit(),
        update = DeletePatientUpdate(),
        effectHandler = effectHandlerFactory.create(this).build(),
        modelUpdateListener = viewRenderer::render
    )
  }

  @SuppressLint("CheckResult")
  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) return

    context.injector<DeletePatientScreenInjector>().inject(this)

    toolbar.setNavigationOnClickListener { screenRouter.pop() }
    with(deleteReasonsRecyclerView) {
      adapter = deleteReasonsAdapter
      addItemDecoration(DividerItemDecorator(context, marginStart = 56.dp, marginEnd = 16.dp))
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    delegate.start()
  }

  override fun onDetachedFromWindow() {
    delegate.stop()
    super.onDetachedFromWindow()
  }

  override fun onSaveInstanceState(): Parcelable? {
    return delegate.onSaveInstanceState(super.onSaveInstanceState())
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    val viewState = delegate.onRestoreInstanceState(state)
    super.onRestoreInstanceState(viewState)
  }

  override fun showDeleteReasons(patientDeleteReasons: List<PatientDeleteReason>, selectedReason: PatientDeleteReason?) {
    deleteReasonsAdapter.submitList(DeleteReasonItem.from(patientDeleteReasons, selectedReason))
  }

  override fun showConfirmDeleteDialog(patientName: String, deletedReason: DeletedReason) {
    val message = context.getString(R.string.deletereason_confirm_message, patientName)

    AlertDialog.Builder(context, R.style.Clinic_V2_DialogStyle_Destructive)
        .setTitle(R.string.deletereason_confirm_title)
        .setMessage(message)
        .setPositiveButton(R.string.deletereason_confirm_positive) { _, _ ->
          dialogEvents.onNext(ConfirmPatientDeleteClicked(deletedReason))
        }
        .setNegativeButton(R.string.deletereason_confirm_negative, null)
        .show()
  }

  override fun showConfirmDiedDialog(patientName: String) {
    val message = context.getString(R.string.deletereason_confirm_message, patientName)

    AlertDialog.Builder(context, R.style.Clinic_V2_DialogStyle_Destructive)
        .setTitle(R.string.deletereason_confirm_title)
        .setMessage(message)
        .setPositiveButton(R.string.deletereason_confirm_positive) { _, _ ->
          dialogEvents.onNext(ConfirmPatientDiedClicked)
        }
        .setNegativeButton(R.string.deletereason_confirm_negative, null)
        .show()
  }

  override fun showHomeScreen() {
    screenRouter.clearHistoryAndPush(HomeScreenKey(), RouterDirection.BACKWARD)
  }

  private fun adapterEvents(): Observable<DeletePatientEvent> {
    return deleteReasonsAdapter
        .itemEvents
        .ofType<DeleteReasonItem.Event.Clicked>()
        .map { PatientDeleteReasonClicked(it.reason) }
  }
}
