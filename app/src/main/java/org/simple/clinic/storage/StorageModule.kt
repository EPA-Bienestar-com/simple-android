package org.simple.clinic.storage

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import io.requery.android.database.sqlite.SQLiteGlobal
import net.sqlcipher.database.SupportFactory
import org.simple.clinic.AppDatabase
import org.simple.clinic.di.AppScope
import org.simple.clinic.questionnaire.component.BaseComponentData
import org.simple.clinic.storage.migrations.RoomMigrationsModule
import org.simple.clinic.storage.text.TextRecord
import org.simple.clinic.storage.text.TextStoreModule
import org.simple.clinic.user.User
import org.simple.clinic.util.CryptoUtils.generateEncodedKey
import org.simple.clinic.util.SQLCipherUtils
import org.simple.clinic.util.ThreadPools
import javax.inject.Named


const val DATABASE_NAME = "red-db"
const val DATABASE_ENCRYPTION_KEY = "DATABASE_ENCRYPTION_KEY"
const val ALGORITHM_AES = "AES"
const val KEY_SIZE = 256

@Module(includes = [
  RoomMigrationsModule::class,
  SharedPreferencesModule::class,
  TextStoreModule::class,
  SqliteModule::class
])
class StorageModule {

  @Provides
  @AppScope
  fun appDatabase(
      appContext: Application,
      factory: SupportSQLiteOpenHelper.Factory,
      migrations: List<@JvmSuppressWildcards Migration>,
      moshi: Moshi,
      @Named("encrypted_shared_preferences") sharedPreferences: SharedPreferences
  ): AppDatabase {

    val passphrase = getPassphrase(sharedPreferences) ?: initializePassphrase(sharedPreferences)
    val state = SQLCipherUtils.getDatabaseState(appContext, DATABASE_NAME)

    if (state == SQLCipherUtils.State.UNENCRYPTED) {
      SQLCipherUtils.encrypt(appContext, DATABASE_NAME, passphrase)
    }

    // Don't occupy all connections with Room threads since there are
    // non-Room accesses of the database which SQLite itself might do
    // internally.
    val sqliteThreadPoolCount = SQLiteGlobal.getWALConnectionPoolSize() / 2
    val queryExecutor = ThreadPools.create(
        corePoolSize = sqliteThreadPoolCount,
        maxPoolSize = sqliteThreadPoolCount,
        threadPrefix = "room-query"
    )

    return Room.databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME)
        .openHelperFactory(factory)
        .openHelperFactory(SupportFactory(passphrase))
        .addMigrations(*migrations.toTypedArray())
        .addTypeConverter(BaseComponentData.RoomTypeConverter(moshi))
        .setQueryExecutor(queryExecutor)
        .build()
  }

  private fun initializePassphrase(sharedPreferences: SharedPreferences): ByteArray {
    val passphrase = generateEncodedKey(ALGORITHM_AES, KEY_SIZE)

    sharedPreferences.edit(commit = true) {
      putString(DATABASE_ENCRYPTION_KEY, passphrase.toString(Charsets.ISO_8859_1))
    }

    return passphrase
  }

  private fun getPassphrase(sharedPreferences: SharedPreferences): ByteArray? {
    val passphraseString = sharedPreferences.getString(DATABASE_ENCRYPTION_KEY, null)
    return passphraseString?.toByteArray(Charsets.ISO_8859_1)
  }

  @Provides
  fun userDao(appDatabase: AppDatabase): User.RoomDao {
    return appDatabase.userDao()
  }

  @Provides
  fun provideTextStoreDao(appDatabase: AppDatabase): TextRecord.RoomDao = appDatabase.textRecordDao()
}
