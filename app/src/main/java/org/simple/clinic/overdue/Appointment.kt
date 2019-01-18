package org.simple.clinic.overdue

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.squareup.moshi.Json
import io.reactivex.Flowable
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.util.RoomEnumTypeConverter
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.UUID

@Entity(tableName = "Appointment")
data class Appointment(
    @PrimaryKey val uuid: UUID,
    val patientUuid: UUID,
    val facilityUuid: UUID,
    val scheduledDate: LocalDate,
    val status: Status,
    val cancelReason: AppointmentCancelReason?,
    val remindOn: LocalDate?,
    val agreedToVisit: Boolean?,
    val syncStatus: SyncStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?
) {

  enum class Status {

    @Json(name = "scheduled")
    SCHEDULED,

    @Json(name = "cancelled")
    CANCELLED,

    @Json(name = "visited")
    VISITED;

    class RoomTypeConverter : RoomEnumTypeConverter<Status>(Status::class.java)
  }

  @Dao
  interface RoomDao {

    @Query("SELECT * FROM Appointment WHERE syncStatus = :status")
    fun recordsWithSyncStatus(status: SyncStatus): Flowable<List<Appointment>>

    @Query("UPDATE Appointment SET syncStatus = :to WHERE syncStatus = :from")
    fun updateSyncStatus(from: SyncStatus, to: SyncStatus)

    @Query("UPDATE Appointment SET syncStatus = :to WHERE uuid IN (:ids)")
    fun updateSyncStatus(ids: List<UUID>, to: SyncStatus)

    @Query("SELECT * FROM Appointment WHERE uuid = :id LIMIT 1")
    fun getOne(id: UUID): Appointment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(appointments: List<Appointment>)

    @Query("""
      SELECT * FROM Appointment
      WHERE patientUuid = :patientUuid
      ORDER BY createdAt DESC
      LIMIT 1
    """)
    fun lastCreatedAppointmentForPatient(patientUuid: UUID): Flowable<List<Appointment>>

    @Query("SELECT COUNT(uuid) FROM Appointment")
    fun count(): Flowable<Int>

    @Query("""
      UPDATE Appointment
      SET status = :updatedStatus, syncStatus = :newSyncStatus, updatedAt = :newUpdatedAt
      WHERE patientUuid = :patientUuid AND status = :scheduledStatus
    """)
    fun markOlderAppointmentsAsVisited(
        patientUuid: UUID,
        updatedStatus: Status,
        scheduledStatus: Status,
        newSyncStatus: SyncStatus,
        newUpdatedAt: Instant
    )

    @Query("""
      UPDATE Appointment
       SET remindOn = :reminderDate, syncStatus = :newSyncStatus, updatedAt = :newUpdatedAt
       WHERE uuid = :appointmentUUID
    """)
    fun saveRemindDate(
        appointmentUUID: UUID,
        reminderDate: LocalDate,
        newSyncStatus: SyncStatus,
        newUpdatedAt: Instant
    )

    @Query("""
      UPDATE Appointment
      SET remindOn = :reminderDate, agreedToVisit = :agreed, syncStatus = :newSyncStatus, updatedAt = :newUpdatedAt
      WHERE uuid = :appointmentUUID
    """)
    fun markAsAgreedToVisit(
        appointmentUUID: UUID,
        reminderDate: LocalDate,
        agreed: Boolean = true,
        newSyncStatus: SyncStatus,
        newUpdatedAt: Instant
    )

    @Query("""
      UPDATE Appointment
      SET status = :newStatus, syncStatus = :newSyncStatus, updatedAt = :newUpdatedAt
      WHERE uuid = :appointmentUuid
    """)
    fun markAsVisited(
        appointmentUuid: UUID,
        newStatus: Status,
        newSyncStatus: SyncStatus,
        newUpdatedAt: Instant
    )

    @Query("""
      UPDATE Appointment
      SET cancelReason = :cancelReason, status = :newStatus, syncStatus = :newSyncStatus, updatedAt = :newUpdatedAt
      WHERE uuid = :appointmentUuid
    """)
    fun cancelWithReason(
        appointmentUuid: UUID,
        cancelReason: AppointmentCancelReason,
        newStatus: Status,
        newSyncStatus: SyncStatus,
        newUpdatedAt: Instant
    )

    @Query("DELETE FROM Appointment")
    fun clear()
  }
}
