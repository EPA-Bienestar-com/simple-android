package org.simple.clinic.patient

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.TestData
import org.simple.clinic.analytics.Analytics
import org.simple.clinic.analytics.MockAnalyticsReporter
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.PatientSearchCriteria.Name
import org.simple.clinic.patient.PatientSearchResult.PatientNameAndId
import org.simple.clinic.patient.ReminderConsent.Granted
import org.simple.clinic.patient.businessid.BusinessId
import org.simple.clinic.patient.businessid.BusinessIdMetaDataAdapter
import org.simple.clinic.patient.filter.SearchPatientByName
import org.simple.clinic.patient.shortcode.DigitFilter
import org.simple.clinic.patient.shortcode.UuidShortCodeCreator
import org.simple.clinic.patient.sync.PatientPayload
import org.simple.clinic.patient.sync.PatientPhoneNumberPayload
import org.simple.clinic.registration.phone.PhoneNumberValidator
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.TestUtcClock
import org.simple.clinic.util.advanceTimeBy
import org.simple.clinic.util.scheduler.TestSchedulersProvider
import org.simple.clinic.widgets.ageanddateofbirth.UserInputAgeValidator
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator
import org.threeten.bp.Duration
import org.threeten.bp.format.DateTimeFormatter
import java.util.UUID

