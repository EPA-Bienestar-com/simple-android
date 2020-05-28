package org.simple.clinic.overdue

import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.reactivex.Flowable
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.util.room.SafeEnumTypeAdapter
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.UUID

@Entity(tableName = "Appointment")
@Parcelize
data class Appointment(
    @PrimaryKey val uuid: UUID,
    val patientUuid: UUID,
    val facilityUuid: UUID,
    val scheduledDate: LocalDate,
    val status: Status,
    val cancelReason: AppointmentCancelReason?,
    val remindOn: LocalDate?,
    val agreedToVisit: Boolean?,
    val appointmentType: AppointmentType,
    val syncStatus: SyncStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val creationFacilityUuid: UUID?
) : Parcelable {

  fun wasCancelledBecauseOfInvalidPhoneNumber(): Boolean = status == Status.Cancelled && cancelReason == AppointmentCancelReason.InvalidPhoneNumber

  sealed class Status : Parcelable {

    @Parcelize
    object Scheduled : Status()

    @Parcelize
    object Cancelled : Status()

    @Parcelize
    object Visited : Status()

    @Parcelize
    data class Unknown(val actualValue: String) : Status()

    object TypeAdapter : SafeEnumTypeAdapter<Status>(
        knownMappings = mapOf(
            Scheduled to "scheduled",
            Cancelled to "cancelled",
            Visited to "visited"
        ),
        unknownStringToEnumConverter = { Unknown(it) },
        unknownEnumToStringConverter = { (it as Unknown).actualValue }
    )

    class RoomTypeConverter {
      @TypeConverter
      fun toEnum(value: String?): Status? = TypeAdapter.toEnum(value)

      @TypeConverter
      fun fromEnum(reason: Status?): String? = TypeAdapter.fromEnum(reason)
    }

    class MoshiTypeConverter {
      @FromJson
      fun toEnum(value: String?): Status? = TypeAdapter.toEnum(value)

      @ToJson
      fun fromEnum(reason: Status?): String? = TypeAdapter.fromEnum(reason)
    }

    companion object {
      @VisibleForTesting
      fun random(): Status = TypeAdapter.knownMappings.keys.shuffled().first()
    }
  }

  sealed class AppointmentType : Parcelable {

    @Parcelize
    object Manual : AppointmentType()

    @Parcelize
    object Automatic : AppointmentType()

    @Parcelize
    data class Unknown(val actual: String) : AppointmentType()

    object TypeAdapter : SafeEnumTypeAdapter<AppointmentType>(
        knownMappings = mapOf(
            Manual to "manual",
            Automatic to "automatic"
        ),
        unknownStringToEnumConverter = { Unknown(it) },
        unknownEnumToStringConverter = { (it as Unknown).actual }
    )

    class RoomTypeConverter {

      @TypeConverter
      fun toEnum(value: String?) = TypeAdapter.toEnum(value)

      @TypeConverter
      fun fromEnum(enum: AppointmentType?) = TypeAdapter.fromEnum(enum)
    }

    class MoshiTypeAdapter {

      @FromJson
      fun toEnum(value: String?) = TypeAdapter.toEnum(value)

      @ToJson
      fun fromEnum(enum: AppointmentType) = TypeAdapter.fromEnum(enum)
    }

    companion object {
      @VisibleForTesting
      fun random() = AppointmentType.TypeAdapter.knownMappings.keys.shuffled().first()
    }
  }

  @Dao
  interface RoomDao {

    @Query("SELECT * FROM Appointment WHERE syncStatus = :status")
    fun recordsWithSyncStatus(status: SyncStatus): Flowable<List<Appointment>>

    @Query("UPDATE Appointment SET syncStatus = :to WHERE syncStatus = :from")
    fun updateSyncStatus(from: SyncStatus, to: SyncStatus)

    @Query("UPDATE Appointment SET syncStatus = :to WHERE uuid IN (:ids)")
    fun updateSyncStatus(ids: List<UUID>, to: SyncStatus)

    @Query("SELECT * FROM Appointment WHERE uuid = :id")
    fun getOne(id: UUID): Appointment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(appointments: List<Appointment>)

    @Query("""
      SELECT * FROM Appointment
      WHERE patientUuid = :patientUuid
      ORDER BY createdAt DESC
      LIMIT 1
    """)
    fun lastCreatedAppointmentForPatient(patientUuid: UUID): Appointment?

    @Query("SELECT COUNT(uuid) FROM Appointment")
    fun count(): Flowable<Int>

    @Query("SELECT COUNT(uuid) FROM Appointment WHERE syncStatus = :syncStatus")
    fun count(syncStatus: SyncStatus): Int

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

    @Query("""
      UPDATE Appointment SET status = :updatedStatus, syncStatus = :newSyncStatus, updatedAt = :newUpdatedAt
      WHERE patientUuid = :patientUuid AND status = :scheduledStatus AND createdAt < :createdBefore
    """)
    fun markAsVisited(
        patientUuid: UUID,
        updatedStatus: Status,
        scheduledStatus: Status,
        newSyncStatus: SyncStatus,
        newUpdatedAt: Instant,
        createdBefore: Instant
    )
  }
}
