package org.simple.clinic.setup

import androidx.fragment.app.FragmentManager
import com.f2prateek.rx.preferences2.Preference
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import org.simple.clinic.R
import org.simple.clinic.activity.placeholder.PlaceholderScreen
import org.simple.clinic.main.TypedPreference
import org.simple.clinic.main.TypedPreference.Type.DatabaseMaintenanceRunAt
import org.simple.clinic.navigation.v2.Router
import org.simple.clinic.scopes.OnboardingScope
import org.simple.clinic.scopes.SingleIn
import org.simple.clinic.util.preference.InstantRxPreferencesConverter
import org.simple.clinic.util.preference.getOptional
import java.time.Instant
import java.util.Optional

@Module
@ContributesTo(OnboardingScope::class)
class SetupActivityModule {

  @Provides
  @TypedPreference(DatabaseMaintenanceRunAt)
  fun providesDatabaseMaintenanceRunAt(
      rxSharedPreferences: RxSharedPreferences
  ): Preference<Optional<Instant>> {
    return rxSharedPreferences.getOptional("database_maintenance_run_at", InstantRxPreferencesConverter())
  }

  @Provides
  @SingleIn(OnboardingScope::class)
  fun providesRouter(fragmentManager: FragmentManager): Router {
    return Router(
        initialScreenKey = PlaceholderScreen.Key(),
        fragmentManager = fragmentManager,
        containerId = R.id.screen_host_view
    )
  }
}
