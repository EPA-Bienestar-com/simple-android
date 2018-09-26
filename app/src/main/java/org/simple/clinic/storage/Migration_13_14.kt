package org.simple.clinic.storage

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.migration.Migration
import org.simple.clinic.medicalhistory.MedicalHistory
import org.threeten.bp.Instant
import java.util.UUID

/**
 * Adds an empty [MedicalHistory] for all patients.
 */
@Suppress("ClassName")
class Migration_13_14 : Migration(13, 14) {

  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("DELETE FROM `MedicalHistory`")
    db.query("SELECT * FROM `Patient`").use {
      val nowTime = Instant.now()
      val syncStatus = "PENDING"
      val falseAsInt = 0

      while (it.moveToNext()) {
        val patientUuid = it.getString(it.getColumnIndex("uuid"))
        val historyUuid = UUID.randomUUID()
        db.execSQL("""
          INSERT INTO `MedicalHistory` VALUES(
            '$historyUuid',
            '$patientUuid',
             $falseAsInt,
             $falseAsInt,
             $falseAsInt,
             $falseAsInt,
             $falseAsInt,
            '$syncStatus',
            '$nowTime',
            '$nowTime');
        """)
      }
    }
  }
}
