package org.simple.clinic.contactpatient.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.android.synthetic.main.contactpatient_appointmentreminder.view.*
import org.simple.clinic.R
import org.simple.clinic.di.injector
import org.simple.clinic.overdue.TimeToAppointment
import org.simple.clinic.overdue.TimeToAppointment.Days
import org.simple.clinic.overdue.TimeToAppointment.Months
import org.simple.clinic.overdue.TimeToAppointment.Weeks
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named

private typealias DecrementStepperClicked = () -> Unit
private typealias IncrementStepperClicked = () -> Unit
private typealias AppointmentDateClicked = () -> Unit
private typealias SetAppointmentReminderDoneClicked = () -> Unit

class SetAppointmentReminderView(
    context: Context,
    attributeSet: AttributeSet
) : ConstraintLayout(context, attributeSet) {

  @Inject
  @Named("date_for_user_input")
  lateinit var dateFormatter: DateTimeFormatter

  var decrementStepperClicked: DecrementStepperClicked? = null

  var incrementStepperClicked: IncrementStepperClicked? = null

  var appointmentDateClicked: AppointmentDateClicked? = null

  var doneClicked: SetAppointmentReminderDoneClicked? = null

  override fun onFinishInflate() {
    super.onFinishInflate()
    context.injector<Injector>().inject(this)

    View.inflate(context, R.layout.contactpatient_appointmentreminder, this)

    previousDateStepper.setOnClickListener { decrementStepperClicked?.invoke() }
    nextDateStepper.setOnClickListener { incrementStepperClicked?.invoke() }
    saveReminder.setOnClickListener { doneClicked?.invoke() }
    actualAppointmentDateButton.setOnClickListener { appointmentDateClicked?.invoke() }
  }

  fun renderSelectedAppointmentDate(
      selectedAppointmentReminderPeriod: TimeToAppointment,
      selectedDate: LocalDate
  ) {
    selectedAppointmentDate.text = displayTextForReminderPeriod(selectedAppointmentReminderPeriod)
    actualAppointmentDateButton.text = dateFormatter.format(selectedDate)
  }

  private fun displayTextForReminderPeriod(timeToAppointment: TimeToAppointment): CharSequence {
    val quantityStringResourceId = when (timeToAppointment) {
      is Days -> R.plurals.contactpatient_days
      is Weeks -> R.plurals.contactpatient_weeks
      is Months -> R.plurals.contactpatient_months
    }

    return resources.getQuantityString(quantityStringResourceId, timeToAppointment.value, "${timeToAppointment.value}")
  }

  fun disablePreviousReminderDateStepper() {
    previousDateStepper.isEnabled = false
  }

  fun enablePreviousReminderDateStepper() {
    previousDateStepper.isEnabled = true
  }

  fun disableNextReminderDateStepper() {
    nextDateStepper.isEnabled = false
  }

  fun enableNextReminderDateStepper() {
    nextDateStepper.isEnabled = true
  }

  interface Injector {
    fun inject(target: SetAppointmentReminderView)
  }
}
