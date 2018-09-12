package org.simple.clinic.user

import com.google.common.truth.Truth
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.util.Just
import org.simple.clinic.util.Optional

@RunWith(JUnitParamsRunner::class)
class NewlyVerifiedUserTest {

  private lateinit var newlyVerifiedUser: NewlyVerifiedUser
  private lateinit var receivedUsers: MutableList<User>

  private val userEmitter = PublishSubject.create<Optional<User>>()!!

  @Before
  fun setUp() {
    newlyVerifiedUser = NewlyVerifiedUser()

    receivedUsers = mutableListOf()

    userEmitter.compose(newlyVerifiedUser)
        .subscribe { receivedUsers.add(it) }
  }

  @Test
  @Parameters(value = [
    "NOT_LOGGED_IN|NOT_LOGGED_IN|OTP_REQUESTED|false",
    "OTP_REQUESTED|OTP_REQUESTED|LOGGED_IN|true",
    "LOGGED_IN|LOGGED_IN|LOGGED_IN|false"
  ])
  fun `when the user status changes to verified, it should emit the user`(
      previousLoggedInStatus2: User.LoggedInStatus,
      previousLoggedInStatus1: User.LoggedInStatus,
      currentLoggedInStatus: User.LoggedInStatus,
      shouldEmitUser: Boolean
  ) {
    val user = PatientMocker.loggedInUser(loggedInStatus = previousLoggedInStatus2)

    userEmitter.onNext(Just(user))
    userEmitter.onNext(Just(user.copy(loggedInStatus = previousLoggedInStatus1)))

    val expectedUser = user.copy(loggedInStatus = currentLoggedInStatus)
    userEmitter.onNext(Just(expectedUser))

    if (shouldEmitUser) {
      Truth.assertThat(receivedUsers).isEqualTo(listOf(expectedUser))
    }
  }
}
