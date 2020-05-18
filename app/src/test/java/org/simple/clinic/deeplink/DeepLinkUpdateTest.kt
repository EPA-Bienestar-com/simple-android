package org.simple.clinic.deeplink

import com.spotify.mobius.test.NextMatchers.hasEffects
import com.spotify.mobius.test.NextMatchers.hasNoModel
import com.spotify.mobius.test.UpdateSpec
import com.spotify.mobius.test.UpdateSpec.assertThatNext
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.user.User
import java.util.UUID

class DeepLinkUpdateTest {

  private val defaultModel = DeepLinkModel.default(null)
  private val updateSpec = UpdateSpec(DeepLinkUpdate())
  private val patientUuid = UUID.fromString("f88cc05b-620a-490a-92f3-1c0c43fb76ab")

  @Test
  fun `if there is no user logged in, then open setup activity`() {
    updateSpec
        .given(defaultModel)
        .whenEvent(UserFetched(null))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(NavigateToSetupActivity as DeepLinkEffect)
        ))
  }

  @Test
  fun `if there is a logged in user and patient uuid is null, then navigate to main activity`() {
    val user = TestData.loggedInUser(
        uuid = UUID.fromString("dc0a9d11-aee4-4792-820f-c5cb66ae5e47"),
        loggedInStatus = User.LoggedInStatus.LOGGED_IN
    )

    updateSpec
        .given(defaultModel)
        .whenEvent(UserFetched(user))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(NavigateToMainActivity as DeepLinkEffect)
        ))
  }

  @Test
  fun `if user didn't complete the login, then navigate to main activity`() {
    val user = TestData.loggedInUser(
        uuid = UUID.fromString("dc0a9d11-aee4-4792-820f-c5cb66ae5e47"),
        loggedInStatus = User.LoggedInStatus.OTP_REQUESTED
    )

    updateSpec
        .given(defaultModel)
        .whenEvent(UserFetched(user))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(NavigateToMainActivity as DeepLinkEffect)
        ))
  }

  @Test
  fun `if user is logged in and patient uuid is not null, then fetch patient`() {
    val user = TestData.loggedInUser(
        uuid = UUID.fromString("fa0dfb7b-a0ea-425a-987d-2056f1a9e93b"),
        loggedInStatus = User.LoggedInStatus.LOGGED_IN
    )
    val model = DeepLinkModel.default(patientUuid)

    updateSpec
        .given(model)
        .whenEvent(UserFetched(user))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(FetchPatient(patientUuid) as DeepLinkEffect)
        ))
  }
}
