package org.simple.clinic.searchresultsview

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.PatientToFacilityId
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.PatientSearchCriteria.Name
import org.simple.clinic.patient.PatientSearchCriteria.PhoneNumber
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.scheduler.TestSchedulersProvider
import org.simple.clinic.widgets.UiEvent
import org.simple.mobius.migration.MobiusTestFixture
import java.util.UUID

class PatientSearchViewControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val ui = mock<SearchResultsUi>()
  private val patientRepository = mock<PatientRepository>()
  private val userSession = mock<UserSession>()
  private val facilityRepository = mock<FacilityRepository>()
  private val bloodPressureDao = mock<BloodPressureMeasurement.RoomDao>()

  private val uiEvents = PublishSubject.create<UiEvent>()

  private val currentFacility = TestData.facility(UUID.fromString("69cf85c8-6788-4071-b985-0536ae606b70"))
  private val otherFacility = TestData.facility(UUID.fromString("0bb48f0a-3a6c-4e35-8781-74b074443f36"))
  private val user = TestData.loggedInUser(uuid = UUID.fromString("00b815f7-96dc-4846-bb87-3ef60e690523"))

  private val patientName = "name"
  private val phoneNumber: String = "123456"

  private lateinit var controllerSubscription: Disposable
  private lateinit var testFixture: MobiusTestFixture<SearchResultsModel, SearchResultsEvent, SearchResultsEffect>

  @Before
  fun setUp() {
    whenever(userSession.requireLoggedInUser()).doReturn(Observable.just(user))
    whenever(facilityRepository.currentFacility(user)).doReturn(Observable.just(currentFacility))
    whenever(patientRepository.search(Name(patientName))).doReturn(Observable.never())
    whenever(patientRepository.search(PhoneNumber(phoneNumber))).doReturn(Observable.never())
  }

  @After
  fun tearDown() {
    controllerSubscription.dispose()
    testFixture.dispose()
  }

  @Test
  fun `when searching patients by name returns results, the results should be displayed`() {
    // given
    val searchCriteria = Name(patientName = patientName)
    val searchResult1InCurrentFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("1d5f18d9-43f7-4e7f-92d3-a4f641709470"),
        fullName = "Anish Acharya in CHC Bagta"
    )
    val searchResult2InCurrentFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("6aba53eb-c8dd-452c-b640-c11c6df94f8a"),
        fullName = "Deepa in CHC Bagta"
    )
    val searchResult3InCurrentFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("65965892-7218-4ca1-bfca-e7b037751802"),
        fullName = "Vinay in CHC Bagta"
    )
    val searchResult1InOtherFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("139bfac5-1adc-43fa-9406-d1000fb67a88"),
        fullName = "Sanchita in CHC Ballianwali"
    )
    val searchResult2InOtherFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("760c7342-b0f2-4130-9d4c-c5f18336d4b8"),
        fullName = "Daniel in CHC Ballianwali"
    )

    val allSearchResults = listOf(
        searchResult1InCurrentFacility,
        searchResult2InOtherFacility,
        searchResult3InCurrentFacility,
        searchResult2InCurrentFacility,
        searchResult1InOtherFacility
    )
    val patientUuids = allSearchResults.map { it.uuid }
    val patientAndFacilityIdsToReturn = listOf(
        PatientToFacilityId(patientUuid = searchResult1InCurrentFacility.uuid, facilityUuid = currentFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult2InCurrentFacility.uuid, facilityUuid = currentFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult3InCurrentFacility.uuid, facilityUuid = currentFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult1InOtherFacility.uuid, facilityUuid = otherFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult2InOtherFacility.uuid, facilityUuid = otherFacility.uuid)
    )

    whenever(bloodPressureDao.patientToFacilityIds(patientUuids))
        .doReturn(Flowable.just(patientAndFacilityIdsToReturn))
    whenever(patientRepository.search(searchCriteria))
        .doReturn(Observable.just(allSearchResults))

    // when
    setupController()
    uiEvents.onNext(SearchPatientWithCriteria(searchCriteria))

    // then
    val expectedSearchResults = PatientSearchResults(
        visitedCurrentFacility = listOf(searchResult1InCurrentFacility, searchResult3InCurrentFacility, searchResult2InCurrentFacility),
        notVisitedCurrentFacility = listOf(searchResult2InOtherFacility, searchResult1InOtherFacility),
        currentFacility = currentFacility
    )
    verify(ui).updateSearchResults(expectedSearchResults)
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when searching patients by phone number returns results, the results should be displayed`() {
    // given
    val searchCriteria = PhoneNumber(phoneNumber = phoneNumber)
    val searchResult1InCurrentFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("1d5f18d9-43f7-4e7f-92d3-a4f641709470"),
        fullName = "Anish Acharya in CHC Bagta"
    )
    val searchResult2InCurrentFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("6aba53eb-c8dd-452c-b640-c11c6df94f8a"),
        fullName = "Deepa in CHC Bagta"
    )
    val searchResult3InCurrentFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("65965892-7218-4ca1-bfca-e7b037751802"),
        fullName = "Vinay in CHC Bagta"
    )
    val searchResult1InOtherFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("139bfac5-1adc-43fa-9406-d1000fb67a88"),
        fullName = "Sanchita in CHC Ballianwali"
    )
    val searchResult2InOtherFacility = TestData.patientSearchResult(
        uuid = UUID.fromString("760c7342-b0f2-4130-9d4c-c5f18336d4b8"),
        fullName = "Daniel in CHC Ballianwali"
    )

    val allSearchResults = listOf(
        searchResult1InCurrentFacility,
        searchResult2InOtherFacility,
        searchResult3InCurrentFacility,
        searchResult2InCurrentFacility,
        searchResult1InOtherFacility
    )
    val patientUuids = allSearchResults.map { it.uuid }
    val patientAndFacilityIdsToReturn = listOf(
        PatientToFacilityId(patientUuid = searchResult1InCurrentFacility.uuid, facilityUuid = currentFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult2InCurrentFacility.uuid, facilityUuid = currentFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult3InCurrentFacility.uuid, facilityUuid = currentFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult1InOtherFacility.uuid, facilityUuid = otherFacility.uuid),
        PatientToFacilityId(patientUuid = searchResult2InOtherFacility.uuid, facilityUuid = otherFacility.uuid)
    )

    whenever(bloodPressureDao.patientToFacilityIds(patientUuids))
        .doReturn(Flowable.just(patientAndFacilityIdsToReturn))
    whenever(patientRepository.search(searchCriteria))
        .doReturn(Observable.just(allSearchResults))

    // when
    setupController()
    uiEvents.onNext(SearchPatientWithCriteria(searchCriteria))

    // then
    val expectedSearchResults = PatientSearchResults(
        visitedCurrentFacility = listOf(searchResult1InCurrentFacility, searchResult3InCurrentFacility, searchResult2InCurrentFacility),
        notVisitedCurrentFacility = listOf(searchResult2InOtherFacility, searchResult1InOtherFacility),
        currentFacility = currentFacility
    )
    verify(ui).updateSearchResults(expectedSearchResults)
    verifyNoMoreInteractions(ui)
  }

  private fun setupController() {
    val effectHandler = SearchResultsEffectHandler(
        schedulers = TestSchedulersProvider.trampoline()
    )
    val uiRenderer = SearchResultsUiRenderer(ui)

    testFixture = MobiusTestFixture(
        events = uiEvents.ofType(),
        defaultModel = SearchResultsModel.create(),
        update = SearchResultsUpdate(),
        effectHandler = effectHandler.build(),
        modelUpdateListener = uiRenderer::render,
        init = SearchResultsInit()
    )
    testFixture.start()

    val controller = PatientSearchViewController(
        patientRepository = patientRepository,
        userSession = userSession,
        facilityRepository = facilityRepository,
        bloodPressureDao = bloodPressureDao
    )

    controllerSubscription = uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(ui) }

    uiEvents.onNext(SearchResultsViewCreated)
  }
}
