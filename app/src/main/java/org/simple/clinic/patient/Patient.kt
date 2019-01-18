package org.simple.clinic.patient

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import io.reactivex.Flowable
import org.simple.clinic.storage.DaoWithUpsert
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.UUID


/**
 * [Regex] for stripping patient names and search queries of white spaces and punctuation
 *
 * Currently matches the following characters
 * - Any whitespace
 * - Comma, Hyphen, SemiColon, Colon, Underscore, Apostrophe, Period
 * */
private val spacePunctuationRegex = Regex("[\\s;_\\-:,'\\\\.]")

fun nameToSearchableForm(string: String) = string.replace(spacePunctuationRegex, "")

@Entity(
    foreignKeys = [
      ForeignKey(
          entity = PatientAddress::class,
          parentColumns = ["uuid"],
          childColumns = ["addressUuid"],
          onDelete = ForeignKey.CASCADE,
          onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
      Index("addressUuid")
    ])
data class Patient (
    @PrimaryKey
    val uuid: UUID,

    val addressUuid: UUID,

    val fullName: String,

    val searchableName: String,

    val gender: Gender,

    val dateOfBirth: LocalDate?,

    @Embedded(prefix = "age_")
    val age: Age?,

    val status: PatientStatus,

    val createdAt: Instant,

    val updatedAt: Instant,

    val deletedAt: Instant?,

    val syncStatus: SyncStatus
) {

  @Dao
  abstract class RoomDao : DaoWithUpsert<Patient>() {

    @Query("SELECT * FROM patient")
    abstract fun allPatients(): Flowable<List<Patient>>

    @Query("SELECT * FROM patient WHERE uuid = :uuid")
    abstract fun getOne(uuid: UUID): Patient?

    // Only if Room supported custom adapters, we wouldn't need both getOne() and patient().
    @Query("SELECT * FROM patient WHERE uuid = :uuid")
    abstract fun patient(uuid: UUID): Flowable<List<Patient>>

    fun save(patient: Patient) {
      save(listOf(patient))
    }

    fun save(patients: List<Patient>) {
      upsert(patients)
    }

    @Query("UPDATE patient SET syncStatus = :newStatus WHERE syncStatus = :oldStatus")
    abstract fun updateSyncStatus(oldStatus: SyncStatus, newStatus: SyncStatus)

    @Query("UPDATE patient SET syncStatus = :newStatus WHERE uuid IN (:uuids)")
    abstract fun updateSyncStatus(uuids: List<UUID>, newStatus: SyncStatus)

    @Query("SELECT COUNT(uuid) FROM patient")
    abstract fun patientCount(): Flowable<Int>

    @Query("DELETE FROM patient")
    abstract fun clear()

    @Query("UPDATE patient SET status = :newStatus WHERE uuid = :uuid")
    abstract fun updatePatientStatus(uuid: UUID, newStatus: PatientStatus)

    // Patient can have multiple phone numbers, and Room's support for @Relation annotations doesn't
    // support loading into constructor parameters and needs a settable property. Room does fix
    // this limitation in 2.1.0, but it requires migration to AndroidX. For now, we create a
    // transient query model whose only job is to represent this and process it in memory.
    // TODO: Remove this when we migrate to Room 2.1.0.
    @Query("""
      SELECT P.uuid patient_uuid, P.addressUuid patient_addressUuid, P.fullName patient_fullName, P.searchableName patient_searchableName,
       P.gender patient_gender, P.dateOfBirth patient_dateOfBirth, P.age_value patient_age_value, P.age_updatedAt patient_age_updatedAt,
       P.age_computedDateOfBirth patient_age_computedDateOfBirth, P.status patient_status, P.createdAt patient_createdAt,
       P.updatedAt patient_updatedAt, P.syncStatus patient_syncStatus, PA.uuid addr_uuid, PA.colonyOrVillage addr_colonyOrVillage,
       PA.district addr_district, PA.state addr_state, PA.country addr_country, PA.createdAt addr_createdAt, PA.updatedAt addr_updatedAt,
       PPN.uuid phone_uuid, PPN.patientUuid phone_patientUuid, PPN.number phone_number, PPN.phoneType phone_phoneType, PPN.active phone_active,
       PPN.createdAt phone_createdAt, PPN.updatedAt phone_updatedAt
      FROM Patient P
      INNER JOIN PatientAddress PA ON P.addressUuid == PA.uuid
      LEFT JOIN PatientPhoneNumber PPN ON PPN.patientUuid == P.uuid
      WHERE P.syncStatus == :syncStatus
    """)
    protected abstract fun loadPatientQueryModelsWithSyncStatus(syncStatus: SyncStatus): Flowable<List<PatientQueryModel>>

    fun recordsWithSyncStatus(syncStatus: SyncStatus): Flowable<List<PatientProfile>> {
      return loadPatientQueryModelsWithSyncStatus(syncStatus)
          .map { results ->
            results.asSequence()
                .groupBy { it.patient.uuid }
                .map { (_, patientQueryModels) ->
                  val phoneNumbers = when {
                    // Patient has either no phone numbers or one phone number saved, which is
                    // indicated by whether the Patient phone number in the query model instance is
                    // null or not.
                    patientQueryModels.size == 1 -> {
                      patientQueryModels.first()
                          .phoneNumber
                          ?.let { listOf(it) } ?: emptyList()
                    }
                    patientQueryModels.size > 1 -> patientQueryModels.map { it.phoneNumber!! }
                    else -> throw AssertionError("Patient query models is empty!")
                  }

                  PatientProfile(
                      patient = patientQueryModels.first().patient,
                      address = patientQueryModels.first().address,
                      phoneNumbers = phoneNumbers
                  )
                }.toList()
          }
    }

    protected data class PatientQueryModel(

        @Embedded(prefix = "patient_")
        val patient: Patient,

        @Embedded(prefix = "addr_")
        val address: PatientAddress,

        @Embedded(prefix = "phone_")
        val phoneNumber: PatientPhoneNumber?
    )
  }
}
