package org.simple.clinic.home.overdue

import android.annotation.SuppressLint
import android.content.Context
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.recyclerview.widget.DiffUtil
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.item_overdue_list_patient.*
import org.simple.clinic.R
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.patient.DateOfBirth
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.displayIconRes
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.toLocalDateAtZone
import org.simple.clinic.widgets.ItemAdapter
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.locationRectOnScreen
import org.simple.clinic.widgets.marginLayoutParams
import org.simple.clinic.widgets.recyclerview.ViewHolderX
import org.simple.clinic.widgets.setCompoundDrawableStart
import org.simple.clinic.widgets.visibleOrGone
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.util.UUID

data class OverdueAppointmentRow(
    val appointmentUuid: UUID,
    val patientUuid: UUID,
    val name: String,
    val gender: Gender,
    val age: Int,
    val phoneNumber: String? = null,
    val overdueDays: Int,
    val isAtHighRisk: Boolean,
    val lastSeenDate: String,
    val diagnosedWithDiabetes: Answer?,
    val diagnosedWithHypertension: Answer?
) : ItemAdapter.Item<UiEvent> {

  companion object {

    fun from(
        appointments: List<OverdueAppointment>,
        clock: UserClock,
        dateFormatter: DateTimeFormatter
    ): List<OverdueAppointmentRow> {
      return appointments.map { overdueAppointment -> from(overdueAppointment, clock, dateFormatter) }
    }

    private fun from(overdueAppointment: OverdueAppointment, clock: UserClock, dateFormatter: DateTimeFormatter): OverdueAppointmentRow {
      return OverdueAppointmentRow(
          appointmentUuid = overdueAppointment.appointment.uuid,
          patientUuid = overdueAppointment.appointment.patientUuid,
          name = overdueAppointment.fullName,
          gender = overdueAppointment.gender,
          age = DateOfBirth.fromOverdueAppointment(overdueAppointment, clock).estimateAge(clock),
          phoneNumber = overdueAppointment.phoneNumber?.number,
          overdueDays = daysBetweenNowAndDate(overdueAppointment.appointment.scheduledDate, clock),
          isAtHighRisk = overdueAppointment.isAtHighRisk,
          lastSeenDate = dateFormatter.format(overdueAppointment.patientLastSeen.toLocalDateAtZone(clock.zone)),
          diagnosedWithDiabetes = overdueAppointment.diagnosedWithDiabetes,
          diagnosedWithHypertension = overdueAppointment.diagnosedWithHypertension
      )
    }

    private fun daysBetweenNowAndDate(
        date: LocalDate,
        clock: UserClock
    ): Int {
      return ChronoUnit.DAYS.between(date, LocalDate.now(clock)).toInt()
    }
  }

  private var cardExpanded = false

  override fun layoutResId(): Int = R.layout.item_overdue_list_patient

  override fun render(holder: ViewHolderX, subject: Subject<UiEvent>) {
    setupEvents(holder, subject)
    bindUi(holder)
  }

  private fun setupEvents(
      holder: ViewHolderX,
      eventSubject: Subject<UiEvent>
  ) {
    val containerView = holder.containerView
    containerView.setOnClickListener {
      cardExpanded = cardExpanded.not()
      if (cardExpanded) {
        eventSubject.onNext(AppointmentExpanded(patientUuid))
      }
      updateBottomLayoutVisibility(holder)
      updatePhoneNumberViewVisibility(holder)

      containerView.post {
        val itemLocation = containerView.locationRectOnScreen()
        val itemBottomWithMargin = itemLocation.bottom + containerView.marginLayoutParams.bottomMargin
        eventSubject.onNext(CardExpansionToggled(itemBottomWithMargin))
      }
    }
    holder.callButton.setOnClickListener {
      eventSubject.onNext(CallPatientClicked(patientUuid))
    }
    holder.agreedToVisitTextView.setOnClickListener {
      eventSubject.onNext(AgreedToVisitClicked(appointmentUuid))
    }
    holder.remindLaterTextView.setOnClickListener {
      eventSubject.onNext(RemindToCallLaterClicked(appointmentUuid))
    }
    holder.removeFromListTextView.setOnClickListener {
      eventSubject.onNext(RemoveFromListClicked(appointmentUuid, patientUuid))
    }
  }

  @SuppressLint("SetTextI18n")
  private fun bindUi(holder: ViewHolderX) {
    val containerView = holder.containerView
    val context = containerView.context

    holder.patientNameTextView.text = context.getString(R.string.overdue_list_item_name_age, name, age.toString())
    holder.patientNameTextView.setCompoundDrawableStart(gender.displayIconRes)

    holder.patientLastSeenTextView.text = lastSeenDate

    holder.callButton.visibility = if (phoneNumber == null) GONE else VISIBLE
    holder.phoneNumberTextView.text = phoneNumber

    holder.isAtHighRiskTextView.visibility = if (isAtHighRisk) VISIBLE else GONE

    holder.overdueDaysTextView.text = context.resources.getQuantityString(
        R.plurals.overdue_list_item_appointment_overdue_days,
        overdueDays,
        "$overdueDays"
    )

    holder.diagnosisTextView.text = diagnosisText(context)

    updateBottomLayoutVisibility(holder)
    updatePhoneNumberViewVisibility(holder)
  }

  private fun diagnosisText(context: Context): CharSequence {
    return listOf(
        diagnosedWithDiabetes to R.string.overdue_list_item_diagnosis_diabetes,
        diagnosedWithHypertension to R.string.overdue_list_item_diagnosis_hypertension
    )
        .filter { (answer, _) -> answer is Answer.Yes }
        .map { (_, diagnosisTitle) -> diagnosisTitle }
        .ifEmpty { listOf(R.string.overdue_list_item_diagnosis_none) }
        .joinToString { context.getString(it) }
  }

  private fun updateBottomLayoutVisibility(holder: ViewHolderX) {
    holder.actionsContainer.visibleOrGone(cardExpanded)
  }

  private fun updatePhoneNumberViewVisibility(holder: ViewHolderX) {
    val shouldShowPhoneNumberView = cardExpanded && phoneNumber != null
    holder.phoneNumberTextView.visibleOrGone(shouldShowPhoneNumberView)
  }

  data class CardExpansionToggled(val cardBottomWithMargin: Int) : UiEvent

  class DiffCallback : DiffUtil.ItemCallback<OverdueAppointmentRow>() {
    override fun areItemsTheSame(oldItem: OverdueAppointmentRow, newItem: OverdueAppointmentRow): Boolean {
      return oldItem.patientUuid == newItem.patientUuid
    }

    override fun areContentsTheSame(oldItem: OverdueAppointmentRow, newItem: OverdueAppointmentRow): Boolean {
      return oldItem == newItem
    }
  }
}
