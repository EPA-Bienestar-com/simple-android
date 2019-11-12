package org.simple.clinic.setup

import com.spotify.mobius.Next
import com.spotify.mobius.Next.noChange
import com.spotify.mobius.Update
import org.simple.clinic.appconfig.Country
import org.simple.clinic.mobius.dispatch
import org.simple.clinic.mobius.next
import org.simple.clinic.user.User
import org.simple.clinic.util.Just
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional

class SetupActivityUpdate : Update<SetupActivityModel, SetupActivityEvent, SetupActivityEffect> {

  override fun update(model: SetupActivityModel, event: SetupActivityEvent): Next<SetupActivityModel, SetupActivityEffect> {
    return when (event) {
      is UserDetailsFetched -> {
        val updatedModel = model
            .withLoggedInUser(event.loggedInUser)
            .withSelectedCountry(event.userSelectedCountry)
        val effect = goToNextScreenEffect(event.loggedInUser, event.hasUserCompletedOnboarding, event.userSelectedCountry)

        next(updatedModel, effect)
      }
      is DatabaseInitialized -> dispatch(FetchUserDetails)
      is FallbackCountrySetAsSelected -> dispatch(GoToMainActivity)
      else -> noChange()
    }
  }

  private fun goToNextScreenEffect(
      loggedInUser: Optional<User>,
      hasUserCompletedOnboarding: Boolean,
      selectedCountry: Optional<Country>
  ): SetupActivityEffect {
    val hasUserLoggedInCompletely = loggedInUser is Just && selectedCountry is Just
    val userPresentButCountryNotSelected = loggedInUser is Just && selectedCountry is None

    return when {
      hasUserLoggedInCompletely -> GoToMainActivity
      userPresentButCountryNotSelected -> SetFallbackCountryAsCurrentCountry
      hasUserCompletedOnboarding.not() -> ShowOnboardingScreen
      else -> ShowCountrySelectionScreen
    }
  }
}
