package org.simple.clinic.shortcodesearchresult

import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.withLatestFrom
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.plumbing.BaseUiStateProducer
import org.simple.clinic.searchresultsview.PartitionSearchResultsByVisitedFacility
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.scheduler.SchedulersProvider
import org.simple.clinic.util.unwrapJust
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent

class ShortCodeSearchResultStateProducer(
    private val shortCode: String,
    private val patientRepository: PatientRepository,
    private val userSession: UserSession,
    private val facilityRepository: FacilityRepository,
    private val bloodPressureDao: BloodPressureMeasurement.RoomDao,
    private val ui: ShortCodeSearchResultUi,
    private val schedulersProvider: SchedulersProvider
) : BaseUiStateProducer<UiEvent, ShortCodeSearchResultState>() {

  override fun apply(events: Observable<UiEvent>): ObservableSource<ShortCodeSearchResultState> {
    return Observable.merge(
        initialStates(events),
        fetchPatients(events),
        viewPatient(events),
        searchPatient(events)
    )
  }

  private fun initialStates(events: Observable<UiEvent>) = events.ofType<ScreenCreated>()
      .map { ShortCodeSearchResultState.fetchingPatients(shortCode) }

  private fun fetchPatients(events: Observable<UiEvent>): Observable<ShortCodeSearchResultState> {
    val shortCodeSearchResults = events.ofType<ScreenCreated>()
        .flatMap {
          patientRepository
              .searchByShortCode(shortCode)
              .subscribeOn(schedulersProvider.io())
        }
        .share()

    val noMatchingPatient = shortCodeSearchResults
        .filter { it.isEmpty() }
        .withLatestFrom(states) { _, state ->
          state.noMatchingPatients()
        }

    val currentFacilityStream = userSession
        .loggedInUser()
        .unwrapJust()
        .flatMap(facilityRepository::currentFacility)

    val matchingPatients = shortCodeSearchResults
        .filter { it.isNotEmpty() }
        .compose(PartitionSearchResultsByVisitedFacility(bloodPressureDao, currentFacilityStream))
        .withLatestFrom(states) { patientSearchResults, state ->
          state.patientsFetched(patientSearchResults)
        }

    return matchingPatients.mergeWith(noMatchingPatient)
  }

  private fun viewPatient(events: Observable<UiEvent>): Observable<ShortCodeSearchResultState> {
    return events.ofType<ViewPatient>()
        .doOnNext { ui.openPatientSummary(it.patientUuid) }
        .flatMap { Observable.empty<ShortCodeSearchResultState>() }
  }

  private fun searchPatient(events: Observable<UiEvent>): Observable<ShortCodeSearchResultState> {
    return events.ofType<SearchPatient>()
        .doOnNext { ui.openPatientSearch() }
        .flatMap { Observable.empty<ShortCodeSearchResultState>() }
  }
}
