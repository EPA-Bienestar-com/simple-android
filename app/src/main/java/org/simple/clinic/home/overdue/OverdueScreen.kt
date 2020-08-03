package org.simple.clinic.home.overdue

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import io.reactivex.rxkotlin.ofType
import kotlinx.android.synthetic.main.screen_overdue.view.*
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.contactpatient.ContactPatientBottomSheet
import org.simple.clinic.di.injector
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.ItemAdapter
import org.simple.clinic.widgets.visibleOrGone
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

class OverdueScreen(
    context: Context,
    attrs: AttributeSet
) : RelativeLayout(context, attrs), OverdueUi, OverdueUiActions {

  @Inject
  lateinit var activity: AppCompatActivity

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var userClock: UserClock

  @field:[Inject Named("full_date")]
  lateinit var dateFormatter: DateTimeFormatter

  @Inject
  lateinit var effectHandlerFactory: OverdueEffectHandler.Factory

  private val overdueListAdapter = ItemAdapter(OverdueAppointmentRow.DiffCallback())

  private val events by unsafeLazy {
    overdueListAdapter
        .itemEvents
        .compose(ReportAnalyticsEvents())
        .share()
  }

  private val delegate by unsafeLazy {
    val uiRenderer = OverdueUiRenderer(this)

    val date = LocalDate.now(userClock)

    MobiusDelegate.forView(
        events = events.ofType(),
        defaultModel = OverdueModel.create(),
        update = OverdueUpdate(date),
        effectHandler = effectHandlerFactory.create(this).build(),
        init = OverdueInit(date),
        modelUpdateListener = uiRenderer::render
    )
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }

    context.injector<Injector>().inject(this)

    overdueRecyclerView.adapter = overdueListAdapter
    overdueRecyclerView.layoutManager = LinearLayoutManager(context)
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

  override fun updateList(overdueAppointments: List<OverdueAppointment>, isDiabetesManagementEnabled: Boolean) {
    val areOverdueAppointmentsAvailable = overdueAppointments.isNotEmpty()
    viewForEmptyList.visibleOrGone(isVisible = !areOverdueAppointmentsAvailable)
    overdueRecyclerView.visibleOrGone(isVisible = areOverdueAppointmentsAvailable)

    overdueListAdapter.submitList(OverdueAppointmentRow.from(
        appointments = overdueAppointments,
        clock = userClock,
        dateFormatter = dateFormatter,
        isDiabetesManagementEnabled = isDiabetesManagementEnabled
    ))
  }

  override fun openPhoneMaskBottomSheet(patientUuid: UUID) {
    activity.startActivity(ContactPatientBottomSheet.intent(context, patientUuid))
  }

  interface Injector {
    fun inject(target: OverdueScreen)
  }
}
