package org.simple.clinic.teleconsultlog.teleconsultrecord

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import java.util.UUID

data class TeleconsultRecordWithPrescribedDrugs(
    @Embedded
    val teleconsultRecord: TeleconsultRecord,

    @Relation(
        parentColumn = "id",
        entity = TeleconsultRecordPrescribedDrug::class,
        entityColumn = "teleconsultRecordId"
    )
    val prescribedDrugs: List<TeleconsultRecordPrescribedDrug>

) {
  @Dao
  interface RoomDao {

    @Query("SELECT * FROM TeleconsultRecord WHERE id = :teleconsultRecordUuid")
    fun getPrescribedUuidForTeleconsultRecordUuid(teleconsultRecordUuid: UUID): TeleconsultRecordWithPrescribedDrugs
  }
}
