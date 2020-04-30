package org.simple.clinic.contactpatient

import org.simple.clinic.overdue.TimeToAppointment
import org.simple.clinic.patient.Gender
import org.threeten.bp.LocalDate

interface ContactPatientUi {
  fun switchToCallPatientView()
  fun switchToSetAppointmentReminderView()
  fun switchToRemoveAppointmentView()

  fun renderPatientDetails(name: String, gender: Gender, age: Int, phoneNumber: String)
  fun showCallResultSection()
  fun hideCallResultSection()
  fun showSecureCallUi()
  fun hideSecureCallUi()

  fun renderSelectedAppointmentDate(
      potentialAppointmentReminderPeriods: List<TimeToAppointment>,
      selectedAppointmentReminderPeriod: TimeToAppointment,
      selectedDate: LocalDate
  )
  fun disablePreviousReminderDateStepper()
  fun enablePreviousReminderDateStepper()
  fun disableNextReminderDateStepper()
  fun enableNextReminderDateStepper()

  fun renderAppointmentRemoveReasons(reasons: List<RemoveAppointmentReason>, selectedReason: RemoveAppointmentReason?)
  fun enableRemoveAppointmentDoneButton()
  fun disableRemoveAppointmentDoneButton()
}
