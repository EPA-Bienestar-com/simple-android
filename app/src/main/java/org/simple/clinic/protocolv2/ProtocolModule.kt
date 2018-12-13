package org.simple.clinic.protocolv2

import com.f2prateek.rx.preferences2.Preference
import com.f2prateek.rx.preferences2.RxSharedPreferences
import dagger.Module
import dagger.Provides
import org.simple.clinic.AppDatabase
import org.simple.clinic.protocolv2.sync.ProtocolSync
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.OptionalRxPreferencesConverter
import org.simple.clinic.util.StringPreferenceConverter
import retrofit2.Retrofit
import javax.inject.Named

@Module
class ProtocolModule {

  @Provides
  fun protocolDao(appDatabase: AppDatabase): Protocol.RoomDao {
    return appDatabase.protocolDao()
  }

  @Provides
  fun protocolDrugDao(appDatabase: AppDatabase): ProtocolDrug.RoomDao {
    return appDatabase.protocolDrugDao()
  }

  @Provides
  fun protocolSyncApi(retrofit: Retrofit): ProtocolSyncApiV2 {
    return retrofit.create(ProtocolSyncApiV2::class.java)
  }

  @Provides
  @Named("last_protocol_pull_token")
  fun lastPullToken(rxSharedPreferences: RxSharedPreferences): Preference<Optional<String>> {
    return rxSharedPreferences.getObject("last_protocol_pull_timestamp", None, OptionalRxPreferencesConverter(StringPreferenceConverter()))
  }
}
