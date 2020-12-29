package org.simple.clinic.allpatientsinfacility

import com.spotify.mobius.rx2.RxMobius
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import org.simple.clinic.facility.Facility
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.PatientSearchResult
import org.simple.clinic.util.scheduler.SchedulersProvider

object AllPatientsInFacilityEffectHandler {
  fun createEffectHandler(
      facilityRepository: FacilityRepository,
      patientRepository: PatientRepository,
      schedulersProvider: SchedulersProvider
  ): ObservableTransformer<AllPatientsInFacilityEffect, AllPatientsInFacilityEvent> {
    return RxMobius
        .subtypeEffectHandler<AllPatientsInFacilityEffect, AllPatientsInFacilityEvent>()
        .addTransformer(FetchFacilityEffect::class.java, fetchFacilityEffectHandler(facilityRepository, schedulersProvider))
        .addTransformer(FetchPatientsEffect::class.java, fetchPatientsEffectHandler(patientRepository, schedulersProvider))
        .build()
  }

  private fun fetchFacilityEffectHandler(
      facilityRepository: FacilityRepository,
      schedulersProvider: SchedulersProvider
  ): (Observable<FetchFacilityEffect>) -> Observable<AllPatientsInFacilityEvent> {
    return {
      facilityRepository
          .currentFacility()
          .subscribeOn(schedulersProvider.io())
          .map(::FacilityFetchedEvent)
    }
  }

  private fun fetchPatientsEffectHandler(
      patientRepository: PatientRepository,
      schedulersProvider: SchedulersProvider
  ): ObservableTransformer<FetchPatientsEffect, AllPatientsInFacilityEvent> {
    return ObservableTransformer { fetchPatients ->
      fetchPatients
          .map { it.facility }
          .switchMap { facility -> loadSearchResultsFromRepository(patientRepository, facility, schedulersProvider) }
          .map(::mapSearchResultsToUiStates)
          .map { patients -> if (patients.isEmpty()) NoPatientsInFacilityEvent else HasPatientsInFacilityEvent(patients) }
    }
  }

  private fun loadSearchResultsFromRepository(
      patientRepository: PatientRepository,
      facility: Facility,
      schedulersProvider: SchedulersProvider
  ): Observable<List<PatientSearchResult>> {
    return patientRepository
        .allPatientsInFacility_Old(facility)
        .subscribeOn(schedulersProvider.io())
  }

  private fun mapSearchResultsToUiStates(
      searchResults: List<PatientSearchResult>
  ): List<PatientSearchResultUiState> {
    return searchResults.map(::PatientSearchResultUiState)
  }
}
