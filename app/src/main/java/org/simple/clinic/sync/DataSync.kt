package org.simple.clinic.sync

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.simple.clinic.di.AppScope
import org.simple.clinic.platform.analytics.Analytics
import org.simple.clinic.platform.analytics.SyncAnalyticsEvent.Completed
import org.simple.clinic.platform.analytics.SyncAnalyticsEvent.Failed
import org.simple.clinic.platform.analytics.SyncAnalyticsEvent.Started
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.util.ErrorResolver
import org.simple.clinic.util.ResolvedError
import org.simple.clinic.util.ResolvedError.NetworkRelated
import org.simple.clinic.util.ResolvedError.ServerError
import org.simple.clinic.util.ResolvedError.Unauthenticated
import org.simple.clinic.util.ResolvedError.Unexpected
import org.simple.clinic.util.exhaustive
import org.simple.clinic.util.scheduler.SchedulersProvider
import timber.log.Timber
import javax.inject.Inject

@AppScope
class DataSync @Inject constructor(
    private val modelSyncs: ArrayList<ModelSync>,
    private val crashReporter: CrashReporter,
    private val schedulersProvider: SchedulersProvider
) {

  private val syncProgress = PublishSubject.create<SyncGroupResult>()

  private val syncErrors = PublishSubject.create<ResolvedError>()

  fun syncTheWorld(): Completable {
    val syncAllGroups = SyncGroup
        .values()
        .map(this::sync)

    return Completable.merge(syncAllGroups)
  }

  fun sync(syncGroup: SyncGroup): Completable {
    return Observable
        .fromIterable(modelSyncs)
        .flatMapSingle { modelSync ->
          modelSync
              .syncConfig()
              .map { config -> config to modelSync }
        }
        .filter { (config, _) -> config.syncGroup == syncGroup }
        .map { (_, modelSync) ->
          modelSync
              .sync()
              .doOnSubscribe { Analytics.reportSyncEvent(modelSync.name, Started) }
              .doOnComplete { Analytics.reportSyncEvent(modelSync.name, Completed) }
              .doOnError { Analytics.reportSyncEvent(modelSync.name, Failed) }
        }
        .toList()
        .flatMapCompletable { runAndSwallowErrors(it, syncGroup) }
        .doOnSubscribe { syncProgress.onNext(SyncGroupResult(syncGroup, SyncProgress.SYNCING)) }
  }

  fun fireAndForgetSync(syncGroup: SyncGroup) {
    sync(syncGroup)
        .subscribeOn(schedulersProvider.io())
        .subscribe()
  }

  private fun runAndSwallowErrors(completables: List<Completable>, syncGroup: SyncGroup): Completable {
    return Completable
        .mergeDelayError(completables)
        .doOnComplete { syncProgress.onNext(SyncGroupResult(syncGroup, SyncProgress.SUCCESS)) }
        .doOnError { syncProgress.onNext(SyncGroupResult(syncGroup, SyncProgress.FAILURE)) }
        .doOnError(::logError)
        .onErrorComplete()
  }

  private fun logError(cause: Throwable) {
    val resolvedError = ErrorResolver.resolve(cause)
    syncErrors.onNext(resolvedError)

    when (resolvedError) {
      is Unexpected, is ServerError -> {
        Timber.i("(breadcrumb) Reporting to sentry. Error: $cause. Resolved error: $resolvedError")
        crashReporter.report(resolvedError.actualCause)
        Timber.e(resolvedError.actualCause)
      }
      is NetworkRelated, is Unauthenticated -> Timber.e(cause)
    }.exhaustive()
  }

  fun streamSyncResults(): Observable<SyncGroupResult> = syncProgress

  fun streamSyncErrors(): Observable<ResolvedError> = syncErrors

  data class SyncGroupResult(val syncGroup: SyncGroup, val syncProgress: SyncProgress)

}
