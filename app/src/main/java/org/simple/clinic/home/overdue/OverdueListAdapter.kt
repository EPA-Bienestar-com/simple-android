package org.simple.clinic.home.overdue

import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import io.reactivex.subjects.PublishSubject
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.patient.Gender
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.locationRectOnScreen
import org.simple.clinic.widgets.marginLayoutParams
import org.simple.clinic.widgets.setCompoundDrawableStart
import java.util.UUID
import javax.inject.Inject

class OverdueListAdapter @Inject constructor() : ListAdapter<OverdueListItem, OverdueListViewHolder>(OverdueListDiffer()) {

  private lateinit var recyclerView: RecyclerView

  val itemClicks = PublishSubject.create<UiEvent>()!!

  override fun onAttachedToRecyclerView(rv: RecyclerView) {
    super.onAttachedToRecyclerView(rv)
    recyclerView = rv
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OverdueListViewHolder {
    val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_overdue_list, parent, false)
    val holder = OverdueListViewHolder(layout, itemClicks)

    layout.setOnClickListener {
      holder.toggleBottomLayoutVisibility()
      holder.togglePhoneNumberViewVisibility()

      holder.itemView.post {
        val itemLocation = holder.itemView.locationRectOnScreen()
        val itemBottomWithMargin = itemLocation.bottom + holder.itemView.marginLayoutParams.bottomMargin

        val rvLocation = recyclerView.locationRectOnScreen()
        val differenceInBottoms = itemBottomWithMargin - rvLocation.bottom

        if (differenceInBottoms > 0) {
          (holder.itemView.parent as RecyclerView).smoothScrollBy(0, differenceInBottoms)
        }
      }
    }

    return holder
  }

  override fun onBindViewHolder(holder: OverdueListViewHolder, position: Int) {
    holder.appointment = getItem(position)
    holder.render()
  }
}

data class OverdueListItem(
    val appointmentUuid: UUID,
    val patientUuid: UUID,
    val name: String,
    val gender: Gender,
    val age: Int,
    val phoneNumber: String? = null,
    val bpSystolic: Int,
    val bpDiastolic: Int,
    val bpDaysAgo: Int,
    val overdueDays: Int,
    val isAtHighRisk: Boolean
)

class OverdueListViewHolder(
    itemView: View,
    private val eventStream: PublishSubject<UiEvent>
) : RecyclerView.ViewHolder(itemView) {

  private val patientNameTextView by bindView<TextView>(R.id.overdue_patient_name_age)
  private val patientBPTextView by bindView<TextView>(R.id.overdue_patient_bp)
  private val overdueDaysTextView by bindView<TextView>(R.id.overdue_days)
  private val isAtHighRiskTextView by bindView<TextView>(R.id.overdue_high_risk_label)
  private val callButton by bindView<ImageButton>(R.id.overdue_patient_call)
  private val actionsContainer by bindView<LinearLayout>(R.id.overdue_actions_container)
  private val phoneNumberTextView by bindView<TextView>(R.id.overdue_patient_phone_number)
  private val agreedToVisitTextView by bindView<TextView>(R.id.overdue_agreed_to_visit)
  private val remindLaterTextView by bindView<TextView>(R.id.overdue_reminder_later)
  private val removeFromListTextView by bindView<TextView>(R.id.overdue_remove_from_list)

  lateinit var appointment: OverdueListItem

  init {
    callButton.setOnClickListener {
      eventStream.onNext(CallPatientClicked(appointment.phoneNumber!!))
    }
    agreedToVisitTextView.setOnClickListener {
      eventStream.onNext(AgreedToVisitClicked(appointment.appointmentUuid))
    }
    remindLaterTextView.setOnClickListener {
      eventStream.onNext(RemindToCallLaterClicked(appointment.appointmentUuid))
    }
    removeFromListTextView.setOnClickListener {
      eventStream.onNext(RemoveFromListClicked(appointment.appointmentUuid, appointment.patientUuid))
    }
  }

  fun toggleBottomLayoutVisibility() {
    val isVisible = actionsContainer.visibility == VISIBLE
    actionsContainer.visibility =
        if (isVisible) {
          GONE
        } else {
          eventStream.onNext(AppointmentExpanded(appointment.patientUuid))
          VISIBLE
        }
  }

  fun togglePhoneNumberViewVisibility() {
    val isVisible = phoneNumberTextView.visibility == VISIBLE
    if (!isVisible && appointment.phoneNumber != null) {
      phoneNumberTextView.visibility = VISIBLE
    } else {
      phoneNumberTextView.visibility = GONE
    }
  }

  fun render() {
    val context = itemView.context

    patientNameTextView.text = context.getString(R.string.overdue_list_item_name_age, appointment.name, appointment.age)
    patientNameTextView.setCompoundDrawableStart(appointment.gender.displayIconRes)

    patientBPTextView.text = context.resources.getQuantityString(
        R.plurals.overdue_list_item_patient_bp,
        appointment.bpDaysAgo,
        appointment.bpSystolic,
        appointment.bpDiastolic,
        appointment.bpDaysAgo
    )

    callButton.visibility = if (appointment.phoneNumber == null) GONE else VISIBLE
    phoneNumberTextView.text = appointment.phoneNumber

    isAtHighRiskTextView.visibility = if (appointment.isAtHighRisk) VISIBLE else GONE

    overdueDaysTextView.text = context.resources.getQuantityString(
        R.plurals.overdue_list_item_overdue_days,
        appointment.overdueDays,
        appointment.overdueDays
    )
  }
}

class OverdueListDiffer : DiffUtil.ItemCallback<OverdueListItem>() {

  override fun areItemsTheSame(oldItem: OverdueListItem, newItem: OverdueListItem): Boolean = oldItem.appointmentUuid == newItem.appointmentUuid

  override fun areContentsTheSame(oldItem: OverdueListItem, newItem: OverdueListItem): Boolean = oldItem == newItem
}
