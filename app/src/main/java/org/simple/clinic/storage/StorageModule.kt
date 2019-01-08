package org.simple.clinic.storage

import android.app.Application
import android.arch.persistence.db.SupportSQLiteOpenHelper
import android.arch.persistence.room.Room
import android.arch.persistence.room.migration.Migration
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.f2prateek.rx.preferences2.RxSharedPreferences
import dagger.Module
import dagger.Provides
import org.simple.clinic.AppDatabase
import org.simple.clinic.di.AppScope
import org.simple.clinic.di.AppSqliteOpenHelperFactory

@Module
open class StorageModule(
    private val databaseName: String = "red-db",
    private val runDatabaseQueriesOnMainThread: Boolean = false
) {

  @Provides
  fun databaseMigrations(): MutableList<Migration> {
    return mutableListOf(
        Migration_3_4(),
        Migration_4_5(),
        Migration_5_6(),
        Migration_6_7(),
        Migration_7_8(),
        Migration_8_9(),
        Migration_9_10(),
        Migration_10_11(),
        Migration_11_12(),
        Migration_12_13(),
        Migration_13_14(),
        Migration_14_15(),
        Migration_15_16(),
        Migration_16_17(),
        Migration_17_18(),
        Migration_18_19(),
        Migration_19_20(),
        Migration_20_21(),
        Migration_21_22(),
        Migration_22_23(),
        Migration_23_24(),
        Migration_24_25(),
        Migration_25_26(),
        Migration_26_27())
  }

  @Provides
  @AppScope
  fun appDatabase(
      appContext: Application,
      factory: SupportSQLiteOpenHelper.Factory,
      migrations: MutableList<Migration>
  ): AppDatabase {
    return Room.databaseBuilder(appContext, AppDatabase::class.java, databaseName)
        .openHelperFactory(factory)
        .apply {
          if (runDatabaseQueriesOnMainThread) {
            allowMainThreadQueries()
          }
        }
        .addMigrations(*migrations.toTypedArray())
        .build()
  }

  @Provides
  fun rxSharedPreferences(appContext: Application): RxSharedPreferences {
    val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
    return RxSharedPreferences.create(preferences)
  }

  @Provides
  fun sharedPreferences(appContext: Application): SharedPreferences {
    return PreferenceManager.getDefaultSharedPreferences(appContext)
  }

  @Provides
  open fun sqliteOpenHelperFactory(): SupportSQLiteOpenHelper.Factory = AppSqliteOpenHelperFactory()
}
