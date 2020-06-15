package org.simple.clinic.facility.change.confirm

import com.f2prateek.rx.preferences2.Preference
import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import dagger.Lazy
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.reports.ReportsRepository
import org.simple.clinic.reports.ReportsSync
import org.simple.clinic.user.User
import org.simple.clinic.util.scheduler.SchedulersProvider
import javax.inject.Named

class ConfirmFacilityChangeEffectHandler @AssistedInject constructor(
    private val facilityRepository: FacilityRepository,
    private val reportsRepository: ReportsRepository,
    private val reportsSync: ReportsSync,
    private val schedulersProvider: SchedulersProvider,
    private val currentUser: Lazy<User>,
    @Assisted private val uiActions: ConfirmFacilityChangeUiActions,
    @Named("is_facility_switched") private val isFacilitySwitchedPreference: Preference<Boolean>
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(uiActions: ConfirmFacilityChangeUiActions): ConfirmFacilityChangeEffectHandler
  }

  fun build(): ObservableTransformer<ConfirmFacilityChangeEffect, ConfirmFacilityChangeEvent> {
    return RxMobius
        .subtypeEffectHandler<ConfirmFacilityChangeEffect, ConfirmFacilityChangeEvent>()
        .addTransformer(ChangeFacilityEffect::class.java, changeFacility(schedulersProvider.io()))
        .addAction(CloseSheet::class.java, { uiActions.closeSheet() }, schedulersProvider.ui())
        .build()
  }

  private fun changeFacility(
      io: Scheduler
  ): ObservableTransformer<ChangeFacilityEffect, ConfirmFacilityChangeEvent> {
    return ObservableTransformer { changeFacilityStream ->
      changeFacilityStream
          .map { it.selectedFacility }
          .switchMapSingle {
            val user = currentUser.get()
            facilityRepository
                .setCurrentFacility(user, it)
                .subscribeOn(io)
                .toSingleDefault(it)
          }
          .doOnNext { isFacilitySwitchedPreference.set(true) }
          .doOnNext { clearAndSyncReports(io) }
          .map { FacilityChanged }
    }
  }

  private fun clearAndSyncReports(scheduler: Scheduler) {
    reportsRepository
        .deleteReports()
        .andThen(reportsSync.sync().onErrorComplete())
        .subscribeOn(scheduler)
        .subscribe()
  }
}
