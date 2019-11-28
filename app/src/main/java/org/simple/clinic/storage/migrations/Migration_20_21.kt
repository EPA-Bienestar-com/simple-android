package org.simple.clinic.storage.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import org.simple.clinic.storage.inTransaction

@Suppress("ClassName")
class Migration_20_21 @javax.inject.Inject constructor() : Migration(20, 21) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.inTransaction {
      database.execSQL("""
        UPDATE "Appointment"
        SET "cancelReason" = 'not_responding'
        WHERE "cancelReason" = 'PATIENT_NOT_RESPONDING'
      """)

      database.execSQL("""
        UPDATE "Appointment"
        SET "cancelReason" = 'moved'
        WHERE "cancelReason" = 'MOVED'
      """)

      database.execSQL("""
        UPDATE "Appointment"
        SET "cancelReason" = 'dead'
        WHERE "cancelReason" = 'DEAD'
      """)

      database.execSQL("""
        UPDATE "Appointment"
        SET "cancelReason" = 'other'
        WHERE "cancelReason" = 'OTHER'
      """)
    }
  }
}
