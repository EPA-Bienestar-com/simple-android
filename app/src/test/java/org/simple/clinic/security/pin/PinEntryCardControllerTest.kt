package org.simple.clinic.security.pin

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.security.ComparisonResult
import org.simple.clinic.security.PasswordHasher
import org.simple.clinic.security.pin.BruteForceProtection.ProtectedState
import org.simple.clinic.security.pin.PinEntryCardView.State
import org.simple.clinic.user.UserSession
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Clock
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset.UTC

@RunWith(JUnitParamsRunner::class)
class PinEntryCardControllerTest {

  private val screen = mock<PinEntryCardView>()
  private val userSession = mock<UserSession>()
  private val passwordHasher = mock<PasswordHasher>()
  private val bruteForceProtection = mock<BruteForceProtection>()

  private lateinit var controller: PinEntryCardController
  private val uiEvents = PublishSubject.create<UiEvent>()
  private val clock = Clock.fixed(Instant.now(), UTC)
  private val loggedInUser = PatientMocker.loggedInUser()
  private val testScheduler = TestScheduler()

  @Before
  fun setUp() {
    whenever(userSession.requireLoggedInUser()).thenReturn(Observable.just(loggedInUser))
    whenever(passwordHasher.compare(any(), any())).thenReturn(Single.never<ComparisonResult>())
    whenever(bruteForceProtection.incrementFailedAttempt()).thenReturn(Completable.complete())
    whenever(bruteForceProtection.recordSuccessfulAuthentication()).thenReturn(Completable.complete())
    whenever(bruteForceProtection.protectedStateChanges()).thenReturn(Observable.never())

    RxJavaPlugins.setComputationSchedulerHandler { testScheduler }

    controller = PinEntryCardController(userSession, passwordHasher, clock, bruteForceProtection)

    uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(screen) }
  }

  @After
  fun tearDown() {
    RxJavaPlugins.reset()
  }

  @Test
  fun `when 4 digits are entered then the PIN should be submitted automatically`() {
    uiEvents.onNext(PinTextChanged("1"))
    uiEvents.onNext(PinTextChanged("12"))
    uiEvents.onNext(PinTextChanged("123"))
    uiEvents.onNext(PinTextChanged("1234"))

    verify(passwordHasher, times(1)).compare(loggedInUser.pinDigest, "1234")
  }

  @Test
  fun `when the PIN is submitted then it should be validated`() {
    uiEvents.onNext(PinTextChanged("1234"))

    verify(passwordHasher).compare(loggedInUser.pinDigest, "1234")
  }

  @Test
  fun `when the PIN is submitted then progress should be shown`() {
    uiEvents.onNext(PinTextChanged("1234"))

    verify(screen).moveToState(State.Progress)
  }

    @Test
    fun `when PIN validation fails then the progress should be hidden`() {
      whenever(passwordHasher.compare(any(), eq("1234"))).thenReturn(Single.just(ComparisonResult.DIFFERENT))

      uiEvents.onNext(PinTextChanged("1234"))

      verify(screen).moveToState(PinEntryCardView.State.PinEntry)
    }

  @Test
  fun `when PIN validation fails then the PIN should be cleared`() {
    whenever(passwordHasher.compare(any(), eq("1234"))).thenReturn(Single.just(ComparisonResult.DIFFERENT))

    uiEvents.onNext(PinTextChanged("1234"))

    verify(screen).clearPin()
  }

  @Test
  fun `when PIN validation succeeds then a success callback should be sent`() {
    whenever(passwordHasher.compare(any(), eq("1234"))).thenReturn(Single.just(ComparisonResult.SAME))

    uiEvents.onNext(PinTextChanged("1234"))

    verify(screen).dispatchAuthenticatedCallback()
  }

  @Test
  fun `when the PIN is submitted then any existing validation error should be removed`() {
    uiEvents.onNext(PinTextChanged("1234"))

    verify(screen).hideError()
  }

  @Test
  fun `when PIN validation fails then the count of failed attempts should be incremented`() {
    whenever(passwordHasher.compare(any(), eq("1234"))).thenReturn(Single.just(ComparisonResult.DIFFERENT))

    uiEvents.onNext(PinTextChanged("1234"))

    verify(bruteForceProtection).incrementFailedAttempt()
  }

  @Test
  fun `when PIN validation succeeds then the count of failed attempts should be reset`() {
    whenever(passwordHasher.compare(any(), eq("1234"))).thenReturn(Single.just(ComparisonResult.SAME))

    uiEvents.onNext(PinTextChanged("1234"))

    verify(bruteForceProtection).recordSuccessfulAuthentication()
  }

  @Test
  fun `when the limit of failed attempts is reached then PIN entry should remain blocked until a fixed duration`() {
    val blockedTill = Instant.now(clock) + Duration.ofMinutes(19) + Duration.ofSeconds(42)

    whenever(bruteForceProtection.protectedStateChanges())
        .thenReturn(Observable.just(ProtectedState.Allowed, ProtectedState.Blocked(blockedTill = blockedTill)))

    uiEvents.onNext(PinEntryViewCreated)

    verify(screen).moveToState(State.PinEntry)
    verify(screen).moveToState(State.BruteForceLocked(timeTillUnlock = "19:42"))
  }

  @Test
  @Parameters(value = [
    "19,42",
    "2,21",
    "0,9"
  ])
  fun `when PIN entry is blocked due to brute force then a timer should be shown to indicate remaining time`(
      minutesRemaining: Long,
      secondsRemaining: Long
  ) {
    val blockedTill = Instant.now(clock) + Duration.ofMinutes(minutesRemaining) + Duration.ofSeconds(secondsRemaining)

    whenever(bruteForceProtection.protectedStateChanges())
        .thenReturn(Observable.just(ProtectedState.Blocked(blockedTill = blockedTill)))

    uiEvents.onNext(PinEntryViewCreated)

    val minutesWithPadding = minutesRemaining.toString().padStart(2, padChar = '0')
    val secondsWithPadding = secondsRemaining.toString().padStart(2, padChar = '0')
    verify(screen).moveToState(State.BruteForceLocked(timeTillUnlock = "$minutesWithPadding:$secondsWithPadding"))
  }
}
