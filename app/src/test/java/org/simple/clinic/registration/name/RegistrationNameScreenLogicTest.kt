package org.simple.clinic.registration.name

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.user.OngoingRegistrationEntry
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import org.simple.clinic.util.toOptional
import org.simple.clinic.widgets.UiEvent
import org.simple.mobius.migration.MobiusTestFixture
import java.util.UUID

class RegistrationNameScreenLogicTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val uiEvents = PublishSubject.create<UiEvent>()
  private val ui = mock<RegistrationNameUi>()
  private val uiActions = mock<RegistrationNameUiActions>()
  private val userSession = mock<UserSession>()

  private val currentOngoingRegistrationEntry = OngoingRegistrationEntry(
      uuid = UUID.fromString("301a9ea3-caed-4e50-a144-bc5aad66a53d"),
      phoneNumber = "1111111111"
  )

  private lateinit var testFixture: MobiusTestFixture<RegistrationNameModel, RegistrationNameEvent, RegistrationNameEffect>

  @After
  fun tearDown() {
    testFixture.dispose()
  }

  @Test
  fun `when next is clicked with a valid name then the ongoing entry should be updated with the name and the next screen should be opened`() {
    // given
    val input = "Ashok Kumar"
    val entryWithName = currentOngoingRegistrationEntry.withName(input)

    whenever(userSession.ongoingRegistrationEntry()).thenReturn(currentOngoingRegistrationEntry.toOptional())

    // when
    setupController()
    uiEvents.onNext(RegistrationFullNameTextChanged(input))
    uiEvents.onNext(RegistrationFullNameDoneClicked())

    // then
    verify(userSession).saveOngoingRegistrationEntry(entryWithName)
    verify(uiActions).openRegistrationPinEntryScreen()
    verify(uiActions).preFillUserDetails(currentOngoingRegistrationEntry)
    verify(ui, times(2)).hideValidationError()
    verifyNoMoreInteractions(ui)
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when screen is created then user's existing details should be pre-filled`() {
    // given
    val ongoingEntry = currentOngoingRegistrationEntry.withName("Ashok Kumar")
    whenever(userSession.ongoingRegistrationEntry()).thenReturn(ongoingEntry.toOptional())

    // when
    setupController(ongoingRegistrationEntry = ongoingEntry)

    // then
    verify(uiActions).preFillUserDetails(ongoingEntry)
    verify(ui).hideValidationError()
    verifyNoMoreInteractions(ui)
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `proceed button clicks should only be accepted if the input name is valid`() {
    // given
    val validName = "Ashok"
    val invalidName = "  "

    whenever(userSession.ongoingRegistrationEntry()).thenReturn(currentOngoingRegistrationEntry.toOptional())

    // when
    setupController()
    verify(uiActions).preFillUserDetails(currentOngoingRegistrationEntry)
    uiEvents.onNext(RegistrationFullNameTextChanged(invalidName))
    uiEvents.onNext(RegistrationFullNameDoneClicked())

    // then
    verify(ui).hideValidationError()
    verify(ui).showEmptyNameValidationError()
    clearInvocations(ui, uiActions)

    // when
    uiEvents.onNext(RegistrationFullNameTextChanged(validName))
    uiEvents.onNext(RegistrationFullNameDoneClicked())

    // then
    verify(userSession).saveOngoingRegistrationEntry(currentOngoingRegistrationEntry.withName(validName))
    verify(uiActions).openRegistrationPinEntryScreen()
    verify(ui, times(2)).hideValidationError()
    verifyNoMoreInteractions(ui)
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when proceed is clicked with an empty name then an error should be shown`() {
    // when
    setupController()
    uiEvents.onNext(RegistrationFullNameTextChanged(""))
    uiEvents.onNext(RegistrationFullNameDoneClicked())

    // then
    verify(userSession, never()).saveOngoingRegistrationEntry(any())
    verify(uiActions).preFillUserDetails(currentOngoingRegistrationEntry)
    verify(ui).hideValidationError()
    verify(ui).showEmptyNameValidationError()
    verify(uiActions, never()).openRegistrationPinEntryScreen()
    verifyNoMoreInteractions(ui)
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when input text is changed then any visible errors should be removed`() {
    // when
    setupController()
    uiEvents.onNext(RegistrationFullNameTextChanged(""))

    // then
    verify(uiActions).preFillUserDetails(currentOngoingRegistrationEntry)
    verify(ui).hideValidationError()
    verifyNoMoreInteractions(ui)
    verifyNoMoreInteractions(uiActions)
  }

  private fun setupController(
      ongoingRegistrationEntry: OngoingRegistrationEntry = currentOngoingRegistrationEntry
  ) {

    val uiRenderer = RegistrationNameUiRenderer(ui)
    val effectHandler = RegistrationNameEffectHandler(
        schedulers = TrampolineSchedulersProvider(),
        userSession = userSession,
        uiActions = uiActions
    )

    testFixture = MobiusTestFixture(
        events = uiEvents.ofType(),
        defaultModel = RegistrationNameModel.create(ongoingRegistrationEntry),
        init = RegistrationNameInit(),
        update = RegistrationNameUpdate(),
        effectHandler = effectHandler.build(),
        modelUpdateListener = uiRenderer::render
    )
    testFixture.start()
  }
}
