package org.simple.clinic.sync

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import org.simple.clinic.patient.SyncStatus
import java.util.UUID

/**
 * @param T Type of local data model.
 * @param P Type of payload for the local model.
 */
interface SynceableRepository<T, P> {

  fun save(records: List<T>): Completable

  fun recordsWithSyncStatus(syncStatus: SyncStatus): Single<List<T>>

  fun setSyncStatus(from: SyncStatus, to: SyncStatus): Completable

  fun setSyncStatus(ids: List<UUID>, to: SyncStatus): Completable

  fun mergeWithLocalData(payloads: List<P>): Completable

  fun recordCount(): Observable<Int>

  fun pendingSyncRecordCount(): Int
}
