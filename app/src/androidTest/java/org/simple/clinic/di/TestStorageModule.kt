package org.simple.clinic.di

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dagger.Module
import dagger.Provides
import org.simple.clinic.AppDatabase
import org.simple.clinic.patient.PatientModule
import org.simple.clinic.storage.FileStorageModule
import org.simple.clinic.storage.SharedPreferencesModule
import org.simple.clinic.storage.migrations.RoomMigrationsModule
import org.simple.clinic.summary.PatientSummaryModule
import org.simple.clinic.user.User

@Module(includes = [
  RoomMigrationsModule::class,
  SharedPreferencesModule::class,
  PatientModule::class,
  FileStorageModule::class,
  PatientSummaryModule::class
])
class TestStorageModule {

  @Provides
  fun sqliteOpenHelperFactory(): SupportSQLiteOpenHelper.Factory = AppSqliteOpenHelperFactory(inMemory = true)

  @AppScope
  @Provides
  fun appDatabase(
      appContext: Application,
      factory: SupportSQLiteOpenHelper.Factory
  ): AppDatabase {

    return Room.databaseBuilder(appContext, AppDatabase::class.java, "test-db")
        .openHelperFactory(factory)
        .apply { allowMainThreadQueries() }
        .build()
  }

  @Provides
  fun userDao(appDatabase: AppDatabase): User.RoomDao {
    return appDatabase.userDao()
  }
}
