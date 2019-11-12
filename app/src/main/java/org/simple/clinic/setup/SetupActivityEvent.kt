package org.simple.clinic.setup

import org.simple.clinic.appconfig.Country
import org.simple.clinic.user.User
import org.simple.clinic.util.Optional

sealed class SetupActivityEvent

data class UserDetailsFetched(
    val hasUserCompletedOnboarding: Boolean,
    val loggedInUser: Optional<User>,
    val userSelectedCountry: Optional<Country>
) : SetupActivityEvent()

object DatabaseInitialized : SetupActivityEvent()
