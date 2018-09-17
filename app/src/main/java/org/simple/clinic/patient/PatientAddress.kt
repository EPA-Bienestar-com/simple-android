package org.simple.clinic.patient

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.PrimaryKey
import android.arch.persistence.room.Query
import io.reactivex.Flowable
import org.simple.clinic.patient.sync.PatientAddressPayload
import org.threeten.bp.Instant
import java.util.UUID

@Entity
data class PatientAddress(
    @PrimaryKey
    val uuid: UUID,

    val colonyOrVillage: String?,

    val district: String,

    val state: String,

    // TODO: Don't use India as the country for everyone!
    val country: String? = "India",

    val createdAt: Instant,

    val updatedAt: Instant
) {

  fun toPayload(): PatientAddressPayload {
    return PatientAddressPayload(
        uuid = uuid,
        colonyOrVillage = colonyOrVillage,
        district = district,
        state = state,
        country = country,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
  }

  @Dao
  interface RoomDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun save(address: PatientAddress)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun save(address: List<PatientAddress>)

    @Query("SELECT * FROM patientaddress WHERE uuid = :uuid")
    fun getOne(uuid: UUID): PatientAddress?

    @Query("SELECT * FROM patientaddress WHERE uuid = :uuid")
    fun address(uuid: UUID): Flowable<List<PatientAddress>>

    @Query("DELETE FROM patientaddress")
    fun clear()

    @Query("SELECT COUNT(uuid) FROM PatientAddress")
    fun count(): Int
  }
}
