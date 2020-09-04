package org.simple.clinic.teleconsultlog.teleconsultrecord

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import java.util.UUID

@Entity(
    primaryKeys = ["teleconsultRecordId", "prescribedDrugUuid"],
    foreignKeys = [
      ForeignKey(
          entity = TeleconsultRecord::class,
          parentColumns = ["id"],
          childColumns = ["teleconsultRecordId"],
          onDelete = ForeignKey.CASCADE
      )
    ]
)
data class TeleconsultRecordPrescribedDrug(
    val teleconsultRecordId: UUID,
    val prescribedDrugUuid: UUID
) {

  @Dao
  interface RoomDao {

    @Insert(onConflict = REPLACE)
    fun save(records: List<TeleconsultRecordPrescribedDrug>)

    @Query("SELECT * FROM TeleconsultRecordPrescribedDrug")
    fun getAll(): List<TeleconsultRecordPrescribedDrug>

    @Query("DELETE FROM TeleconsultRecordPrescribedDrug")
    fun clear()
  }
}
