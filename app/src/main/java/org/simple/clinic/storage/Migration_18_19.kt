package org.simple.clinic.storage

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Suppress("ClassName")
class Migration_18_19 : Migration(18, 19) {
  override fun migrate(database: SupportSQLiteDatabase) {
    database.execSQL("""CREATE TABLE IF NOT EXISTS "OngoingLoginEntry" (
      "uuid" TEXT NOT NULL,
      "phoneNumber" TEXT NOT NULL,
      "pin" TEXT,
      PRIMARY KEY("uuid"))""")
  }
}
