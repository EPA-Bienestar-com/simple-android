package org.simple.clinic.registration.phone.loggedout

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.simple.clinic.user.UserSession
import org.simple.clinic.user.UserSession.LogoutResult.Failure
import org.simple.clinic.user.UserSession.LogoutResult.Success
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.scheduler.TestSchedulersProvider
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent
import org.simple.mobius.migration.MobiusTestFixture

class LoggedOutOfDeviceDialogControllerTest {

  @get:Rule
  val rule: TestRule = RxErrorsRule()

  private val ui = mock<LoggedOutOfDeviceDialogUi>()
  private val userSession = mock<UserSession>()
  private val uiEvents = PublishSubject.create<UiEvent>()

  private lateinit var controllerSubscription: Disposable
  private lateinit var testFixture: MobiusTestFixture<LoggedOutOfDeviceModel, LoggedOutOfDeviceEvent, LoggedOutOfDeviceEffect>

  @After
  fun tearDown() {
    controllerSubscription.dispose()
    testFixture.dispose()
  }

  @Test
  fun `when the dialog is created, the okay button must be disabled`() {
    // given
    RxJavaPlugins.setErrorHandler(null)
    whenever(userSession.logout()).thenReturn(Single.never())

    // when
    setupController()

    // then
    verify(ui).disableOkayButton()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the logout result completes successfully, the okay button must be enabled`() {
    // given
    RxJavaPlugins.setErrorHandler(null)
    whenever(userSession.logout()).thenReturn(Single.just(Success))

    // when
    setupController()

    // then
    verify(ui).disableOkayButton()
    verify(ui).enableOkayButton()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the logout fails with runtime exception, then error must be thrown`() {
    // given
    var thrownError: Throwable? = null
    RxJavaPlugins.setErrorHandler { thrownError = it }
    whenever(userSession.logout()).thenReturn(Single.just(Failure(RuntimeException())))

    // when
    setupController()

    // then
    verify(ui).disableOkayButton()
    verify(ui, never()).enableOkayButton()
    verifyNoMoreInteractions(ui)
    assertThat(thrownError).isNotNull()
  }

  @Test
  fun `when the logout fails with null pointer exception, then error must be thrown`() {
    // given
    var thrownError: Throwable? = null
    RxJavaPlugins.setErrorHandler { thrownError = it }
    whenever(userSession.logout()).thenReturn(Single.just(Failure(NullPointerException())))

    // when
    setupController()

    // then
    verify(ui).disableOkayButton()
    verify(ui, never()).enableOkayButton()
    verifyNoMoreInteractions(ui)
    assertThat(thrownError).isNotNull()
  }

  private fun setupController() {
    val controller = LoggedOutOfDeviceDialogController(userSession)

    controllerSubscription = uiEvents
        .compose(controller)
        .subscribe({ uiChange -> uiChange(ui) }, { throw it })

    uiEvents.onNext(ScreenCreated())

    val effectHandler = LoggedOutOfDeviceEffectHandler(
        schedulersProvider = TestSchedulersProvider.trampoline()
    )

    val uiRenderer = LoggedOutOfDeviceUiRenderer(ui)

    testFixture = MobiusTestFixture(
        events = uiEvents.ofType(),
        defaultModel = LoggedOutOfDeviceModel.create(),
        init = LoggedOutOfDeviceInit(),
        update = LoggedOutOfDeviceUpdate(),
        effectHandler = effectHandler.build(),
        modelUpdateListener = uiRenderer::render
    )

    testFixture.start()
  }
}
