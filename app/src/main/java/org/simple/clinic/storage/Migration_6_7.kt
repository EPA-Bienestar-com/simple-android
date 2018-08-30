package org.simple.clinic.storage

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.migration.Migration

/**
 * Adds [LoggedInUserFacilityMapping] table.
 */
@Suppress("ClassName")
class Migration_6_7 : Migration(6, 7) {
  override fun migrate(database: SupportSQLiteDatabase) {
    database.inTransaction {
      database.execSQL("""
      CREATE TABLE IF NOT EXISTS `LoggedInUserFacilityMapping` (
          `userUuid` TEXT NOT NULL,
          `facilityUuid` TEXT NOT NULL,
          `isCurrentFacility` INTEGER NOT NULL,
          PRIMARY KEY(`userUuid`, `facilityUuid`),
          FOREIGN KEY(`userUuid`) REFERENCES `LoggedInUser`(`uuid`) ON UPDATE NO ACTION ON DELETE NO ACTION ,
          FOREIGN KEY(`facilityUuid`) REFERENCES `Facility`(`uuid`) ON UPDATE NO ACTION ON DELETE NO ACTION )
      """)

      database.execSQL("""
      CREATE INDEX `index_LoggedInUserFacilityMapping_facilityUuid`
      ON `LoggedInUserFacilityMapping` (`facilityUuid`)
      """)

      database.execSQL("""
      INSERT INTO `LoggedInUserFacilityMapping`(`userUuid`, `facilityUuid`, `isCurrentFacility`)
      SELECT `uuid`, `facilityUuid`, 1
      FROM `LoggedInUser`
       """)

      database.execSQL("ALTER TABLE `LoggedInUser` RENAME TO `LoggedInUser_v6`")
      database.execSQL("""
      CREATE TABLE IF NOT EXISTS `LoggedInUser` (
      `uuid` TEXT NOT NULL,
      `fullName` TEXT NOT NULL,
      `phoneNumber` TEXT NOT NULL,
      `pinDigest` TEXT NOT NULL,
      `status` TEXT NOT NULL,
      `createdAt` TEXT NOT NULL,
      `updatedAt` TEXT NOT NULL,
      PRIMARY KEY(`uuid`));
      """)
      database.execSQL("""
      INSERT INTO `LoggedInUser`(`uuid`, `fullName`, `phoneNumber`, `pinDigest`, `status`, `createdAt`, `updatedAt`)
      SELECT `uuid`, `fullName`, `phoneNumber`, `pinDigest`, `status`, `createdAt`, `updatedAt`
      FROM `LoggedInUser_v6`
      """)
      database.execSQL("DROP TABLE `LoggedInUser_v6`")
    }
  }
}
