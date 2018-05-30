package org.resolvetosavelives.red.sync

import com.f2prateek.rx.preferences2.Preference
import com.gojuno.koptional.None
import com.gojuno.koptional.Optional
import com.gojuno.koptional.Some
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.toCompletable
import io.reactivex.schedulers.Schedulers.single
import org.resolvetosavelives.red.newentry.search.PatientRepository
import org.resolvetosavelives.red.newentry.search.SyncStatus
import org.threeten.bp.Instant
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

class PatientSync @Inject constructor(
    private val api: PatientSyncApiV1,
    private val repository: PatientRepository,
    private val configProvider: Single<PatientSyncConfig>,
    @Named("last_pull_timestamp") private val lastPullTimestamp: Preference<Optional<Instant>>
) {

  fun sync(): Completable {
    return Completable.mergeArrayDelayError(push(), pull())
  }

  fun push(): Completable {
    val cachedPendingSyncPatients = repository
        .patientsWithSyncStatus(SyncStatus.PENDING)
        // Converting to an Observable because Single#filter() returns a Maybe.
        // And Maybe#flatMapSingle() throws a NoSuchElementException on completion.
        .toObservable()
        .filter({ it.isNotEmpty() })
        .cache()

    val pendingToInFlight = cachedPendingSyncPatients
        .flatMapCompletable {
          repository.updatePatientsSyncStatus(fromStatus = SyncStatus.PENDING, toStatus = SyncStatus.IN_FLIGHT)
        }

    val networkCall = cachedPendingSyncPatients
        .map { patients -> patients.map { it.toPayload() } }
        .map(::PatientPushRequest)
        .flatMapSingle { request -> api.push(request) }
        .doOnNext { response -> logValidationErrorsIfAny(response) }
        .map { it.validationErrors }
        .map { errors -> errors.map { it.uuid } }
        .flatMapCompletable { patientUuidsWithErrors ->
          repository
              .updatePatientsSyncStatus(fromStatus = SyncStatus.IN_FLIGHT, toStatus = SyncStatus.DONE)
              .andThen(patientUuidsWithErrors.let {
                when {
                  it.isEmpty() -> Completable.complete()
                  else -> repository.updatePatientsSyncStatus(patientUuidsWithErrors, SyncStatus.INVALID)
                }
              })
        }

    return pendingToInFlight.andThen(networkCall)
  }

  private fun logValidationErrorsIfAny(response: PatientPushResponse) {
    if (response.validationErrors.isNotEmpty()) {
      Timber.e("Server sent validation errors for patients: ${response.validationErrors}")
    }
  }

  fun pull(): Completable {
    return configProvider
        .flatMapCompletable { config ->
          lastPullTimestamp.asObservable()
              .take(1)
              .flatMapSingle { lastPullTime ->
                when (lastPullTime) {
                  is Some -> api.pull(recordsToRetrieve = config.batchSize, latestRecordTimestamp = lastPullTime.value)
                  is None -> api.pull(recordsToRetrieve = config.batchSize, isFirstSync = true)
                }
              }
              .flatMap { response ->
                repository.mergeWithLocalData(response.patients)
                    .observeOn(single())
                    .andThen({ lastPullTimestamp.set(Some(response.latestRecordTimestamp)) }.toCompletable())
                    .andThen(Observable.just(response))
              }
              .repeat()
              .takeWhile({ response -> response.patients.size >= config.batchSize })
              .ignoreElements()
        }
  }
}
