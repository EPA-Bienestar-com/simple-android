package org.simple.clinic.registration.register

import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import dagger.Lazy
import io.reactivex.Single
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import org.junit.After
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.user.User.LoggedInStatus.NOT_LOGGED_IN
import org.simple.clinic.user.UserStatus.WaitingForApproval
import org.simple.clinic.user.registeruser.RegisterUser
import org.simple.clinic.user.registeruser.RegistrationResult
import org.simple.clinic.user.registeruser.RegistrationResult.NetworkError
import org.simple.clinic.user.registeruser.RegistrationResult.Success
import org.simple.clinic.user.registeruser.RegistrationResult.UnexpectedError
import org.simple.clinic.util.scheduler.TestSchedulersProvider
import org.simple.clinic.widgets.UiEvent
import org.simple.mobius.migration.MobiusTestFixture
import java.util.UUID

class RegistrationLoadingLogicTest {

  private val ui = mock<RegistrationLoadingUi>()
  private val uiActions = mock<RegistrationLoadingUiActions>()
  private val registerUser = mock<RegisterUser>()
  private val uiEvents = PublishSubject.create<UiEvent>()

  private val user = TestData.loggedInUser(
      uuid = UUID.fromString("fe1786be-5725-45b5-a6aa-e9ce0f99f794"),
      loggedInStatus = NOT_LOGGED_IN,
      status = WaitingForApproval
  )
  private val facility = TestData.facility(UUID.fromString("37e253a9-8a8a-4c60-8aac-34338dc47e8b"))

  private lateinit var testFixture: MobiusTestFixture<RegistrationLoadingModel, RegistrationLoadingEvent, RegistrationLoadingEffect>

  @After
  fun tearDown() {
    testFixture.dispose()
  }

  @Test
  fun `when retry button is clicked, then user registration should be attempted again`() {
    // given
    whenever(registerUser.registerUserAtFacility(user, facility)).doReturn(
        Single.just<RegistrationResult>(NetworkError),
        Single.just<RegistrationResult>(Success)
    )

    // when
    setupController()

    // then
    verify(ui).showNetworkError()
    verifyNoMoreInteractions(ui, uiActions)

    // when
    clearInvocations(ui, uiActions)
    uiEvents.onNext(RegisterErrorRetryClicked)

    // then
    verify(uiActions).openHomeScreen()
    verifyNoMoreInteractions(ui, uiActions)
  }

  @Test
  fun `when screen is created, then the user registration should be attempted`() {
    // given
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.never()

    // when
    setupController()

    // then
    verify(registerUser).registerUserAtFacility(user, facility)
    verifyZeroInteractions(ui, uiActions)
  }

  @Test
  fun `when the user registration succeeds, then clear registration entry and go to home screen`() {
    // given
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.just<RegistrationResult>(Success)

    // when
    setupController()

    // then
    verify(uiActions).openHomeScreen()
    verifyNoMoreInteractions(ui, uiActions)
  }

  @Test
  fun `when the user registration fails with a network error, show the network error message`() {
    // given
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.just<RegistrationResult>(NetworkError)

    // when
    setupController()

    // then
    verify(ui).showNetworkError()
    verifyNoMoreInteractions(ui, uiActions)
  }

  @Test
  fun `when the user registration fails with any other error, show the generic error message`() {
    // given
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.just<RegistrationResult>(UnexpectedError)

    // when
    setupController()

    // then
    verify(ui).showUnexpectedError()
    verifyNoMoreInteractions(uiActions)
  }

  private fun setupController() {
    val effectHandler = RegistrationLoadingEffectHandler(
        schedulers = TestSchedulersProvider.trampoline(),
        registerUser = registerUser,
        currentUser = Lazy { user },
        currentFacility = Lazy { facility },
        uiActions = uiActions
    )
    val uiRenderer = RegistrationLoadingUiRenderer(ui)

    testFixture = MobiusTestFixture(
        events = uiEvents.ofType(),
        defaultModel = RegistrationLoadingModel.create(),
        update = RegistrationLoadingUpdate(),
        effectHandler = effectHandler.build(),
        init = RegistrationLoadingInit(),
        modelUpdateListener = uiRenderer::render
    )
    testFixture.start()
  }
}
