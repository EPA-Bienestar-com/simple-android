package org.simple.clinic.di.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.RxWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import org.simple.clinic.di.AppScope
import org.simple.clinic.platform.crash.CrashReporter
import javax.inject.Inject
import javax.inject.Provider

@AppScope
class DaggerWorkerFactory @Inject constructor(
    private val workerComponent: WorkerComponent.Factory
) : WorkerFactory() {

  override fun createWorker(
      appContext: Context,
      workerClassName: String,
      workerParameters: WorkerParameters
  ) = workerComponent
      .create(workerParameters)
      .run {
        createWorker(workerClassName, workers())
      }

  private fun createWorker(
      workerClassName: String,
      workers: Map<Class<out RxWorker>, Provider<RxWorker>>
  ): ListenableWorker? = try {
    val workerClass = Class.forName(workerClassName).asSubclass(RxWorker::class.java)

    var provider = workers[workerClass]
    if (provider == null) {
      for ((key, value) in workers) {
        if (workerClass.isAssignableFrom(key)) {
          provider = value
          break
        }
      }
    }

    if (provider == null) {
      throw IllegalArgumentException("Missing binding for $workerClassName")
    }

    provider.get()
  } catch (e: Exception) {
    // If we don't find the worker, we will report it and then try default initialisation.
    CrashReporter.report(e)
    null
  }
}
