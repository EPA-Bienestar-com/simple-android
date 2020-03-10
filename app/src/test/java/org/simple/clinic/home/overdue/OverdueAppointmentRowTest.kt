package org.simple.clinic.home.overdue

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.patient.Age
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.util.TestUserClock
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class OverdueAppointmentRowTest {

  private val userClock = TestUserClock(LocalDate.parse("2019-01-05"))
  private val dateFormatter = DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH)

  @Test
  fun `overdue appointments must be converted to list items`() {
    // given
    val oneYear = Duration.ofDays(365)

    val appointmentDelayedBy4Days = PatientMocker
        .overdueAppointment(
            name = "Anish Acharya",
            isHighRisk = false,
            gender = Gender.Male,
            dateOfBirth = LocalDate.parse("1985-01-01"),
            age = null,
            phoneNumber = PatientMocker.phoneNumber(number = "123456"),
            appointment = PatientMocker.appointment(
                uuid = UUID.fromString("65d790f3-a9ea-4a83-bce1-8d1ea8539c67"),
                patientUuid = UUID.fromString("c88a4835-40e5-476b-9a6f-2f850c48ecdb"),
                scheduledDate = LocalDate.parse("2019-01-01")
            ),
            patientLastSeen = Instant.parse("2020-01-01T00:00:00Z")
        )
    val appointmentDelayedByOneWeek = PatientMocker
        .overdueAppointment(
            name = "Deepa",
            isHighRisk = true,
            gender = Gender.Female,
            dateOfBirth = null,
            age = Age(45, Instant.now(userClock).minus(oneYear)),
            phoneNumber = PatientMocker.phoneNumber(number = "45678912"),
            appointment = PatientMocker.appointment(
                uuid = UUID.fromString("4f13f6d3-05dc-4248-891b-b5ebd6f56987"),
                patientUuid = UUID.fromString("0c35a015-d823-4cc5-be77-21ce026c5780"),
                scheduledDate = LocalDate.parse("2018-12-29")
            ),
            patientLastSeen = Instant.parse("2019-12-25T00:00:00Z")
        )

    val appointments = listOf(appointmentDelayedBy4Days, appointmentDelayedByOneWeek)

    // when
    val overdueListItems = OverdueAppointmentRow.from(appointments, userClock, dateFormatter)

    // then
    val expectedListItems = listOf(
        OverdueAppointmentRow(
            appointmentUuid = UUID.fromString("65d790f3-a9ea-4a83-bce1-8d1ea8539c67"),
            patientUuid = UUID.fromString("c88a4835-40e5-476b-9a6f-2f850c48ecdb"),
            name = "Anish Acharya",
            gender = Gender.Male,
            age = 34,
            phoneNumber = "123456",
            overdueDays = 4,
            isAtHighRisk = false,
            lastSeenDate = "1-Jan-2020"
        ),
        OverdueAppointmentRow(
            appointmentUuid = UUID.fromString("4f13f6d3-05dc-4248-891b-b5ebd6f56987"),
            patientUuid = UUID.fromString("0c35a015-d823-4cc5-be77-21ce026c5780"),
            name = "Deepa",
            gender = Gender.Female,
            age = 46,
            phoneNumber = "45678912",
            overdueDays = 7,
            isAtHighRisk = true,
            lastSeenDate = "25-Dec-2019"
        )
    )
    assertThat(overdueListItems)
        .containsExactlyElementsIn(expectedListItems)
        .inOrder()
  }
}
