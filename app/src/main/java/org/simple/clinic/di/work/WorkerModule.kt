package org.simple.clinic.di.work

import androidx.work.RxWorker
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import org.simple.clinic.overdue.download.OverdueDownloadWorker
import org.simple.clinic.sync.SyncWorker

@Module
abstract class WorkerModule {

  @Binds
  @IntoMap
  @WorkerKey(OverdueDownloadWorker::class)
  abstract fun bindOverdueDownloadWorker(worker: OverdueDownloadWorker): RxWorker

  @Binds
  @IntoMap
  @WorkerKey(SyncWorker::class)
  abstract fun bindSyncWorker(worker: SyncWorker): RxWorker
}
