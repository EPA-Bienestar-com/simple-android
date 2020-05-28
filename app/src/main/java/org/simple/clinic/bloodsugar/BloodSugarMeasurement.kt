package org.simple.clinic.bloodsugar

import android.os.Parcelable
import androidx.paging.DataSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.PrimaryKey
import androidx.room.Query
import io.reactivex.Flowable
import io.reactivex.Observable
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.bloodsugar.sync.BloodSugarMeasurementPayload
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.storage.Timestamps
import org.threeten.bp.Instant
import java.util.UUID

@Entity(tableName = "BloodSugarMeasurements")
@Parcelize
data class BloodSugarMeasurement(
    @PrimaryKey
    val uuid: UUID,

    @Embedded(prefix = "reading_")
    val reading: BloodSugarReading,

    val recordedAt: Instant,

    val patientUuid: UUID,

    val userUuid: UUID,

    val facilityUuid: UUID,

    @Embedded
    val timestamps: Timestamps,

    val syncStatus: SyncStatus
) : Parcelable {

  fun toPayload() = BloodSugarMeasurementPayload(
      uuid = uuid,
      bloodSugarType = reading.type,
      bloodSugarValue = reading.value.toFloat(),
      patientUuid = patientUuid,
      facilityUuid = facilityUuid,
      userUuid = userUuid,
      createdAt = timestamps.createdAt,
      updatedAt = timestamps.updatedAt,
      deletedAt = timestamps.deletedAt,
      recordedAt = recordedAt
  )

  @Dao
  interface RoomDao {

    @Insert(onConflict = REPLACE)
    fun save(bloodSugars: List<BloodSugarMeasurement>)

    @Query("""
      SELECT * FROM BloodSugarMeasurements
      WHERE patientUuid = :patientUuid AND deletedAt IS NULL
      ORDER BY recordedAt DESC LIMIT :limit
    """)
    fun latestMeasurements(patientUuid: UUID, limit: Int): Observable<List<BloodSugarMeasurement>>

    @Query("""
      SELECT * FROM BloodSugarMeasurements
      WHERE patientUuid == :patientUuid AND deletedAt IS NULL
      ORDER BY recordedAt DESC
    """)
    fun allBloodSugars(patientUuid: UUID): Observable<List<BloodSugarMeasurement>>

    @Query("""
      SELECT * FROM BloodSugarMeasurements
      WHERE patientUuid == :patientUuid AND deletedAt IS NULL
      ORDER BY recordedAt DESC
    """)
    fun allBloodSugarsDataSource(patientUuid: UUID): DataSource.Factory<Int, BloodSugarMeasurement>

    @Query("SELECT * FROM BloodSugarMeasurements WHERE syncStatus = :status")
    fun withSyncStatus(status: SyncStatus): Flowable<List<BloodSugarMeasurement>>

    @Query("UPDATE BloodSugarMeasurements SET syncStatus = :newStatus WHERE syncStatus = :oldStatus")
    fun updateSyncStatus(oldStatus: SyncStatus, newStatus: SyncStatus)

    @Query("UPDATE BloodSugarMeasurements SET syncStatus = :newStatus WHERE uuid IN (:uuids)")
    fun updateSyncStatus(uuids: List<UUID>, newStatus: SyncStatus)

    @Query("SELECT * FROM BloodSugarMeasurements WHERE uuid = :uuid LIMIT 1")
    fun getOne(uuid: UUID): BloodSugarMeasurement?

    @Query("SELECT COUNT(uuid) FROM BloodSugarMeasurements")
    fun count(): Flowable<Int>

    @Query("SELECT COUNT(uuid) FROM BloodSugarMeasurements WHERE syncStatus = :syncStatus")
    fun count(syncStatus: SyncStatus): Int

    @Query("""
      SELECT COUNT(uuid)
      FROM bloodsugarmeasurements
      WHERE patientUuid = :patientUuid AND deletedAt IS NULL
    """)
    fun recordedBloodSugarsCountForPatient(patientUuid: UUID): Observable<Int>

    @Query("""
      SELECT COUNT(uuid)
      FROM bloodsugarmeasurements
      WHERE patientUuid = :patientUuid AND deletedAt IS NULL
    """)
    fun recordedBloodSugarsCountForPatientImmediate(patientUuid: UUID): Int

    @Query("""
        SELECT (
            CASE
                WHEN (COUNT(uuid) > 0) THEN 1
                ELSE 0
            END
        )
        FROM bloodsugarmeasurements
        WHERE updatedAt > :instantToCompare AND syncStatus = :pendingStatus AND patientUuid = :patientUuid
    """)
    fun haveBloodSugarsForPatientChangedSince(
        patientUuid: UUID,
        instantToCompare: Instant,
        pendingStatus: SyncStatus
    ): Boolean

    @Query("DELETE FROM BloodSugarMeasurements")
    fun clear()
  }
}
