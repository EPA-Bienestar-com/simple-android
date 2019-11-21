package org.simple.clinic.registration.register

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.user.User
import org.simple.clinic.user.User.LoggedInStatus.NOT_LOGGED_IN
import org.simple.clinic.user.UserSession
import org.simple.clinic.user.UserStatus.WaitingForApproval
import org.simple.clinic.user.registeruser.RegisterUser
import org.simple.clinic.user.registeruser.RegistrationResult
import org.simple.clinic.user.registeruser.RegistrationResult.NetworkError
import org.simple.clinic.user.registeruser.RegistrationResult.Success
import org.simple.clinic.user.registeruser.RegistrationResult.UnexpectedError
import org.simple.clinic.util.Just
import org.simple.clinic.util.Optional
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent
import java.util.UUID

class RegistrationLoadingScreenControllerTest {

  private val userSession = mock<UserSession>()
  private val screen = mock<RegistrationLoadingScreen>()
  private val registerUser = mock<RegisterUser>()
  private val facilityRepository = mock<FacilityRepository>()
  private val uiEvents = PublishSubject.create<UiEvent>()

  private val user = PatientMocker.loggedInUser(
      uuid = UUID.fromString("fe1786be-5725-45b5-a6aa-e9ce0f99f794"),
      loggedInStatus = NOT_LOGGED_IN,
      status = WaitingForApproval
  )
  private val facility = PatientMocker.facility(UUID.fromString("37e253a9-8a8a-4c60-8aac-34338dc47e8b"))

  private val controller = RegistrationLoadingScreenController(userSession, facilityRepository, registerUser)

  @Before
  fun setUp() {
    uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(screen) }
  }

  @Test
  fun `when retry button is clicked, then register api should be called`() {
    whenever(userSession.loggedInUser()) doReturn Observable.just<Optional<User>>(Just(user))
    whenever(facilityRepository.currentFacility(user)) doReturn Observable.just(facility)
    whenever(registerUser.registerUserAtFacility(user, facility)).doReturn(Single.just<RegistrationResult>(Success))
    whenever(userSession.clearOngoingRegistrationEntry()).doReturn(Completable.complete())

    uiEvents.onNext(RegisterErrorRetryClicked)

    verify(registerUser).registerUserAtFacility(user, facility)
    verify(userSession).clearOngoingRegistrationEntry()
    verify(screen).openHomeScreen()
  }

  @Test
  fun `when screen is created, then the user registration api should be called`() {
    // given
    whenever(userSession.loggedInUser()) doReturn Observable.just<Optional<User>>(Just(user))
    whenever(facilityRepository.currentFacility(user)) doReturn Observable.just(facility)
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.never()

    // when
    uiEvents.onNext(ScreenCreated())

    // then
    verify(registerUser).registerUserAtFacility(user, facility)
    verifyZeroInteractions(screen)
  }

  @Test
  fun `when the register user call succeeds, then clear registration entry and go to home screen`() {
    // given
    whenever(userSession.loggedInUser()) doReturn Observable.just<Optional<User>>(Just(user))
    whenever(facilityRepository.currentFacility(user)) doReturn Observable.just(facility)
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.just<RegistrationResult>(Success)
    whenever(userSession.clearOngoingRegistrationEntry()) doReturn Completable.complete()

    // when
    uiEvents.onNext(ScreenCreated())

    // then
    verify(screen).openHomeScreen()
    verifyNoMoreInteractions(screen)
  }

  @Test
  fun `when the register call fails with a network error, show the network error message`() {
    // given
    whenever(userSession.loggedInUser()) doReturn Observable.just<Optional<User>>(Just(user))
    whenever(facilityRepository.currentFacility(user)) doReturn Observable.just(facility)
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.just<RegistrationResult>(NetworkError)

    // when
    uiEvents.onNext(ScreenCreated())

    // then
    verify(screen).showNetworkError()
    verifyNoMoreInteractions(screen)
  }

  @Test
  fun `when the register call fails with any other error, show the generic error message`() {
    // given
    whenever(userSession.loggedInUser()) doReturn Observable.just<Optional<User>>(Just(user))
    whenever(facilityRepository.currentFacility(user)) doReturn Observable.just(facility)
    whenever(registerUser.registerUserAtFacility(user, facility)) doReturn Single.just<RegistrationResult>(UnexpectedError)

    // when
    uiEvents.onNext(ScreenCreated())

    // then
    verify(screen).showUnexpectedError()
    verifyNoMoreInteractions(screen)
  }
}
