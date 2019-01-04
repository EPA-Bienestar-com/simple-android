package org.simple.clinic.bp

import com.f2prateek.rx.preferences2.Preference
import com.f2prateek.rx.preferences2.RxSharedPreferences
import dagger.Module
import dagger.Provides
import io.reactivex.Single
import org.simple.clinic.AppDatabase
import org.simple.clinic.bp.sync.BloodPressureSyncApiV2
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.OptionalRxPreferencesConverter
import org.simple.clinic.util.StringPreferenceConverter
import retrofit2.Retrofit
import javax.inject.Named

@Module
open class BloodPressureModule {

  @Provides
  fun dao(appDatabase: AppDatabase): BloodPressureMeasurement.RoomDao {
    return appDatabase.bloodPressureDao()
  }

  @Provides
  fun syncApi(retrofit: Retrofit): BloodPressureSyncApiV2 {
    return retrofit.create(BloodPressureSyncApiV2::class.java)
  }

  @Provides
  @Named("last_bp_pull_token")
  fun lastPullToken(rxSharedPrefs: RxSharedPreferences): Preference<Optional<String>> {
    return rxSharedPrefs.getObject("last_bp_pull_token_v2", None, OptionalRxPreferencesConverter(StringPreferenceConverter()))
  }

  @Provides
  open fun provideBloodPressureEntryConfig(): Single<BloodPressureConfig> {
    return Single.just(BloodPressureConfig(deleteBloodPressureFeatureEnabled = false))
  }
}
