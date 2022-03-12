package org.simple.clinic.setup

import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import org.simple.clinic.scopes.OnboardingScope
import java.time.Duration

@Module
@ContributesTo(OnboardingScope::class)
class SetupActivityConfigModule {

  @Provides
  fun providesSetupActivityConfig(): SetupActivityConfig {
    return SetupActivityConfig(databaseMaintenanceTaskInterval = Duration.ofMinutes(1))
  }
}
