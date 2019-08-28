package org.simple.clinic.shortcodesearchresult

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.PatientToFacilityId
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.PatientSearchResult
import org.simple.clinic.searchresultsview.PatientSearchResults
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import org.simple.clinic.util.toOptional
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent
import java.util.UUID

class ShortCodeSearchResultStateProducerTest {
  private val uiEventsSubject = PublishSubject.create<UiEvent>()
  private val patientRepository = mock<PatientRepository>()
  private val userSession = mock<UserSession>()
  private val facilityRepository = mock<FacilityRepository>()
  private val bloodPressureDao = mock<BloodPressureMeasurement.RoomDao>()
  private val shortCode = "1234567"
  private val fetchingPatientsState = ShortCodeSearchResultState.fetchingPatients(shortCode)
  private val ui = mock<ShortCodeSearchResultUi>()

  private val loggedInUser = PatientMocker.loggedInUser()
  private val currentFacility = PatientMocker.facility(uuid = UUID.fromString("27a61122-ed11-490a-a3ae-b881840e9842"))

  private val uiStateProducer = ShortCodeSearchResultStateProducer(
      shortCode = shortCode,
      patientRepository = patientRepository,
      userSession = userSession,
      facilityRepository = facilityRepository,
      bloodPressureDao = bloodPressureDao,
      ui = ui,
      schedulersProvider = TrampolineSchedulersProvider()
  )
  lateinit var uiStates: Observable<ShortCodeSearchResultState>

  @Before
  fun setup() {
    whenever(userSession.loggedInUser()).thenReturn(Observable.just(loggedInUser.toOptional()))
    whenever(facilityRepository.currentFacility(loggedInUser)).thenReturn(Observable.just(currentFacility))

    uiStates = uiEventsSubject
        .compose(uiStateProducer)
        .doOnNext { uiStateProducer.states.onNext(it) }
  }

  @Test
  fun `when the screen is created, then patients matching the BP passport number must be fetched`() {
    // given
    val otherFacility = PatientMocker.facility(uuid = UUID.fromString("dca1b165-d7ad-4a93-a645-d657e48afd69"))

    val patientSearchResultsInCurrentFacility = PatientMocker.patientSearchResult(uuid = UUID.fromString("4e0661c6-4376-4aab-8718-4623f1653dcc"))
    val patientSearchResultInOtherFacility = PatientMocker.patientSearchResult(uuid = UUID.fromString("1fe5981a-cdef-4457-bd86-178d49e8cc97"))

    val patientSearchResults = listOf(
        patientSearchResultsInCurrentFacility,
        patientSearchResultInOtherFacility)

    val patientToFacilityIds = listOf(
        PatientToFacilityId(patientSearchResultsInCurrentFacility.uuid, currentFacility.uuid),
        PatientToFacilityId(patientSearchResultInOtherFacility.uuid, otherFacility.uuid)
    )

    whenever(facilityRepository.currentFacility(loggedInUser)).thenReturn(Observable.just(currentFacility))
    whenever(patientRepository.searchByShortCode(shortCode)).thenReturn(Observable.just(patientSearchResults))
    whenever(bloodPressureDao.patientToFacilityIds(listOf(
        patientSearchResultsInCurrentFacility.uuid,
        patientSearchResultInOtherFacility.uuid)))
        .thenReturn(Flowable.just(patientToFacilityIds))

    val testObserver = uiStates.test()

    // when
    uiEventsSubject.onNext(ScreenCreated())

    // then
    val expectedPatientResults = PatientSearchResults(
        visitedCurrentFacility = listOf(patientSearchResultsInCurrentFacility),
        notVisitedCurrentFacility = listOf(patientSearchResultInOtherFacility),
        currentFacility = currentFacility)

    testObserver
        .assertNoErrors()
        .assertValues(fetchingPatientsState, fetchingPatientsState.patientsFetched(expectedPatientResults))
        .assertNotTerminated()
  }

  @Test
  fun `when the screen is created and there are no patients, then don't show any patients`() {
    // given
    val emptyPatientSearchResults = emptyList<PatientSearchResult>()
    whenever(patientRepository.searchByShortCode(shortCode))
        .thenReturn(Observable.just(emptyPatientSearchResults))

    val testObserver = uiStates.test()

    // when
    uiEventsSubject.onNext(ScreenCreated())

    // then
    testObserver
        .assertNoErrors()
        .assertValues(fetchingPatientsState, fetchingPatientsState.noMatchingPatients())
        .assertNotTerminated()
  }

  @Test
  fun `when the user clicks on a patient search result, then open the patient summary screen`() {
    // given
    val patientUuid = UUID.fromString("d18fa4dc-3b47-4a88-826f-342401527d65")

    val testObserver = uiStates.test()

    // when
    uiEventsSubject.onNext(ViewPatient(patientUuid))

    // then
    testObserver
        .assertNoErrors()
        .assertNoValues()
        .assertNotTerminated()

    verify(ui).openPatientSummary(patientUuid)
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when enter patient name is clicked, then take the user to search patient screen`() {
    // given
    val patientSearchResults = listOf(PatientMocker.patientSearchResult())
    val patientsFetched = ShortCodeSearchResultState
        .fetchingPatients("1234567")
        .patientsFetched(PatientSearchResults(
            visitedCurrentFacility = patientSearchResults,
            notVisitedCurrentFacility = emptyList(),
            currentFacility = currentFacility))
    uiStateProducer.states.onNext(patientsFetched) // TODO Fix `setState` in tests

    val testObserver = uiStates.test()

    // when
    uiEventsSubject.onNext(SearchPatient)

    // then
    testObserver
        .assertNoErrors()
        .assertNoValues()
        .assertNotTerminated()

    verify(ui).openPatientSearch()
    verifyNoMoreInteractions(ui)
  }
}
