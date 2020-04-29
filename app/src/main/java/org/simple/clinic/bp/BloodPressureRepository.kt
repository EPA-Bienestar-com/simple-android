package org.simple.clinic.bp

import androidx.paging.DataSource
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.toObservable
import org.simple.clinic.bp.sync.BloodPressureMeasurementPayload
import org.simple.clinic.di.AppScope
import org.simple.clinic.facility.Facility
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.canBeOverriddenByServerCopy
import org.simple.clinic.sync.SynceableRepository
import org.simple.clinic.user.User
import org.simple.clinic.util.UtcClock
import org.threeten.bp.Instant
import java.util.UUID
import javax.inject.Inject

@AppScope
class BloodPressureRepository @Inject constructor(
    private val dao: BloodPressureMeasurement.RoomDao,
    private val utcClock: UtcClock
) : SynceableRepository<BloodPressureMeasurement, BloodPressureMeasurementPayload> {

  @Deprecated(
      message = "Use the method with the reading instead",
      replaceWith = ReplaceWith(
          expression = "saveMeasurement(patientUuid, BloodPressureReading(systolic, diastolic), loggedInUser, currentFacility, recordedAt)",
          imports = ["org.simple.clinic.bp.BloodPressureReading"]
      )
  )
  fun saveMeasurement(
      patientUuid: UUID,
      systolic: Int,
      diastolic: Int,
      loggedInUser: User,
      currentFacility: Facility,
      recordedAt: Instant = Instant.now(utcClock)
  ): Single<BloodPressureMeasurement> {
    return saveMeasurement(patientUuid, BloodPressureReading(systolic, diastolic), loggedInUser, currentFacility, recordedAt)
  }

  fun saveMeasurement(
      patientUuid: UUID,
      reading: BloodPressureReading,
      loggedInUser: User,
      currentFacility: Facility,
      recordedAt: Instant = Instant.now(utcClock)
  ): Single<BloodPressureMeasurement> {
    if (reading.systolic < 0 || reading.diastolic < 0) {
      throw AssertionError("Cannot have negative BP readings.")
    }

    val now = Instant.now(utcClock)
    return Single
        .just(
            BloodPressureMeasurement(
                uuid = UUID.randomUUID(),
                reading = reading,
                syncStatus = SyncStatus.PENDING,
                userUuid = loggedInUser.uuid,
                facilityUuid = currentFacility.uuid,
                patientUuid = patientUuid,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                recordedAt = recordedAt
            )
        )
        .flatMap {
          save(listOf(it)).toSingleDefault(it)
        }
  }

  override fun save(records: List<BloodPressureMeasurement>): Completable {
    return Completable.fromAction { dao.save(records) }
  }

  fun updateMeasurement(measurement: BloodPressureMeasurement): Completable {
    return Completable.fromAction {
      val updatedMeasurement = measurement.copy(
          updatedAt = Instant.now(utcClock),
          syncStatus = SyncStatus.PENDING
      )

      dao.save(listOf(updatedMeasurement))
    }
  }

  override fun recordsWithSyncStatus(syncStatus: SyncStatus): Single<List<BloodPressureMeasurement>> {
    return dao
        .withSyncStatus(syncStatus)
        .firstOrError()
  }

  override fun setSyncStatus(from: SyncStatus, to: SyncStatus): Completable {
    return Completable.fromAction {
      dao.updateSyncStatus(oldStatus = from, newStatus = to)
    }
  }

  override fun setSyncStatus(ids: List<UUID>, to: SyncStatus): Completable {
    if (ids.isEmpty()) {
      throw AssertionError()
    }
    return Completable.fromAction {
      dao.updateSyncStatus(uuids = ids, newStatus = to)
    }
  }

  override fun mergeWithLocalData(payloads: List<BloodPressureMeasurementPayload>): Completable {
    return payloads
        .toObservable()
        .filter { payload ->
          val localCopy = dao.getOne(payload.uuid)
          localCopy?.syncStatus.canBeOverriddenByServerCopy()
        }
        .map { it.toDatabaseModel(SyncStatus.DONE) }
        .toList()
        .flatMapCompletable { Completable.fromAction { dao.save(it) } }
  }

  override fun recordCount(): Observable<Int> {
    return dao.count().toObservable()
  }

  fun newestMeasurementsForPatient(patientUuid: UUID, limit: Int): Observable<List<BloodPressureMeasurement>> {
    return dao
        .newestMeasurementsForPatient(patientUuid, limit)
        .toObservable()
  }

  fun measurement(uuid: UUID): Observable<BloodPressureMeasurement> = dao.bloodPressure(uuid).toObservable()

  fun markBloodPressureAsDeleted(bloodPressureMeasurement: BloodPressureMeasurement): Completable {
    return Completable.fromAction {
      val now = Instant.now(utcClock)
      val deletedBloodPressureMeasurement = bloodPressureMeasurement.copy(
          updatedAt = now,
          deletedAt = now,
          syncStatus = SyncStatus.PENDING)

      dao.save(listOf(deletedBloodPressureMeasurement))
    }
  }

  fun bloodPressureCountImmediate(patientUuid: UUID): Int = dao.recordedBloodPressureCountForPatientImmediate(patientUuid)

  fun bloodPressureCount(patientUuid: UUID): Observable<Int> = dao.recordedBloodPressureCountForPatient(patientUuid)

  fun allBloodPressures(patientUuid: UUID): Observable<List<BloodPressureMeasurement>> {
    return dao.allBloodPressures(patientUuid)
  }

  fun allBloodPressuresDataSource(patientUuid: UUID): DataSource.Factory<Int, BloodPressureMeasurement> {
    return dao.allBloodPressuresDataSource(patientUuid)
  }

  override fun pendingSyncRecordCount(): Observable<Int> {
    return dao
        .count(SyncStatus.PENDING)
        .toObservable()
  }
}
