package org.simple.clinic.storage

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Suppress("ClassName")
class Migration_25_26 : Migration(25, 26) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.execSQL("""
      ALTER TABLE ProtocolDrug ADD COLUMN "order" INTEGER NOT NULL DEFAULT 0
    """)
  }
}
