package org.simple.clinic.drugs

import com.f2prateek.rx.preferences2.Preference
import com.f2prateek.rx.preferences2.RxSharedPreferences
import dagger.Module
import dagger.Provides
import org.simple.clinic.AppDatabase
import org.simple.clinic.drugs.sync.PrescriptionSyncApiV1
import org.simple.clinic.util.InstantRxPreferencesConverter
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.OptionalRxPreferencesConverter
import org.threeten.bp.Instant
import retrofit2.Retrofit
import javax.inject.Named

@Module
class PrescriptionModule {

  @Provides
  fun dao(appDatabase: AppDatabase): PrescribedDrug.RoomDao {
    return appDatabase.prescriptionDao()
  }

  @Provides
  fun syncApi(retrofit: Retrofit): PrescriptionSyncApiV1 {
    return retrofit.create(PrescriptionSyncApiV1::class.java)
  }

  @Provides
  @Named("last_prescription_pull_timestamp")
  fun lastPullTimestamp(rxSharedPrefs: RxSharedPreferences): Preference<Optional<Instant>> {
    return rxSharedPrefs.getObject("last_prescription_pull_timestamp", None, OptionalRxPreferencesConverter(InstantRxPreferencesConverter()))
  }
}
