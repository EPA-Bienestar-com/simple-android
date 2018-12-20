package org.simple.clinic.registration.confirmpin

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import org.simple.clinic.user.OngoingRegistrationEntry
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.TestClock
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Instant
import java.util.UUID

class RegistrationConfirmPinScreenControllerTest {

  private val uiEvents = PublishSubject.create<UiEvent>()!!
  private val screen = mock<RegistrationConfirmPinScreen>()
  private val userSession = mock<UserSession>()
  private val clock = TestClock()

  private lateinit var controller: RegistrationConfirmPinScreenController

  @Before
  fun setUp() {
    controller = RegistrationConfirmPinScreenController(userSession, clock)

    uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(screen) }
  }

  @Test
  fun `when 4 digits are entered then the PIN should be submitted automatically`() {
    whenever(userSession.ongoingRegistrationEntry()).thenReturn(Single.just(OngoingRegistrationEntry(pin = "1234")))
    whenever(userSession.saveOngoingRegistrationEntry(any())).thenReturn(Completable.complete())

    uiEvents.onNext(RegistrationConfirmPinTextChanged("1"))
    uiEvents.onNext(RegistrationConfirmPinTextChanged("12"))
    uiEvents.onNext(RegistrationConfirmPinTextChanged("123"))
    uiEvents.onNext(RegistrationConfirmPinTextChanged("1234"))

    verify(userSession).saveOngoingRegistrationEntry(OngoingRegistrationEntry(
        pin = "1234",
        pinConfirmation = "1234",
        createdAt = Instant.now(clock)))
  }

  @Test
  fun `when next is clicked with a matching PIN then ongoing entry should be updated and the next screen should be opened`() {
    val input = "1234"

    val ongoingEntry = OngoingRegistrationEntry(pin = "1234")
    whenever(userSession.ongoingRegistrationEntry()).thenReturn(Single.just(ongoingEntry))
    whenever(userSession.saveOngoingRegistrationEntry(any())).thenReturn(Completable.complete())

    uiEvents.onNext(RegistrationConfirmPinTextChanged(input))

    val inOrder = inOrder(userSession, screen)
    inOrder.verify(userSession).saveOngoingRegistrationEntry(check {
      assertThat(it.pinConfirmation).isEqualTo(input)
      assertThat(it.createdAt).isNotNull()
    })
    inOrder.verify(screen).openFacilitySelectionScreen()
  }

  @Test
  fun `proceed button clicks should only be accepted if the confirmation pin matches with original pin`() {
    val originalPin = "1234"
    val invalidConfirmationPin = "123"
    val validConfirmationPin = "1234"

    val ongoingEntry = OngoingRegistrationEntry(pin = originalPin)
    whenever(userSession.ongoingRegistrationEntry()).thenReturn(Single.just(ongoingEntry))
    whenever(userSession.saveOngoingRegistrationEntry(any())).thenReturn(Completable.complete())

    uiEvents.onNext(RegistrationConfirmPinTextChanged(invalidConfirmationPin))
    uiEvents.onNext(RegistrationConfirmPinDoneClicked())

    uiEvents.onNext(RegistrationConfirmPinTextChanged(validConfirmationPin))

    verify(userSession, times(1)).saveOngoingRegistrationEntry(any())
    verify(screen, times(1)).openFacilitySelectionScreen()
  }

  @Test
  fun `when proceed is clicked with a confirmation PIN that does not match with original PIN then an error should be shown`() {
    val ongoingEntry = OngoingRegistrationEntry(pin = "1234")
    whenever(userSession.ongoingRegistrationEntry()).thenReturn(Single.just(ongoingEntry))

    uiEvents.onNext(RegistrationConfirmPinTextChanged("4567"))
    uiEvents.onNext(RegistrationConfirmPinDoneClicked())

    verify(screen, times(2)).showPinMismatchError()
    verify(userSession, never()).saveOngoingRegistrationEntry(any())
    verify(screen, never()).openFacilitySelectionScreen()
  }

  @Test
  fun `when reset PIN is clicked then both PINs should be reset in ongoing entry and the user should be taken to the PIN entry screen`() {
    val ongoingEntry = OngoingRegistrationEntry(
        uuid = UUID.randomUUID(),
        phoneNumber = "1234567890",
        fullName = "Ashok",
        pin = "1234",
        pinConfirmation = "5678",
        createdAt = Instant.now())
    val ongoingEntryWithoutPins = ongoingEntry.copy(pin = null, pinConfirmation = null)
    whenever(userSession.ongoingRegistrationEntry()).thenReturn(Single.just(ongoingEntry))
    whenever(userSession.saveOngoingRegistrationEntry(ongoingEntryWithoutPins)).thenReturn(Completable.complete())

    uiEvents.onNext(RegistrationResetPinClicked())

    val inOrder = inOrder(userSession, screen)
    inOrder.verify(userSession).saveOngoingRegistrationEntry(ongoingEntryWithoutPins)
    inOrder.verify(screen).goBackToPinScreen()
  }
}
