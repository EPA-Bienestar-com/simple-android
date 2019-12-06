package org.simple.clinic.setup

import com.f2prateek.rx.preferences2.Preference
import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import io.reactivex.Single
import org.simple.clinic.appconfig.AppConfigRepository
import org.simple.clinic.appconfig.Country
import org.simple.clinic.user.User
import org.simple.clinic.util.Optional
import org.simple.clinic.util.scheduler.SchedulersProvider
import org.simple.clinic.util.toOptional
import javax.inject.Named

class SetupActivityEffectHandler @AssistedInject constructor(
    @Named("onboarding_complete") private val onboardingCompletePreference: Preference<Boolean>,
    @Assisted private val uiActions: UiActions,
    private val userDao: User.RoomDao,
    private val appConfigRepository: AppConfigRepository,
    @Named("fallback") private val fallbackCountry: Country,
    private val schedulersProvider: SchedulersProvider
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(uiActions: UiActions): SetupActivityEffectHandler
  }

  fun build(): ObservableTransformer<SetupActivityEffect, SetupActivityEvent> {
    return RxMobius
        .subtypeEffectHandler<SetupActivityEffect, SetupActivityEvent>()
        .addTransformer(FetchUserDetails::class.java, fetchUserDetails(schedulersProvider.io()))
        .addAction(GoToMainActivity::class.java, uiActions::goToMainActivity, schedulersProvider.ui())
        .addAction(ShowOnboardingScreen::class.java, uiActions::showOnboardingScreen, schedulersProvider.ui())
        // We could technically also implicitly wait on the database to
        // initialize by querying the actual user data and make it a
        // property of the FetchUserDetails effect, but that has the
        // problem of making it hidden behaviour.

        // If the user details is removed from this screen in the
        // future, then we will also lose the feature of this screen
        // showing the placeholder UI until the database is migrated.

        // In this case, it might be better to have this as an explicit
        // effect so that the intention is clear.
        .addTransformer(InitializeDatabase::class.java, initializeDatabase(schedulersProvider.io()))
        .addAction(ShowCountrySelectionScreen::class.java, uiActions::showCountrySelectionScreen, schedulersProvider.ui())
        .addTransformer(SetFallbackCountryAsCurrentCountry::class.java, setFallbackCountryAsSelected(schedulersProvider.io()))
        .build()
  }

  private fun fetchUserDetails(scheduler: Scheduler): ObservableTransformer<FetchUserDetails, SetupActivityEvent> {
    return ObservableTransformer { effectStream ->
      effectStream
          .flatMapSingle { Single.fromCallable(::readUserDetailsFromStorage).subscribeOn(scheduler) }
          .map { (hasUserCompletedOnboarding, loggedInUser, userSelectedCountry) ->
            UserDetailsFetched(hasUserCompletedOnboarding, loggedInUser, userSelectedCountry)
          }
    }
  }

  private fun readUserDetailsFromStorage(): Triple<Boolean, Optional<User>, Optional<Country>> {
    val hasUserCompletedOnboarding = onboardingCompletePreference.get()
    val loggedInUser = userDao.userImmediate().toOptional()
    val userSelectedCountry = appConfigRepository.currentCountry()

    return Triple(hasUserCompletedOnboarding, loggedInUser, userSelectedCountry)
  }

  private fun initializeDatabase(scheduler: Scheduler): ObservableTransformer<InitializeDatabase, SetupActivityEvent> {
    return ObservableTransformer { effectStream ->
      effectStream
          .flatMapSingle { userDao.userCount().subscribeOn(scheduler) }
          .map { DatabaseInitialized }
    }
  }

  private fun setFallbackCountryAsSelected(scheduler: Scheduler): ObservableTransformer<SetFallbackCountryAsCurrentCountry, SetupActivityEvent> {
    return ObservableTransformer { effectStream ->
      effectStream.flatMapSingle {
        appConfigRepository
            .saveCurrentCountry(fallbackCountry)
            .subscribeOn(scheduler)
            .toSingleDefault(FallbackCountrySetAsSelected)
      }
    }
  }
}
