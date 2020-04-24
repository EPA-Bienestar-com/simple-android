package org.simple.clinic.drugs.selection

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.rxbinding2.view.RxView
import com.mikepenz.itemanimators.SlideUpAlphaAnimator
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.ViewHolder
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.drugs.EditMedicinesEffect
import org.simple.clinic.drugs.EditMedicinesEffectHandler
import org.simple.clinic.drugs.EditMedicinesEvent
import org.simple.clinic.drugs.EditMedicinesInit
import org.simple.clinic.drugs.EditMedicinesModel
import org.simple.clinic.drugs.EditMedicinesUiRenderer
import org.simple.clinic.drugs.EditMedicinesUpdate
import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.drugs.PrescribedDrugsDoneClicked
import org.simple.clinic.drugs.selection.dosage.DosagePickerSheet
import org.simple.clinic.drugs.selection.entry.CustomPrescriptionEntrySheet
import org.simple.clinic.main.TheActivity
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.summary.GroupieItemWithUiEvents
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.PrimarySolidButtonWithFrame
import org.simple.clinic.widgets.UiEvent
import java.util.UUID
import javax.inject.Inject

class EditMedicinesScreen(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs), EditMedicinesUi, EditMedicinesUiActions {

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var effectHandlerFactory: EditMedicinesEffectHandler.Factory

  @Inject
  lateinit var activity: AppCompatActivity

  private val toolbar by bindView<Toolbar>(R.id.prescribeddrugs_toolbar)
  private val recyclerView by bindView<RecyclerView>(R.id.prescribeddrugs_recyclerview)
  private val doneButtonFrame by bindView<PrimarySolidButtonWithFrame>(R.id.prescribeddrugs_done)
  private val groupieAdapter = GroupAdapter<ViewHolder>()

  private val adapterUiEvents = PublishSubject.create<UiEvent>()

  private val patientUuid by unsafeLazy {
    val screenKey = screenRouter.key<PrescribedDrugsScreenKey>(this)
    screenKey.patientUuid
  }

  private val events by unsafeLazy {
    Observable
        .merge(adapterUiEvents, doneClicks())
        .compose(ReportAnalyticsEvents())
  }

  private val uiRenderer = EditMedicinesUiRenderer(this)

  private val delegate: MobiusDelegate<EditMedicinesModel, EditMedicinesEvent, EditMedicinesEffect> by unsafeLazy {
    MobiusDelegate.forView(
        events = events.ofType(),
        defaultModel = EditMedicinesModel.create(patientUuid),
        update = EditMedicinesUpdate(),
        effectHandler = effectHandlerFactory.create(this).build(),
        init = EditMedicinesInit(),
        modelUpdateListener = uiRenderer::render
    )
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    TheActivity.component.inject(this)

    toolbar.setNavigationOnClickListener { screenRouter.pop() }
    recyclerView.layoutManager = LinearLayoutManager(context)
    recyclerView.adapter = groupieAdapter

    val fadeAnimator = DefaultItemAnimator()
    fadeAnimator.supportsChangeAnimations = false
    recyclerView.itemAnimator = fadeAnimator
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
    super.onRestoreInstanceState(delegate.onRestoreInstanceState(state))
  }

  private fun doneClicks() = RxView.clicks(doneButtonFrame.button).map { PrescribedDrugsDoneClicked }

  override fun populateDrugsList(protocolDrugItems: List<GroupieItemWithUiEvents<out ViewHolder>>) {
    // Replace the default fade animator with another animator that
    // plays change animations together instead of sequentially.
    if (groupieAdapter.itemCount != 0) {
      val animator = SlideUpAlphaAnimator().withInterpolator(FastOutSlowInInterpolator())
      animator.supportsChangeAnimations = false
      recyclerView.itemAnimator = animator
    }

    val newAdapterItems = protocolDrugItems + AddNewPrescriptionListItem()

    // Not the best way for registering click listeners,
    // but Groupie doesn't seem to have a better option.
    newAdapterItems.forEach { it.uiEvents = adapterUiEvents }

    val hasNewItems = (groupieAdapter.itemCount == 0).not() && groupieAdapter.itemCount < newAdapterItems.size
    groupieAdapter.update(newAdapterItems)

    // Scroll to end to show newly added prescriptions.
    if (hasNewItems) {
      recyclerView.postDelayed(
          { recyclerView.smoothScrollToPosition(recyclerView.adapter!!.itemCount - 1) },
          300)
    }
  }

  override fun showNewPrescriptionEntrySheet(patientUuid: UUID) {
    activity.startActivity(CustomPrescriptionEntrySheet.intentForAddNewPrescription(context, patientUuid))
  }

  override fun goBackToPatientSummary() {
    screenRouter.pop()
  }

  override fun showDosageSelectionSheet(drugName: String, patientUuid: UUID, prescribedDrugUuid: UUID?) {
    activity.startActivity(DosagePickerSheet.intent(context, drugName, patientUuid, prescribedDrugUuid))
  }

  override fun showUpdateCustomPrescriptionSheet(prescribedDrug: PrescribedDrug) {
    activity.startActivity(CustomPrescriptionEntrySheet.intentForUpdatingPrescription(context, prescribedDrug.uuid))
  }
}
