package org.simple.clinic.storage

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Suppress("ClassName")
class Migration_26_27 : Migration(26, 27) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.execSQL("""
      ALTER TABLE "Facility" ADD COLUMN "groupUuid" TEXT DEFAULT null
    """)
  }
}