@RunWith(JUnitParamsRunner::class)
class PatientRepositoryTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private lateinit var repository: PatientRepository
  private lateinit var config: PatientConfig
  private val database = mock<AppDatabase>()

  private val patientSearchResultDao = mock<PatientSearchResult.RoomDao>()
  private val patientDao = mock<Patient.RoomDao>()
  private val patientAddressDao = mock<PatientAddress.RoomDao>()
  private val patientPhoneNumberDao = mock<PatientPhoneNumber.RoomDao>()
  private val bloodPressureMeasurementDao = mock<BloodPressureMeasurement.RoomDao>()
  private val businessIdDao = mock<BusinessId.RoomDao>()
  private val dobValidator = mock<UserInputDateValidator>()
  private val ageValidator = mock<UserInputAgeValidator>()
  private val facilityRepository = mock<FacilityRepository>()
  private val numberValidator = mock<PhoneNumberValidator>()
  private val searchPatientByName = mock<SearchPatientByName>()
  private val businessIdMetaAdapter = mock<BusinessIdMetaDataAdapter>()
  private val uuidShortCodeCreator = UuidShortCodeCreator(
      requiredShortCodeLength = 7,
      characterFilter = DigitFilter()
  )

  private val clock = TestUtcClock()
  private val dateOfBirthFormat = DateTimeFormatter.ISO_DATE
  private val user = TestData.loggedInUser()
  private val facility = TestData.facility()
  private val schedulersProvider = TestSchedulersProvider()

  @Before
  fun setUp() {
    config = PatientConfig(limitOfSearchResults = 100, scanSimpleCardFeatureEnabled = false, recentPatientLimit = 10)

    repository = PatientRepository(
        database = database,
        dobValidator = dobValidator,
        numberValidator = numberValidator,
        utcClock = clock,
        searchPatientByName = searchPatientByName,
        configProvider = Observable.fromCallable { config },
        reportsRepository = mock(),
        businessIdMetaDataAdapter = businessIdMetaAdapter,
        schedulersProvider = schedulersProvider,
        dateOfBirthFormat = dateOfBirthFormat,
        uuidShortCodeCreator = uuidShortCodeCreator,
            ageValidator = ageValidator)

    whenever(facilityRepository.currentFacility(user)).thenReturn(Observable.just(facility))
    whenever(bloodPressureMeasurementDao.patientToFacilityIds(any())).thenReturn(Flowable.just(listOf()))
    whenever(database.bloodPressureDao()).thenReturn(bloodPressureMeasurementDao)
  }

  @After
  fun tearDown() {
    Analytics.clearReporters()
  }

  @Test
  @Parameters(value = [
    "PENDING, false",
    "INVALID, true",
    "DONE, true"])
  fun `when merging patients with server records, ignore records that already exist locally and are syncing or pending-sync`(
      syncStatusOfLocalCopy: SyncStatus,
      serverRecordExpectedToBeSaved: Boolean
  ) {
    whenever(database.patientDao()).thenReturn(patientDao)
    whenever(database.addressDao()).thenReturn(patientAddressDao)
    whenever(database.phoneNumberDao()).thenReturn(patientPhoneNumberDao)
    whenever(database.businessIdDao()).thenReturn(businessIdDao)

    val patientUuid = UUID.randomUUID()
    val addressUuid = UUID.randomUUID()

    val localPatientCopy = TestData.patient(uuid = patientUuid, addressUuid = addressUuid, syncStatus = syncStatusOfLocalCopy)
    whenever(patientDao.getOne(patientUuid)).thenReturn(localPatientCopy)

    val serverAddress = TestData.patientAddress(uuid = addressUuid).toPayload()
    val serverPatientWithoutPhone = PatientPayload(
        uuid = patientUuid,
        fullName = "name",
        gender = mock(),
        dateOfBirth = mock(),
        age = 0,
        ageUpdatedAt = mock(),
        status = PatientStatus.Active,
        createdAt = mock(),
        updatedAt = mock(),
        deletedAt = null,
        address = serverAddress,
        phoneNumbers = null,
        businessIds = emptyList(),
        recordedAt = mock(),
        reminderConsent = Granted
    )

    repository.mergeWithLocalData(listOf(serverPatientWithoutPhone)).blockingAwait()

    if (serverRecordExpectedToBeSaved) {
      verify(patientDao).save(argThat<List<Patient>> { isNotEmpty() })
      verify(patientAddressDao).save(argThat<List<PatientAddress>> { isNotEmpty() })
    } else {
      verify(patientDao).save(argThat<List<Patient>> { isEmpty() })
      verify(patientAddressDao).save(argThat<List<PatientAddress>> { isEmpty() })
    }
  }

  @Test
  @Parameters(value = [
    "PENDING, false",
    "INVALID, true",
    "DONE, true"])
  fun `that already exist locally and are syncing or pending-sync`(
      syncStatusOfLocalCopy: SyncStatus,
      serverRecordExpectedToBeSaved: Boolean
  ) {
    whenever(database.patientDao()).thenReturn(patientDao)
    whenever(database.addressDao()).thenReturn(patientAddressDao)
    whenever(database.phoneNumberDao()).thenReturn(patientPhoneNumberDao)
    whenever(database.businessIdDao()).thenReturn(businessIdDao)

    val patientUuid = UUID.randomUUID()
    val addressUuid = UUID.randomUUID()

    val localPatientCopy = TestData.patient(uuid = patientUuid, addressUuid = addressUuid, syncStatus = syncStatusOfLocalCopy)
    whenever(patientDao.getOne(patientUuid)).thenReturn(localPatientCopy)

    val serverAddress = TestData.patientAddress(uuid = addressUuid).toPayload()
    val serverPatientWithPhone = PatientPayload(
        uuid = patientUuid,
        fullName = "name",
        gender = mock(),
        dateOfBirth = mock(),
        age = 0,
        ageUpdatedAt = mock(),
        status = PatientStatus.Active,
        createdAt = mock(),
        updatedAt = mock(),
        deletedAt = null,
        address = serverAddress,
        phoneNumbers = listOf(PatientPhoneNumberPayload(
            uuid = UUID.randomUUID(),
            number = "1232",
            type = mock(),
            active = false,
            createdAt = mock(),
            updatedAt = mock(),
            deletedAt = mock())),
        businessIds = emptyList(),
        recordedAt = mock(),
        reminderConsent = Granted
    )

    repository.mergeWithLocalData(listOf(serverPatientWithPhone)).blockingAwait()

    if (serverRecordExpectedToBeSaved) {
      verify(patientAddressDao).save(argThat<List<PatientAddress>> { isNotEmpty() })
      verify(patientDao).save(argThat<List<Patient>> { isNotEmpty() })
      verify(patientPhoneNumberDao).save(argThat { isNotEmpty() })

    } else {
      verify(patientAddressDao).save(argThat<List<PatientAddress>> { isEmpty() })
      verify(patientDao).save(argThat<List<Patient>> { isEmpty() })
      verify(patientPhoneNumberDao).save(emptyList())
    }
  }

  @Test
  @Parameters(method = "params for querying results for fuzzy search")
  fun `when the the filter patient by name returns results, the database must be queried for the complete information`(
      filteredUuids: List<UUID>,
      shouldQueryFilteredIds: Boolean
  ) {
    whenever(database.patientDao()).thenReturn(patientDao)
    whenever(database.addressDao()).thenReturn(patientAddressDao)
    whenever(database.phoneNumberDao()).thenReturn(patientPhoneNumberDao)
    whenever(database.patientSearchDao()).thenReturn(patientSearchResultDao)
    whenever(searchPatientByName.search(any(), any())).thenReturn(Single.just(filteredUuids))
    whenever(patientSearchResultDao.searchByIds(any(), any()))
        .thenReturn(Single.just(filteredUuids.map { TestData.patientSearchResult(uuid = it) }))
    whenever(database.patientSearchDao().nameAndId(any())).thenReturn(Flowable.just(emptyList()))

    repository
        .search(Name("name"))
        .ignoreElements()
        .blockingAwait()

    if (shouldQueryFilteredIds) {
      verify(patientSearchResultDao, atLeastOnce()).searchByIds(filteredUuids, PatientStatus.Active)
    } else {
      verify(patientSearchResultDao, never()).searchByIds(filteredUuids, PatientStatus.Active)
    }
  }

  @Suppress("Unused")
  private fun `params for querying results for fuzzy search`(): List<List<Any>> {
    return listOf(
        listOf(listOf(UUID.randomUUID()), true),
        listOf(listOf(UUID.randomUUID(), UUID.randomUUID()), true),
        listOf(emptyList<UUID>(), false))
  }

  @Test
  @Parameters(method = "params for sorting results for fuzzy search")
  fun `when the filter patient by name returns results, the results must be sorted in the same order as the filtered ids`(
      filteredUuids: List<UUID>,
      results: List<PatientSearchResult>,
      expectedResults: List<PatientSearchResult>
  ) {
    whenever(database.patientDao()).thenReturn(patientDao)
    whenever(database.addressDao()).thenReturn(patientAddressDao)
    whenever(database.phoneNumberDao()).thenReturn(patientPhoneNumberDao)
    whenever(database.patientSearchDao()).thenReturn(patientSearchResultDao)
    whenever(searchPatientByName.search(any(), any())).thenReturn(Single.just(filteredUuids))
    whenever(patientSearchResultDao.searchByIds(any(), any())).thenReturn(Single.just(results))
    whenever(database.patientSearchDao().nameAndId(any())).thenReturn(Flowable.just(emptyList()))

    val actualResults = repository.search(Name("name")).blockingFirst()
    assertThat(actualResults).isEqualTo(expectedResults)
  }

  @Suppress("Unused")
  private fun `params for sorting results for fuzzy search`(): List<List<Any>> {
    fun generateTestData(numberOfResults: Int): List<Any> {
      val filteredUuids = (1..numberOfResults).map { UUID.randomUUID() }
      val results = filteredUuids.map { TestData.patientSearchResult(uuid = it) }.shuffled()
      val expectedResults = filteredUuids.map { uuid -> results.find { it.uuid == uuid }!! }

      assertThat(results.map { it.uuid }).isNotEqualTo(filteredUuids)
      assertThat(expectedResults.map { it.uuid }).isEqualTo(filteredUuids)

      return listOf(filteredUuids, results, expectedResults)
    }

    return listOf(
        generateTestData(6),
        generateTestData(10))
  }

  @Test
  fun `the timing of all parts of search patient flow must be reported to analytics`() {
    val reporter = MockAnalyticsReporter()
    Analytics.addReporter(reporter)

    val timeTakenToFetchPatientNameAndId = Duration.ofMinutes(1L)
    val timeTakenToFuzzyFilterPatientNames = Duration.ofMinutes(5L)
    val timeTakenToFetchPatientDetails = Duration.ofSeconds(45L)

    val patientUuid = UUID.randomUUID()

    // The setup function in this test creates reactive sources that terminate immediately after
    // emission (using just(), for example). This is fine for most of our tests, but the way this
    // test is structured depends on the sources behaving as they do in reality
    // (i.e, infinite sources). We replace the mocks for these tests with Subjects to do this.
    whenever(patientSearchResultDao.nameAndId(any()))
        .thenReturn(
            BehaviorSubject.createDefault(listOf(PatientNameAndId(patientUuid, "Name")))
                .doOnNext { schedulersProvider.testScheduler.advanceTimeBy(timeTakenToFetchPatientNameAndId) }
                .toFlowable(BackpressureStrategy.LATEST)
        )
    whenever(searchPatientByName.search(any(), any()))
        .thenReturn(
            BehaviorSubject.createDefault(listOf(patientUuid))
                .doOnNext { schedulersProvider.testScheduler.advanceTimeBy(timeTakenToFuzzyFilterPatientNames) }
                .firstOrError()
        )
    whenever(patientSearchResultDao.searchByIds(any(), any()))
        .thenReturn(
            BehaviorSubject.createDefault(listOf(TestData.patientSearchResult(uuid = patientUuid)))
                .doOnNext { schedulersProvider.testScheduler.advanceTimeBy(timeTakenToFetchPatientDetails) }
                .firstOrError()
        )
    whenever(database.patientSearchDao()).thenReturn(patientSearchResultDao)

    repository
        .search(Name("search"))
        .blockingFirst()

    val receivedEvents = reporter.receivedEvents
    assertThat(receivedEvents).hasSize(3)

    val (fetchNameAndId,
        fuzzyFilterByName,
        fetchPatientDetails) = receivedEvents

    assertThat(fetchNameAndId.props["operationName"]).isEqualTo("Search Patient:Fetch Name and Id")
    assertThat(fetchNameAndId.props["timeTakenInMillis"]).isEqualTo(timeTakenToFetchPatientNameAndId.toMillis())

    assertThat(fuzzyFilterByName.props["operationName"]).isEqualTo("Search Patient:Fuzzy Filtering By Name")
    assertThat(fuzzyFilterByName.props["timeTakenInMillis"]).isEqualTo(timeTakenToFuzzyFilterPatientNames.toMillis())

    assertThat(fetchPatientDetails.props["operationName"]).isEqualTo("Search Patient:Fetch Patient Details")
    assertThat(fetchPatientDetails.props["timeTakenInMillis"]).isEqualTo(timeTakenToFetchPatientDetails.toMillis())
  }
}
