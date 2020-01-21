package org.simple.clinic.summary.bloodpressures.newbpsummary

import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.util.scheduler.SchedulersProvider

class NewBloodPressureSummaryViewEffectHandler @AssistedInject constructor(
    private val bloodPressureRepository: BloodPressureRepository,
    private val schedulersProvider: SchedulersProvider,
    @Assisted private val uiActions: NewBloodPressureSummaryViewUiActions
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(uiActions: NewBloodPressureSummaryViewUiActions): NewBloodPressureSummaryViewEffectHandler
  }

  fun build(): ObservableTransformer<NewBloodPressureSummaryViewEffect, NewBloodPressureSummaryViewEvent> {
    return RxMobius
        .subtypeEffectHandler<NewBloodPressureSummaryViewEffect, NewBloodPressureSummaryViewEvent>()
        .addTransformer(LoadBloodPressures::class.java, loadBloodPressureHistory(schedulersProvider.io()))
        .addTransformer(LoadBloodPressuresCount::class.java, loadBloodPressuresCount(schedulersProvider.io()))
        .addConsumer(OpenBloodPressureEntrySheet::class.java, { uiActions.openBloodPressureEntrySheet(it.patientUuid) }, schedulersProvider.ui())
        .build()
  }

  private fun loadBloodPressureHistory(
      scheduler: Scheduler
  ): ObservableTransformer<LoadBloodPressures, NewBloodPressureSummaryViewEvent> {
    return ObservableTransformer { effect ->
      effect
          .switchMap {
            bloodPressureRepository
                .newestMeasurementsForPatient(it.patientUuid, it.numberOfBpsToDisplay)
                .subscribeOn(scheduler)
          }
          .map(::BloodPressuresLoaded)
    }
  }

  private fun loadBloodPressuresCount(
      scheduler: Scheduler
  ): ObservableTransformer<LoadBloodPressuresCount, NewBloodPressureSummaryViewEvent> {
    return ObservableTransformer { effect ->
      effect
          .switchMap {
            bloodPressureRepository
                .bloodPressureCount(it.patientUuid)
                .subscribeOn(scheduler)
          }
          .map(::BloodPressuresCountLoaded)
    }
  }
}
