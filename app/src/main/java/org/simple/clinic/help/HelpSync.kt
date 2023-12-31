package org.simple.clinic.help

import io.reactivex.Completable
import io.reactivex.Single
import org.simple.clinic.sync.ModelSync
import org.simple.clinic.sync.SyncConfig
import org.simple.clinic.sync.SyncConfigType
import org.simple.clinic.sync.SyncConfigType.Type.Frequent
import java.io.IOException
import javax.inject.Inject

class HelpSync @Inject constructor(
    private val syncApi: HelpApi,
    private val syncRepository: HelpRepository,
    @SyncConfigType(Frequent) private val config: SyncConfig
) : ModelSync {

  override val name: String = "Help"

  override val requiresSyncApprovedUser = false

  override fun push() {
    /* Nothing to do here */
  }

  override fun pull() {
    val helpText = syncApi.help().execute().body()!!
    syncRepository.updateHelp(helpText)
  }

  fun pullWithResult(): Single<HelpPullResult> {
    return Completable
        .fromAction { pull() }
        .toSingleDefault(HelpPullResult.Success as HelpPullResult)
        .onErrorReturn { cause ->
          when (cause) {
            is IOException -> HelpPullResult.NetworkError
            else -> HelpPullResult.OtherError
          }
        }
  }
}
