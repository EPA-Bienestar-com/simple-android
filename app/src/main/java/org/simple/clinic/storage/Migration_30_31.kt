package org.simple.clinic.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("ClassName")
class Migration_30_31 @javax.inject.Inject constructor() : Migration(30, 31) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.execSQL("""
      CREATE TABLE IF NOT EXISTS "MissingPhoneReminder" (
        "patientUuid" TEXT NOT NULL,
        "remindedAt" TEXT NOT NULL,
        PRIMARY KEY("patientUuid"))
    """)
  }
}
