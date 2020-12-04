package org.simple.clinic.shortcodesearchresult

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.TestObserver
import io.reactivex.subjects.PublishSubject
import org.junit.After
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.PatientToFacilityId
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.PatientSearchResult
import org.simple.clinic.searchresultsview.PatientSearchResults
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.scheduler.TestSchedulersProvider
import org.simple.clinic.util.toOptional
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent
import java.time.Instant
import java.util.UUID

class ShortCodeSearchResultLogicTest {
  private val uiEventsSubject = PublishSubject.create<UiEvent>()
  private val patientRepository = mock<PatientRepository>()
  private val userSession = mock<UserSession>()
  private val facilityRepository = mock<FacilityRepository>()
  private val bloodPressureDao = mock<BloodPressureMeasurement.RoomDao>()
  private val shortCode = "1234567"
  private val fetchingPatientsState = ShortCodeSearchResultState.fetchingPatients(shortCode)
  private val ui = mock<ShortCodeSearchResultUi>()

  private val loggedInUser = TestData.loggedInUser()
  private val currentFacility = TestData.facility(uuid = UUID.fromString("27a61122-ed11-490a-a3ae-b881840e9842"))

  private val uiStateProducer = ShortCodeSearchResultStateProducer(
      shortCode = shortCode,
      patientRepository = patientRepository,
      userSession = userSession,
      facilityRepository = facilityRepository,
      bloodPressureDao = bloodPressureDao,
      ui = ui,
      schedulersProvider = TestSchedulersProvider.trampoline()
  )
  private val uiChangeProducer = ShortCodeSearchResultUiChangeProducer(TestSchedulersProvider.trampoline())
  private val disposables = CompositeDisposable()

  lateinit var testObserver: TestObserver<ShortCodeSearchResultState>

  @After
  fun tearDown() {
    disposables.dispose()
  }

