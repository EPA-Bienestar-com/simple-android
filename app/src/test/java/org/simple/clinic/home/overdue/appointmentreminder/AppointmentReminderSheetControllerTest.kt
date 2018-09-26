package org.simple.clinic.home.overdue.appointmentreminder

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset
import org.threeten.bp.temporal.ChronoUnit
import java.util.UUID

@RunWith(JUnitParamsRunner::class)
class AppointmentReminderSheetControllerTest {

  private val sheet = mock<AppointmentReminderSheet>()
  private val repository = mock<AppointmentRepository>()

  private val uiEvents = PublishSubject.create<UiEvent>()
  private val appointmentUuid = UUID.randomUUID()

  lateinit var controller: AppointmentReminderSheetController

  @Before
  fun setUp() {
    controller = AppointmentReminderSheetController(repository)

    uiEvents.compose(controller).subscribe { uiChange -> uiChange(sheet) }
  }

  @Test
  fun `when sheet is created, a date should immediately be displayed to the user`() {
    val current = 17
    uiEvents.onNext(AppointmentReminderSheetCreated(current, appointmentUuid))

    verify(sheet).updateDisplayedDate(current)
  }

  @Test
  @Parameters(method = "paramsForIncrementingAndDecrementing")
  fun `when increment button is clicked, appointment due date should increase`(
      current: Int,
      size: Int
  ) {
    uiEvents.onNext(ReminderDateIncremented(current, size))

    if (current == 0) {
      verify(sheet).updateDisplayedDate(current + 1)
      verify(sheet).enableDecrementButton(true)
    }

    if (current != size - 2) {
      verify(sheet).updateDisplayedDate(current + 1)
      verify(sheet).enableIncrementButton(true)
    }

    if (current == size - 2) {
      verify(sheet).updateDisplayedDate(current + 1)
      verify(sheet).enableIncrementButton(false)
    }

    if (current == size - 1) {
      verify(sheet, never()).updateDisplayedDate(any())
      verify(sheet, never()).enableIncrementButton(any())
      verify(sheet, never()).enableDecrementButton(any())
    }
  }

  @Test
  @Parameters(method = "paramsForIncrementingAndDecrementing")
  fun `when decrement button is clicked, appointment due date should decrease`(
      current: Int,
      size: Int
  ) {
    uiEvents.onNext(ReminderDateDecremented(current, size))

    if (current == 0) {
      verify(sheet, never()).updateDisplayedDate(any())
      verify(sheet, never()).enableIncrementButton(any())
      verify(sheet, never()).enableDecrementButton(any())
    }

    if (current == 1) {
      verify(sheet).updateDisplayedDate(current - 1)
      verify(sheet).enableDecrementButton(false)
    }

    if (current != 0) {
      verify(sheet).updateDisplayedDate(current - 1)
    }

    if (current == size - 1) {
      verify(sheet).updateDisplayedDate(current - 1)
      verify(sheet).enableIncrementButton(true)
    }
  }

  fun paramsForIncrementingAndDecrementing() = arrayOf(
      arrayOf(3, 9),
      arrayOf(1, 9),
      arrayOf(7, 9),
      arrayOf(2, 9)
  )

  @Test
  fun `when done is clicked, appointment should be scheduled with the correct due date`() {
    whenever(repository.createReminder(any(), any())).thenReturn(Completable.complete())

    val current = AppointmentReminder("2 weeks", 2, ChronoUnit.WEEKS)
    uiEvents.onNext(AppointmentReminderSheetCreated(3, appointmentUuid))
    uiEvents.onNext(ReminderCreated(current))

    verify(repository).createReminder(appointmentUuid, LocalDate.now(ZoneOffset.UTC).plus(2L, ChronoUnit.WEEKS))
    verify(sheet).closeSheet()
  }
}

