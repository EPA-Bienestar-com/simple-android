package org.simple.clinic.encounter

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.zipWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.encounter.sync.EncounterPayload
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.SyncStatus.DONE
import org.simple.clinic.patient.SyncStatus.PENDING
import org.simple.clinic.patient.canBeOverriddenByServerCopy
import org.simple.clinic.storage.inTransaction
import org.simple.clinic.sync.SynceableRepository
import java.util.UUID
import javax.inject.Inject

// TODO(vs): 2019-12-05 Delete this file
class EncounterRepository @Inject constructor(
    private val database: AppDatabase
) : SynceableRepository<Encounter, EncounterPayload> {

  override fun save(records: List<Encounter>): Completable {
    return Completable.fromAction { database.encountersDao().save(encounters = records) }
  }

  override fun recordsWithSyncStatus(syncStatus: SyncStatus): Single<List<Encounter>> {
    return database.encountersDao().recordsWithSyncStatus(syncStatus).firstOrError()
  }

  override fun setSyncStatus(from: SyncStatus, to: SyncStatus): Completable {
    return database.encountersDao().updateSyncStatus(from, to)
  }

  override fun setSyncStatus(ids: List<UUID>, to: SyncStatus): Completable {
    return database.encountersDao().updateSyncStatus(ids, to)
  }

  override fun mergeWithLocalData(payloads: List<EncounterPayload>): Completable {
    val payloadObservable = Observable.fromIterable(payloads)
    val encountersCanBeOverridden = payloadObservable
        .flatMap { canEncountersBeOverridden(it) }

    return payloadObservable.zipWith(encountersCanBeOverridden)
        .filter { (_, canBeOverridden) -> canBeOverridden }
        .map { (payload, _) -> payload }
        .map(::payloadToEncounters)
        .toList()
        .flatMapCompletable(::saveMergedEncounters)
  }

  private fun canEncountersBeOverridden(payload: EncounterPayload): Observable<Boolean> {
    return Observable.fromCallable {
      database.encountersDao()
          .getOne(payload.uuid)
          ?.syncStatus.canBeOverriddenByServerCopy()
    }
  }

  private fun payloadToEncounters(payload: EncounterPayload): ObservationsForEncounter {
    val bloodPressures = payload.observations.bloodPressureMeasurements.map { bps ->
      bps.toDatabaseModel(syncStatus = DONE)
    }
    return ObservationsForEncounter(encounter = payload.toDatabaseModel(DONE), bloodPressures = bloodPressures)
  }

  private fun saveMergedEncounters(records: List<ObservationsForEncounter>): Completable {
    return Completable.fromAction {
      val bloodPressures = records.flatMap { it.bloodPressures }
      val encounters = records.map { it.encounter }

      with(database) {
        openHelper.writableDatabase.inTransaction {
          bloodPressureDao().save(bloodPressures)
          encountersDao().save(encounters)
        }
      }
    }
  }

  override fun recordCount(): Observable<Int> {
    return database.encountersDao().recordCount()
  }

  override fun pendingSyncRecordCount(): Observable<Int> {
    return database.encountersDao().recordCount(syncStatus = PENDING)
  }
}
