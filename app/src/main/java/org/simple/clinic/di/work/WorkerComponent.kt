package org.simple.clinic.di.work

import androidx.work.RxWorker
import androidx.work.WorkerParameters
import dagger.BindsInstance
import dagger.Subcomponent
import javax.inject.Provider

@Subcomponent(modules = [WorkerModule::class])
interface WorkerComponent {

  fun workers(): Map<Class<out RxWorker>, Provider<RxWorker>>

  @Subcomponent.Factory
  interface Factory {
    fun create(
        @BindsInstance params: WorkerParameters
    ): WorkerComponent
  }
}
