package org.simple.clinic.registration.pin

import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.ObservableTransformer
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.scheduler.SchedulersProvider

class RegistrationPinEffectHandler @AssistedInject constructor(
    private val userSession: UserSession,
    private val schedulers: SchedulersProvider,
    @Assisted private val uiActions: RegistrationPinUiActions
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(uiActions: RegistrationPinUiActions): RegistrationPinEffectHandler
  }

  fun build(): ObservableTransformer<RegistrationPinEffect, RegistrationPinEvent> {
    return RxMobius.subtypeEffectHandler<RegistrationPinEffect, RegistrationPinEvent>()
        .addTransformer(SaveCurrentOngoingEntry::class.java, saveCurrentOngoingEntry())
        .addConsumer(ProceedToConfirmPin::class.java, { uiActions.openRegistrationConfirmPinScreen(it.entry) }, schedulers.ui())
        .build()
  }

  private fun saveCurrentOngoingEntry(): ObservableTransformer<SaveCurrentOngoingEntry, RegistrationPinEvent> {
    return ObservableTransformer { effects ->
      effects
          .doOnNext { userSession.saveOngoingRegistrationEntry(it.entry) }
          .map { CurrentOngoingEntrySaved }
    }
  }
}
