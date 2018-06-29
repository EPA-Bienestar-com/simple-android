package org.simple.clinic.di

import dagger.Component
import org.simple.clinic.DebugClinicApp
import org.simple.clinic.DebugNotificationActionReceiver

@AppScope
@Component(modules = [AppModule::class])
interface DebugAppComponent : AppComponent {

  fun inject(target: DebugClinicApp)
  fun inject(target: DebugNotificationActionReceiver)
}
