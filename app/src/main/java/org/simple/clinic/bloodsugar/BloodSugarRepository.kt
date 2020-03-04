package org.simple.clinic.bloodsugar

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.toObservable
import org.simple.clinic.bloodsugar.sync.BloodSugarMeasurementPayload
import org.simple.clinic.di.AppScope
import org.simple.clinic.facility.Facility
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.SyncStatus.DONE
import org.simple.clinic.patient.SyncStatus.PENDING
import org.simple.clinic.patient.canBeOverriddenByServerCopy
import org.simple.clinic.storage.Timestamps
import org.simple.clinic.sync.SynceableRepository
import org.simple.clinic.user.User
import org.simple.clinic.util.UtcClock
import org.threeten.bp.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

@AppScope
class BloodSugarRepository @Inject constructor(
    val dao: BloodSugarMeasurement.RoomDao,
    val utcClock: UtcClock,
    @Named("is_hba1c_enabled") val isHbA1cEnabled: Boolean
) : SynceableRepository<BloodSugarMeasurement, BloodSugarMeasurementPayload> {

  fun saveMeasurement(
      reading: BloodSugarReading,
      patientUuid: UUID,
      loggedInUser: User,
      facility: Facility,
      recordedAt: Instant,
      uuid: UUID = UUID.randomUUID()
  ): Single<BloodSugarMeasurement> {
    return Single
        .just(
            BloodSugarMeasurement(
                uuid = uuid,
                reading = reading,
                recordedAt = recordedAt,
                patientUuid = patientUuid,
                userUuid = loggedInUser.uuid,
                facilityUuid = facility.uuid,
                timestamps = Timestamps.create(utcClock),
                syncStatus = PENDING
            )
        )
        .flatMap {
          Completable
              .fromAction { dao.save(listOf(it)) }
              .toSingleDefault(it)
        }
  }

  fun latestMeasurements(patientUuid: UUID, limit: Int): Observable<List<BloodSugarMeasurement>> {
    return dao.latestMeasurements(patientUuid, limit)
  }

  fun allBloodSugars(patientUuid: UUID): Observable<List<BloodSugarMeasurement>> {
    return dao.allBloodSugars(patientUuid)
  }

  fun bloodSugarsCount(patientUuid: UUID): Observable<Int> = dao.recordedBloodSugarsCountForPatient(patientUuid)

  override fun save(records: List<BloodSugarMeasurement>): Completable =
      Completable.fromAction { dao.save(records) }

  override fun recordsWithSyncStatus(syncStatus: SyncStatus): Single<List<BloodSugarMeasurement>> {
    return if (isHbA1cEnabled) {
      dao.withSyncStatus(syncStatus).firstOrError()
    } else {
      dao.withSyncStatus(syncStatus)
          .map { measurements ->
            measurements.filter { measurement ->
              measurement.reading.type != HbA1c
            }
          }
          .firstOrError()
    }
  }

  override fun setSyncStatus(from: SyncStatus, to: SyncStatus): Completable =
      Completable.fromAction {
        dao.updateSyncStatus(oldStatus = from, newStatus = to)
      }

  override fun setSyncStatus(ids: List<UUID>, to: SyncStatus): Completable {
    if (ids.isEmpty()) {
      throw AssertionError()
    }
    return Completable.fromAction {
      dao.updateSyncStatus(uuids = ids, newStatus = to)
    }
  }

  override fun mergeWithLocalData(payloads: List<BloodSugarMeasurementPayload>): Completable {
    return payloads
        .toObservable()
        .filter { payload ->
          val localCopy = dao.getOne(payload.uuid)
          localCopy?.syncStatus.canBeOverriddenByServerCopy()
        }
        .map { it.toDatabaseModel(DONE) }
        .toList()
        .flatMapCompletable { Completable.fromAction { dao.save(it) } }
  }

  override fun recordCount(): Observable<Int> =
      dao.count().toObservable()

  override fun pendingSyncRecordCount(): Observable<Int> =
      dao.count(PENDING).toObservable()

  fun measurement(bloodSugarMeasurementUuid: UUID): BloodSugarMeasurement? =
      dao.getOne(bloodSugarMeasurementUuid)

  fun updateMeasurement(measurement: BloodSugarMeasurement) {
    val updatedMeasurement = measurement.copy(
        timestamps = measurement.timestamps.copy(
            updatedAt = Instant.now(utcClock)
        ),
        syncStatus = PENDING
    )

    dao.save(listOf(updatedMeasurement))
  }

  fun markBloodSugarAsDeleted(bloodSugarMeasurement: BloodSugarMeasurement) {
    val deletedBloodSugarMeasurement = bloodSugarMeasurement.copy(
        timestamps = bloodSugarMeasurement.timestamps.delete(utcClock),
        syncStatus = PENDING
    )
    dao.save(listOf(deletedBloodSugarMeasurement))
  }
}
