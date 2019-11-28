package org.simple.clinic.storage.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import org.simple.clinic.storage.inTransaction

@Suppress("ClassName")
class Migration_5_6 @javax.inject.Inject constructor() : Migration(5, 6) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.inTransaction {
      // We need to manually create this table because it's a virtual table and Room doesn't support virtual tables (yet!)
      database.execSQL("""CREATE VIRTUAL TABLE "PatientFuzzySearch" USING spellfix1""")
      execSQL("""INSERT INTO "PatientFuzzySearch"("rowid","word") SELECT "rowid","searchableName" FROM "Patient"""")
    }
  }
}
