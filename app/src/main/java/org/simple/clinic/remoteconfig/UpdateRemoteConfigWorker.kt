package org.simple.clinic.remoteconfig

import android.app.Application
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import io.reactivex.Single
import org.simple.clinic.platform.crash.CrashReporter
import javax.inject.Inject

class UpdateRemoteConfigWorker @Inject constructor(
    context: Application,
    workerParams: WorkerParameters,
    private val remoteConfigService: RemoteConfigService
) : RxWorker(context, workerParams) {

  companion object {
    const val REMOTE_CONFIG_SYNC_WORKER = "remote_config_sync_worker"

    fun createWorkRequest(): OneTimeWorkRequest {
      val constraints = Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .setRequiresBatteryNotLow(true)
          .build()

      return OneTimeWorkRequest
          .Builder(UpdateRemoteConfigWorker::class.java)
          .setConstraints(constraints)
          .build()
    }
  }

  override fun createWork(): Single<Result> {
    return Single
        .create<Result?> {
          remoteConfigService.update()
          Result.success()
        }
        .doOnError {
          CrashReporter.report(it)
          Result.failure()
        }
  }
}
