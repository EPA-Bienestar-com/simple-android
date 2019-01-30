package org.simple.clinic.protocol.sync

import com.f2prateek.rx.preferences2.Preference
import io.reactivex.Completable
import io.reactivex.Single
import org.simple.clinic.protocol.ProtocolRepository
import org.simple.clinic.protocol.ProtocolSyncApiV2
import org.simple.clinic.sync.ModelSync
import org.simple.clinic.sync.SyncConfig
import org.simple.clinic.sync.SyncCoordinator
import org.simple.clinic.util.Optional
import javax.inject.Inject
import javax.inject.Named

class ProtocolSync @Inject constructor(
    private val syncCoordinator: SyncCoordinator,
    private val repository: ProtocolRepository,
    private val api: ProtocolSyncApiV2,
    @Named("last_protocol_pull_token") private val lastPullToken: Preference<Optional<String>>,
    @Named("sync_config_daily") private val configProvider: Single<SyncConfig>
) : ModelSync {

  override fun sync(): Completable = pull()

  override fun push(): Completable = Completable.complete()

  override fun pull(): Completable {
    return configProvider
        .map { it.batchSize }
        .flatMapCompletable { batchSize ->
          syncCoordinator.pull(repository, lastPullToken, batchSize) { api.pull(batchSize, it) }
        }
  }

  override fun syncConfig() = configProvider
}
