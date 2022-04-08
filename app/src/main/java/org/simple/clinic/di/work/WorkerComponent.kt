package org.simple.clinic.di.work

import androidx.work.WorkerParameters
import dagger.BindsInstance
import dagger.Subcomponent

@Subcomponent
interface WorkerComponent {

  @Subcomponent.Factory
  interface Factory {
    fun create(
        @BindsInstance params: WorkerParameters
    ): WorkerComponent
  }
}
