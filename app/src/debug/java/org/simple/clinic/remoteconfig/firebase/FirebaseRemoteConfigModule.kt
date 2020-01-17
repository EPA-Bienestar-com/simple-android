package org.simple.clinic.remoteconfig.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.Module
import dagger.Provides
import org.simple.clinic.di.AppScope
import org.threeten.bp.Duration
import timber.log.Timber
import javax.inject.Named

@Module
class FirebaseRemoteConfigModule {

  @Provides
  @AppScope
  fun remoteConfig(): FirebaseRemoteConfig {
    return FirebaseRemoteConfig.getInstance().apply {
      val settings = FirebaseRemoteConfigSettings
          .Builder()
          .setMinimumFetchIntervalInSeconds(Duration.ZERO.seconds)
          .build()

      setConfigSettingsAsync(settings)
          .addOnSuccessListener { Timber.i("Set remote config settings") }
          .addOnFailureListener { Timber.e(it) }
    }
  }
}