  @Test
  fun `when the screen is created, then patients matching the BP passport number must be fetched`() {
    // given
    val otherFacility = TestData.facility(uuid = UUID.fromString("dca1b165-d7ad-4a93-a645-d657e48afd69"))

    val patientSearchResultsInCurrentFacility = TestData.patientSearchResult(uuid = UUID.fromString("4e0661c6-4376-4aab-8718-4623f1653dcc"))
    val patientSearchResultInOtherFacility = TestData.patientSearchResult(uuid = UUID.fromString("1fe5981a-cdef-4457-bd86-178d49e8cc97"))

    val patientSearchResults = listOf(
        patientSearchResultsInCurrentFacility,
        patientSearchResultInOtherFacility)

    val patientToFacilityIds = listOf(
        PatientToFacilityId(patientSearchResultsInCurrentFacility.uuid, currentFacility.uuid),
        PatientToFacilityId(patientSearchResultInOtherFacility.uuid, otherFacility.uuid)
    )

    whenever(facilityRepository.currentFacility()).thenReturn(Observable.just(currentFacility))
    whenever(patientRepository.searchByShortCode(shortCode)).thenReturn(Observable.just(patientSearchResults))
    whenever(bloodPressureDao.patientToFacilityIds(
        listOf(
            patientSearchResultsInCurrentFacility.uuid,
            patientSearchResultInOtherFacility.uuid
        )
    )).thenReturn(patientToFacilityIds)

    // when
    setupStateProducer()

    // then
    val expectedPatientResults = PatientSearchResults(
        visitedCurrentFacility = listOf(patientSearchResultsInCurrentFacility),
        notVisitedCurrentFacility = listOf(patientSearchResultInOtherFacility),
        currentFacility = currentFacility)

    testObserver
        .assertNoErrors()
        .assertValues(fetchingPatientsState, fetchingPatientsState.patientsFetched(expectedPatientResults))
        .assertNotTerminated()

    verify(ui).showLoading()
    verify(ui).hideLoading()
    verify(ui).showSearchResults(expectedPatientResults)
    verify(ui).showSearchPatientButton()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the screen is created and there are no patients, then don't show any patients`() {
    // given
    val emptyPatientSearchResults = emptyList<PatientSearchResult>()
    whenever(patientRepository.searchByShortCode(shortCode))
        .thenReturn(Observable.just(emptyPatientSearchResults))

    // when
    setupStateProducer()

    // then
    testObserver
        .assertNoErrors()
        .assertValues(fetchingPatientsState, fetchingPatientsState.noMatchingPatients())
        .assertNotTerminated()

    verify(ui).showLoading()
    verify(ui).hideLoading()
    verify(ui).showNoPatientsMatched()
    verify(ui).showSearchPatientButton()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the user clicks on a patient search result, then open the patient summary screen`() {
    // given
    val patientUuid = UUID.fromString("d18fa4dc-3b47-4a88-826f-342401527d65")
    val patientSearchResults = listOf(TestData.patientSearchResult(
        uuid = patientUuid,
        lastSeen = PatientSearchResult.LastSeen(
            lastSeenOn = Instant.parse("2018-01-01T00:00:00Z"),
            lastSeenAtFacilityName = "PHC Obvious",
            lastSeenAtFacilityUuid = currentFacility.uuid
        )
    ))
    whenever(patientRepository.searchByShortCode(shortCode))
        .thenReturn(Observable.just(patientSearchResults))
    whenever(bloodPressureDao.patientToFacilityIds(listOf(patientUuid)))
        .thenReturn(listOf(PatientToFacilityId(patientUuid, currentFacility.uuid)))

    // when
    setupStateProducer()
    uiEventsSubject.onNext(ViewPatient(patientUuid))

    // then
    val expectedPatientResults = PatientSearchResults(
        visitedCurrentFacility = patientSearchResults,
        notVisitedCurrentFacility = emptyList(),
        currentFacility = currentFacility
    )
    testObserver
        .assertNoErrors()
        .assertValues(fetchingPatientsState, fetchingPatientsState.patientsFetched(expectedPatientResults))
        .assertNotTerminated()

    verify(ui).showLoading()
    verify(ui).hideLoading()
    verify(ui).showSearchPatientButton()
    verify(ui).showSearchResults(expectedPatientResults)
    verify(ui).openPatientSummary(patientUuid)
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when enter patient name is clicked, then take the user to search patient screen`() {
    // given
    val patientUuid = UUID.fromString("ee25c8dd-59ee-448d-8e37-cf87aee8423a")
    val patientSearchResults = listOf(TestData.patientSearchResult(
        uuid = patientUuid,
        lastSeen = PatientSearchResult.LastSeen(
            lastSeenOn = Instant.parse("2018-01-01T00:00:00Z"),
            lastSeenAtFacilityName = "PHC Obvious",
            lastSeenAtFacilityUuid = currentFacility.uuid
        )
    ))
    whenever(patientRepository.searchByShortCode(shortCode))
        .thenReturn(Observable.just(patientSearchResults))
    whenever(bloodPressureDao.patientToFacilityIds(listOf(patientUuid)))
        .thenReturn(listOf(PatientToFacilityId(patientUuid, currentFacility.uuid)))

    // when
    setupStateProducer()
    uiEventsSubject.onNext(SearchPatient)

    // then
    val expectedPatientResults = PatientSearchResults(
        visitedCurrentFacility = patientSearchResults,
        notVisitedCurrentFacility = emptyList(),
        currentFacility = currentFacility
    )
    testObserver
        .assertNoErrors()
        .assertValues(fetchingPatientsState, fetchingPatientsState.patientsFetched(expectedPatientResults))
        .assertNotTerminated()

    verify(ui).showLoading()
    verify(ui).hideLoading()
    verify(ui).showSearchPatientButton()
    verify(ui).showSearchResults(expectedPatientResults)
    verify(ui).openPatientSearch()
    verifyNoMoreInteractions(ui)
  }

  private fun setupStateProducer() {
    whenever(userSession.loggedInUser()).thenReturn(Observable.just(loggedInUser.toOptional()))
    whenever(facilityRepository.currentFacility()).thenReturn(Observable.just(currentFacility))

    val uiStates = uiEventsSubject
        .compose(uiStateProducer)
        .doOnNext { uiStateProducer.states.onNext(it) }
        .share()

    val uiChangeSubscription = uiStates
        .compose(uiChangeProducer)
        .subscribe { uiChange -> uiChange(ui) }

    testObserver = uiStates.test()

    disposables.addAll(testObserver, uiChangeSubscription)

    uiEventsSubject.onNext(ScreenCreated())
  }
}
