package org.simple.clinic.sync

import android.app.Application
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import io.reactivex.Single
import javax.inject.Inject

class SyncWorker @Inject constructor(
    context: Application,
    workerParams: WorkerParameters,
    private val dataSync: DataSync
) : RxWorker(context, workerParams) {

  override fun createWork(): Single<Result> {
    return Single.create {
      try {
        dataSync.syncTheWorld()
      } catch (e: Exception) {
        // Individual syncs report their errors internally so we can just
        // ignore this caught error. This is a good place for future
        // improvements like attempting a backoff based retry.
      }

      Result.success()
    }
  }
}
