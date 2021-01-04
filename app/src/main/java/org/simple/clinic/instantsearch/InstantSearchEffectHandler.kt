package org.simple.clinic.instantsearch

import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import dagger.Lazy
import io.reactivex.ObservableTransformer
import org.simple.clinic.facility.Facility
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.util.scheduler.SchedulersProvider

class InstantSearchEffectHandler @AssistedInject constructor(
    private val currentFacility: Lazy<Facility>,
    private val patientRepository: PatientRepository,
    private val instantSearchValidator: InstantSearchValidator,
    private val instantSearchConfig: InstantSearchConfig,
    private val schedulers: SchedulersProvider,
    @Assisted private val uiActions: InstantSearchUiActions
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(uiActions: InstantSearchUiActions): InstantSearchEffectHandler
  }

  fun build(): ObservableTransformer<InstantSearchEffect, InstantSearchEvent> = RxMobius
      .subtypeEffectHandler<InstantSearchEffect, InstantSearchEvent>()
      .addTransformer(LoadCurrentFacility::class.java, loadCurrentFacility())
      .addTransformer(LoadAllPatients::class.java, loadAllPatients())
      .addTransformer(SearchWithCriteria::class.java, searchWithCriteria())
      .addConsumer(ShowPatientSearchResults::class.java, { uiActions.showPatientsSearchResults(it.patients, it.facility) }, schedulers.ui())
      .addTransformer(ValidateSearchQuery::class.java, validateSearchQuery())
      .build()

  private fun validateSearchQuery(): ObservableTransformer<ValidateSearchQuery, InstantSearchEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(schedulers.computation())
          .map { instantSearchValidator.validate(
              searchQuery = it.searchQuery,
              minLengthForSearchQuery = instantSearchConfig.minLengthOfSearchQuery
          ) }
          .map(::SearchQueryValidated)
    }
  }

  private fun searchWithCriteria(): ObservableTransformer<SearchWithCriteria, InstantSearchEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(schedulers.io())
          .map { patientRepository.search2(it.criteria, it.facility.uuid) }
          .map(::SearchResultsLoaded)
    }
  }

  private fun loadAllPatients(): ObservableTransformer<LoadAllPatients, InstantSearchEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(schedulers.io())
          .map { patientRepository.allPatientsInFacility(it.facility) }
          .map(::AllPatientsLoaded)
    }
  }

  private fun loadCurrentFacility(): ObservableTransformer<LoadCurrentFacility, InstantSearchEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(schedulers.io())
          .map { currentFacility.get() }
          .map(::CurrentFacilityLoaded)
    }
  }
}
