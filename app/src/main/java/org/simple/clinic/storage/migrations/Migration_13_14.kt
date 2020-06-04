package org.simple.clinic.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.simple.clinic.medicalhistory.MedicalHistory
import org.simple.clinic.uuid.UuidGenerator
import org.threeten.bp.Instant
import javax.inject.Inject

/**
 * Adds an empty [MedicalHistory] for all patients.
 */
@Suppress("ClassName")
class Migration_13_14 @Inject constructor(
    private val uuidGenerator: UuidGenerator
) : Migration(13, 14) {

  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("DELETE FROM `MedicalHistory`")
    db.query("SELECT * FROM `Patient`").use {
      val nowTime = Instant.now()
      val syncStatus = "PENDING"
      val falseAsInt = 0

      while (it.moveToNext()) {
        val patientUuid = it.getString(it.getColumnIndex("uuid"))
        val historyUuid = uuidGenerator.v4()
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
