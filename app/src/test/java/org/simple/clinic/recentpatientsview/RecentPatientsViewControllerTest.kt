package org.simple.clinic.recentpatientsview

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import junitparams.JUnitParamsRunner
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.TestData
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.Age
import org.simple.clinic.patient.Gender.Female
import org.simple.clinic.patient.Gender.Male
import org.simple.clinic.patient.Gender.Transgender
import org.simple.clinic.patient.PatientConfig
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.TestUserClock
import org.simple.clinic.util.toOptional
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

@RunWith(JUnitParamsRunner::class)
class RecentPatientsViewControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val screen: RecentPatientsView = mock()
  private val userSession: UserSession = mock()
  private val patientRepository: PatientRepository = mock()
  private val facilityRepository: FacilityRepository = mock()

  private val uiEvents: Subject<UiEvent> = PublishSubject.create()
  private val loggedInUser = TestData.loggedInUser(uuid = UUID.fromString("a4269927-d236-4507-acf9-f75272758740"))
  private val facility = TestData.facility(uuid = UUID.fromString("ddc6471b-caa4-4bfa-9bf6-60898a77c1ec"))
  private val recentPatientLimit = 3
  private val recentPatientLimitPlusOne = recentPatientLimit + 1
  private val dateFormatter = DateTimeFormatter.ISO_INSTANT
  private val userClock = TestUserClock()

  private lateinit var controllerSubscription: Disposable

  @After
  fun tearDown() {
    controllerSubscription.dispose()
  }

  @Test
  fun `when screen opens then fetch and set recent patients`() {
    val patientUuid1 = UUID.fromString("dbb418f0-8329-48c0-a536-a0a781bbdf36")
    val patientUuid2 = UUID.fromString("3eabf6a2-88d8-41e1-b21c-6f3509963382")
    val patientUuid3 = UUID.fromString("aa228e88-50b0-4ada-a839-926fd6dacf2e")
    userClock.setDate(LocalDate.parse("2020-02-01"))
    whenever(patientRepository.recentPatients(
        facilityUuid = facility.uuid,
        limit = recentPatientLimitPlusOne
    )).thenReturn(Observable.just(listOf(
        TestData.recentPatient(
            uuid = patientUuid1,
            fullName = "Ajay Kumar",
            age = Age(42, Instant.now(userClock)),
            gender = Transgender,
            updatedAt = Instant.now(userClock),
            patientRecordedAt = Instant.now(userClock)
        ),
        TestData.recentPatient(
            uuid = patientUuid2,
            fullName = "Vijay Kumar",
            age = Age(24, Instant.now(userClock)),
            gender = Male,
            updatedAt = Instant.now(userClock).minus(1, ChronoUnit.DAYS),
            patientRecordedAt = Instant.now(userClock).minus(5, ChronoUnit.DAYS)
        ),
        TestData.recentPatient(
            uuid = patientUuid3,
            fullName = "Vinaya Kumari",
            age = Age(27, Instant.now(userClock)),
            gender = Female,
            updatedAt = Instant.now(userClock).minus(3, ChronoUnit.DAYS),
            patientRecordedAt = Instant.now(userClock).minus(10, ChronoUnit.DAYS)
        )
    )))

    setupController()

    verify(screen).updateRecentPatients(listOf(
        RecentPatientItem(
            uuid = patientUuid1,
            name = "Ajay Kumar",
            age = 42,
            gender = Transgender,
            updatedAt = Instant.parse("2020-02-01T00:00:00Z"),
            dateFormatter = dateFormatter,
            clock = userClock,
            isNewRegistration = true
        ),
        RecentPatientItem(
            uuid = patientUuid2,
            name = "Vijay Kumar",
            age = 24,
            gender = Male,
            updatedAt = Instant.parse("2020-01-31T00:00:00Z"),
            dateFormatter = dateFormatter,
            clock = userClock,
            isNewRegistration = false
        ),
        RecentPatientItem(
            uuid = patientUuid3,
            name = "Vinaya Kumari",
            age = 27,
            gender = Female,
            updatedAt = Instant.parse("2020-01-29T00:00:00Z"),
            dateFormatter = dateFormatter,
            clock = userClock,
            isNewRegistration = false
        )
    ))
    verify(screen).showOrHideRecentPatients(isVisible = true)
  }

  @Test
  fun `when number of recent patients is greater than recent patient limit then add see all item`() {
    val patientUuid1 = UUID.fromString("ee387eea-732d-4218-a42b-757e3b87d171")
    val patientUuid2 = UUID.fromString("3eaf6595-d3cd-41e4-bfb5-b1fa6e17fed9")
    val patientUuid3 = UUID.fromString("4b404776-feee-4c0d-8262-d7bb69350d83")
    val patientUuid4 = UUID.fromString("091035b5-efbd-404f-890c-b5016dc32b6d")
    userClock.setDate(date = LocalDate.parse("2020-01-01"))
    whenever(patientRepository.recentPatients(
        facilityUuid = facility.uuid,
        limit = recentPatientLimitPlusOne
    )).thenReturn(Observable.just(listOf(
        TestData.recentPatient(
            uuid = patientUuid1,
            fullName = "Ajay Kumar",
            age = Age(42, Instant.now(userClock)),
            gender = Transgender,
            updatedAt = Instant.now(userClock),
            patientRecordedAt = Instant.now(userClock)
        ),
        TestData.recentPatient(
            uuid = patientUuid2,
            fullName = "Vijay Kumar",
            age = Age(24, Instant.now(userClock)),
            gender = Male,
            updatedAt = Instant.now(userClock).minus(1, ChronoUnit.DAYS),
            patientRecordedAt = Instant.now(userClock).minus(3, ChronoUnit.DAYS)
        ),
        TestData.recentPatient(
            uuid = patientUuid3,
            fullName = "Vinaya Kumari",
            age = Age(27, Instant.now(userClock)),
            gender = Female,
            updatedAt = Instant.now(userClock).minus(4, ChronoUnit.DAYS),
            patientRecordedAt = Instant.now(userClock).minus(15, ChronoUnit.DAYS)
        ),
        TestData.recentPatient(
            uuid = patientUuid4,
            fullName = "Abhilash Devi",
            age = Age(37, Instant.now(userClock)),
            gender = Transgender
        )
    )))

    setupController()

    verify(screen).updateRecentPatients(listOf(
        RecentPatientItem(
            uuid = patientUuid1,
            name = "Ajay Kumar",
            age = 42,
            gender = Transgender,
            updatedAt = Instant.parse("2020-01-01T00:00:00Z"),
            dateFormatter = dateFormatter,
            clock = userClock,
            isNewRegistration = true
        ),
        RecentPatientItem(
            uuid = patientUuid2,
            name = "Vijay Kumar",
            age = 24,
            gender = Male,
            updatedAt = Instant.parse("2019-12-31T00:00:00Z"),
            dateFormatter = dateFormatter,
            clock = userClock,
            isNewRegistration = false
        ),
        RecentPatientItem(
            uuid = patientUuid3,
            name = "Vinaya Kumari",
            age = 27,
            gender = Female,
            updatedAt = Instant.parse("2019-12-28T00:00:00Z"),
            dateFormatter = dateFormatter,
            clock = userClock,
            isNewRegistration = false
        ),
        SeeAllItem
    ))
    verify(screen).showOrHideRecentPatients(isVisible = true)
  }

  @Test
  fun `when screen opens and there are no recent patients then show empty state`() {
    whenever(patientRepository.recentPatients(
        facilityUuid = facility.uuid,
        limit = recentPatientLimitPlusOne
    )).thenReturn(Observable.just(emptyList()))

    setupController()

    verify(screen).showOrHideRecentPatients(isVisible = false)
  }

  @Test
  fun `when any recent patient item is clicked, then open patient summary`() {
    val patientUuid = UUID.fromString("418adb0f-032d-4914-93d3-dc0633802e3e")

    whenever(patientRepository.recentPatients(
        facilityUuid = facility.uuid,
        limit = recentPatientLimitPlusOne
    )).thenReturn(Observable.just(listOf(TestData.recentPatient(uuid = patientUuid, dateOfBirth = LocalDate.parse("2018-01-01")))))

    setupController()
    uiEvents.onNext(RecentPatientItemClicked(patientUuid = patientUuid))

    verify(screen).openPatientSummary(patientUuid)
  }

  @Test
  fun `when see all is clicked, then open recent patients screen`() {
    whenever(patientRepository.recentPatients(
        facilityUuid = facility.uuid,
        limit = recentPatientLimitPlusOne
    )).thenReturn(Observable.just(emptyList()))

    setupController()
    uiEvents.onNext(SeeAllItemClicked)

    verify(screen).openRecentPatientsScreen()
  }

  private fun setupController() {
    whenever(userSession.loggedInUser()).thenReturn(Observable.just(loggedInUser.toOptional()))
    whenever(facilityRepository.currentFacility(loggedInUser)).thenReturn(Observable.just(facility))

    val controller = RecentPatientsViewController(
        userSession = userSession,
        patientRepository = patientRepository,
        facilityRepository = facilityRepository,
        userClock = userClock,
        patientConfig = PatientConfig(
            limitOfSearchResults = 1,
            recentPatientLimit = recentPatientLimit
        ),
        dateFormatter = dateFormatter
    )

    controllerSubscription = uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(screen) }

    uiEvents.onNext(ScreenCreated())
  }
}
