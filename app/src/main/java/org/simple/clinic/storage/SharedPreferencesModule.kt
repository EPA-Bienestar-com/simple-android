package org.simple.clinic.storage

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.f2prateek.rx.preferences2.RxSharedPreferences
import dagger.Module
import dagger.Provides
import org.simple.clinic.di.AppScope
import javax.inject.Named

@Module
class SharedPreferencesModule {

  @Provides
  @AppScope
  fun rxSharedPreferences(preferences: SharedPreferences): RxSharedPreferences {
    return RxSharedPreferences.create(preferences)
  }

  @Provides
  @AppScope
  fun sharedPreferences(appContext: Application): SharedPreferences {
    return PreferenceManager.getDefaultSharedPreferences(appContext)
  }

  @Provides
  @AppScope
  @Named("encrypted_shared_preferences")
  fun encryptedSharedPreferences(appContext: Application, masterKey: MasterKey): SharedPreferences {
    return EncryptedSharedPreferences.create(
        appContext,
        "secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
  }
}
