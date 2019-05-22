package org.simple.clinic.home.overdue

import org.simple.clinic.widgets.UiEvent
import java.util.UUID

class OverdueScreenCreated : UiEvent

data class CallPatientClicked(val patientUuid: UUID) : UiEvent {
  override val analyticsName = "Overdue Screen:Call Patient clicked"
}

data class AgreedToVisitClicked(val appointmentUuid: UUID) : UiEvent {
  override val analyticsName = "Overdue Screen:Mark patient as 'agreed to visit' clicked"
}

data class RemindToCallLaterClicked(val appointmentUuid: UUID) : UiEvent {
  override val analyticsName = "Overdue Screen:Remind To Call Later clicked"
}

data class RemoveFromListClicked(val appointmentUuid: UUID, val patientUuid: UUID) : UiEvent {
  override val analyticsName = "Overdue Screen:Remove From List clicked"
}

data class AppointmentExpanded(val patientUuid: UUID) : UiEvent
