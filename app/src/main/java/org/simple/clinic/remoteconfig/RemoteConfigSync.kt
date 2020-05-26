package org.simple.clinic.remoteconfig

import io.reactivex.Completable
import io.reactivex.Single
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.sync.BatchSize
import org.simple.clinic.sync.ModelSync
import org.simple.clinic.sync.SyncConfig
import org.simple.clinic.sync.SyncGroup
import org.simple.clinic.sync.SyncInterval
import javax.inject.Inject

class RemoteConfigSync @Inject constructor(
    private val crashReporter: CrashReporter,
    private val remoteConfigService: RemoteConfigService
) : ModelSync {

  override val name: String = "Remote Config"

  override fun sync(): Completable = pull()

  override fun push(): Completable = Completable.complete()

  override fun pull(): Completable {
    return remoteConfigService.update()
  }

  override fun syncConfig(): Single<SyncConfig> {
    return Single.just(SyncConfig(
        syncInterval = SyncInterval.FREQUENT,
        batchSize = BatchSize.SMALL,
        syncGroup = SyncGroup.FREQUENT))
  }
}
