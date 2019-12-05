package org.simple.clinic.scheduleappointment.patientFacilityTransfer

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.facility.change.FacilitiesUpdateType
import org.simple.clinic.facility.change.FacilityChangeClicked
import org.simple.clinic.facility.change.FacilityChangeConfig
import org.simple.clinic.facility.change.FacilityChangeLocationPermissionChanged
import org.simple.clinic.facility.change.FacilityChangeSearchQueryChanged
import org.simple.clinic.facility.change.FacilityChangeUserLocationUpdated
import org.simple.clinic.facility.change.FacilityListItem
import org.simple.clinic.facility.change.FacilityListItemBuilder
import org.simple.clinic.location.Coordinates
import org.simple.clinic.location.LocationRepository
import org.simple.clinic.location.LocationUpdate
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.reports.ReportsRepository
import org.simple.clinic.reports.ReportsSync
import org.simple.clinic.storage.files.DeleteFileResult
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.Distance
import org.simple.clinic.util.RuntimePermissionResult
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.TestElapsedRealtimeClock
import org.simple.clinic.util.toOptional
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Duration
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(JUnitParamsRunner::class)
class PatientFacilityChangeControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val uiEvents = PublishSubject.create<UiEvent>()!!
  private val screen = mock<PatientFacilityChangeActivity>()
  private val facilityRepository = mock<FacilityRepository>()
  private val reportsRepository = mock<ReportsRepository>()
  private val userSession = mock<UserSession>()
  private val reportsSync = mock<ReportsSync>()
  private val locationRepository = mock<LocationRepository>()
  private val listItemBuilder = mock<FacilityListItemBuilder>()
  private val testComputationScheduler = TestScheduler()
  private val user = PatientMocker.loggedInUser()
  private val elapsedRealtimeClock = TestElapsedRealtimeClock()

  private val configTemplate = FacilityChangeConfig(
      locationListenerExpiry = Duration.ofSeconds(0),
      locationUpdateInterval = Duration.ofSeconds(0),
      proximityThresholdForNearbyFacilities = Distance.ofKilometers(0.0),
      staleLocationThreshold = Duration.ofSeconds(0))
  private val configProvider = BehaviorSubject.createDefault(configTemplate)

  private lateinit var controller: PatientFacilityChangeController

  @Before
  fun setUp() {
    // To control time used by Observable.timer().
    RxJavaPlugins.setComputationSchedulerHandler { testComputationScheduler }

    // Location updates are listened on a background thread.
    RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }

    controller = PatientFacilityChangeController(
        facilityRepository = facilityRepository,
        reportsRepository = reportsRepository,
        userSession = userSession,
        reportsSync = reportsSync,
        locationRepository = locationRepository,
        configProvider = configProvider,
        elapsedRealtimeClock = elapsedRealtimeClock,
        listItemBuilder = listItemBuilder)

    whenever(userSession.requireLoggedInUser()).thenReturn(Observable.just(user))

    uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(screen) }
  }

  @Test
  fun `when screen starts then facilities UI models should be created`() {
    val facility1 = PatientMocker.facility()
    val facility2 = PatientMocker.facility()
    val facilities = listOf(facility1, facility2)
    whenever(facilityRepository.facilitiesInCurrentGroup(user = user)).thenReturn(Observable.just(facilities, facilities))

    val searchQuery = ""
    val facilityListItems = emptyList<FacilityListItem>()
    whenever(listItemBuilder.build(any(), any(), any(), any())).thenReturn(facilityListItems)

    uiEvents.onNext(FacilityChangeUserLocationUpdated(LocationUpdate.Unavailable))
    uiEvents.onNext(FacilityChangeSearchQueryChanged(searchQuery))

    verify(listItemBuilder, times(2)).build(
        facilities = facilities,
        searchQuery = searchQuery,
        userLocation = null,
        proximityThreshold = configTemplate.proximityThresholdForNearbyFacilities)
    verify(screen).updateFacilities(facilityListItems, FacilitiesUpdateType.FIRST_UPDATE)
    verify(screen).updateFacilities(facilityListItems, FacilitiesUpdateType.SUBSEQUENT_UPDATE)
  }

  @Test
  fun `when search query is changed then the query should be used for fetching filtered facilities`() {
    val facilities = listOf(
        PatientMocker.facility(name = "Facility 1"),
        PatientMocker.facility(name = "Facility 2"))
    whenever(facilityRepository.facilitiesInCurrentGroup(any(), eq(user))).thenReturn(Observable.just(facilities))

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(FacilityChangeUserLocationUpdated(LocationUpdate.Unavailable))
    uiEvents.onNext(FacilityChangeSearchQueryChanged(query = "F"))
    uiEvents.onNext(FacilityChangeSearchQueryChanged(query = "Fa"))
    uiEvents.onNext(FacilityChangeSearchQueryChanged(query = "Fac"))

    verify(facilityRepository).facilitiesInCurrentGroup(searchQuery = "F", user = user)
    verify(facilityRepository).facilitiesInCurrentGroup(searchQuery = "Fa", user = user)
    verify(facilityRepository).facilitiesInCurrentGroup(searchQuery = "Fac", user = user)
  }

  @Test
  fun `when a facility is selected then the user's facility should be changed and the screen should be closed`() {
    val newFacility = PatientMocker.facility()
    val user = PatientMocker.loggedInUser()
    whenever(userSession.requireLoggedInUser()).thenReturn(Observable.just(user))
    whenever(facilityRepository.associateUserWithFacility(user, newFacility)).thenReturn(Completable.complete())
    whenever(facilityRepository.setCurrentFacility(user, newFacility)).thenReturn(Completable.complete())
    whenever(reportsRepository.reportsFile()).thenReturn(Observable.just(File("").toOptional()))

    whenever(reportsRepository.deleteReportsFile()).thenReturn(Single.just(DeleteFileResult.Success))
    whenever(reportsSync.sync()).thenReturn(Completable.complete())

    uiEvents.onNext(FacilityChangeClicked(newFacility))

    val inOrder = inOrder(facilityRepository, screen)
    inOrder.verify(facilityRepository).associateUserWithFacility(user, newFacility)
    inOrder.verify(screen).goBack()
  }

  @Test
  @Parameters(method = "params for when a facility is changed then the report has to be deleted and synced")
  fun `when a facility is changed then the report has to be deleted and synced`(
      deleteReport: Single<DeleteFileResult>,
      reportsSyncCompletable: Completable
  ) {
    val newFacility = PatientMocker.facility()
    whenever(facilityRepository.associateUserWithFacility(user, newFacility)).thenReturn(Completable.complete())
    whenever(facilityRepository.setCurrentFacility(user, newFacility)).thenReturn(Completable.complete())
    whenever(reportsRepository.deleteReportsFile()).thenReturn(deleteReport)
    whenever(reportsSync.sync()).thenReturn(reportsSyncCompletable)

    uiEvents.onNext(FacilityChangeClicked(newFacility))

    deleteReport.test().assertSubscribed()
    reportsSyncCompletable.test().assertSubscribed()
    verify(screen).goBack()
  }

  @Suppress("Unused")
  private fun `params for when a facility is changed then the report has to be deleted and synced`(): List<List<Any>> =
      listOf(
          listOf(Single.just(DeleteFileResult.Success), Completable.complete()),
          listOf(Single.just(DeleteFileResult.Success), Completable.error(Exception())),
          listOf(Single.just(DeleteFileResult.Failure(Exception())), Completable.complete()),
          listOf(Single.just(DeleteFileResult.Failure(Exception())), Completable.error(Exception()))
      )

  @Test
  fun `when screen is started and location permission is available then location should be fetched`() {
    configProvider.onNext(configTemplate.copy(locationUpdateInterval = Duration.ofDays(5)))
    whenever(locationRepository.streamUserLocation(any(), any())).thenReturn(Observable.never())

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(FacilityChangeLocationPermissionChanged(RuntimePermissionResult.GRANTED))

    verify(locationRepository).streamUserLocation(updateInterval = eq(Duration.ofDays(5)), updateScheduler = any())
  }

  @Test
  @Parameters(method = "params for permission denials")
  fun `when screen is started and location permission was denied then location should not be fetched and facilities should be shown`(
      deniedResult: RuntimePermissionResult
  ) {
    val facilities = listOf(
        PatientMocker.facility(name = "Facility 1"),
        PatientMocker.facility(name = "Facility 2"))
    whenever(facilityRepository.facilitiesInCurrentGroup(any(), any())).thenReturn(Observable.just(facilities))
    whenever(locationRepository.streamUserLocation(any(), any())).thenReturn(Observable.never())

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(FacilityChangeSearchQueryChanged(""))
    uiEvents.onNext(FacilityChangeLocationPermissionChanged(deniedResult))

    verify(locationRepository, never()).streamUserLocation(any(), any())
    verify(screen).updateFacilities(any(), any())
  }

  @Suppress("unused")
  fun `params for permission denials`(): List<RuntimePermissionResult> {
    return listOf(RuntimePermissionResult.DENIED, RuntimePermissionResult.NEVER_ASK_AGAIN)
  }

  @Test
  fun `when screen is started then location should only be read once`() {
    configProvider.onNext(configTemplate.copy(locationListenerExpiry = Duration.ofSeconds(5)))

    val facilities = listOf(
        PatientMocker.facility(name = "Facility 1"),
        PatientMocker.facility(name = "Facility 2"))
    whenever(facilityRepository.facilitiesInCurrentGroup(any(), any())).thenReturn(Observable.just(facilities))

    val timeSinceBootWhenRecorded = Duration.ofMillis(elapsedRealtimeClock.millis())
    whenever(locationRepository.streamUserLocation(any(), any())).thenReturn(
        Observable.just(
            LocationUpdate.Available(Coordinates(0.0, 0.0), timeSinceBootWhenRecorded),
            LocationUpdate.Unavailable,
            LocationUpdate.Available(Coordinates(0.0, 0.0), timeSinceBootWhenRecorded)))

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(FacilityChangeSearchQueryChanged(""))
    uiEvents.onNext(FacilityChangeLocationPermissionChanged(RuntimePermissionResult.GRANTED))

    testComputationScheduler.advanceTimeBy(6, TimeUnit.SECONDS)

    verify(locationRepository).streamUserLocation(any(), any())
    verify(screen, times(1)).updateFacilities(any(), any())
  }

  @Test
  fun `when the user's location updates are received then only one recent update should be read`() {
    val config = configTemplate.copy(staleLocationThreshold = Duration.ofMinutes(10))
    configProvider.onNext(config)

    val facilities = listOf(PatientMocker.facility(name = "Facility 1"))
    whenever(facilityRepository.facilitiesInCurrentGroup(any(), any())).thenReturn(Observable.just(facilities))

    val locationUpdates = PublishSubject.create<LocationUpdate>()
    whenever(locationRepository.streamUserLocation(any(), any())).thenReturn(locationUpdates)

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(FacilityChangeSearchQueryChanged(""))
    uiEvents.onNext(FacilityChangeLocationPermissionChanged(RuntimePermissionResult.GRANTED))

    val locationOlderThanStaleThreshold = LocationUpdate.Available(
        location = Coordinates(0.0, 0.0),
        timeSinceBootWhenRecorded = Duration.ofMillis(elapsedRealtimeClock.millis()))

    elapsedRealtimeClock.advanceBy(config.staleLocationThreshold + Duration.ofSeconds(1))

    locationUpdates.onNext(locationOlderThanStaleThreshold)
    verify(screen, never()).updateFacilities(any(), any())

    locationUpdates.onNext(locationOlderThanStaleThreshold)
    verify(screen, never()).updateFacilities(any(), any())

    elapsedRealtimeClock.advanceBy(config.staleLocationThreshold)

    val locationNewerThanStaleThreshold = LocationUpdate.Available(
        location = Coordinates(0.0, 0.0),
        timeSinceBootWhenRecorded = Duration.ofMillis(elapsedRealtimeClock.millis()))

    locationUpdates.onNext(locationNewerThanStaleThreshold)
    verify(screen).updateFacilities(any(), any())
  }

  @Test
  @Parameters("5", "6", "7", "8")
  fun `when location is being fetched then it should expire after a fixed time duration`(
      secondsSpentWaitingForLocation: Long
  ) {
    configProvider.onNext(configTemplate.copy(locationListenerExpiry = Duration.ofSeconds(5)))

    whenever(facilityRepository.facilitiesInCurrentGroup(any(), any())).thenReturn(Observable.just(emptyList()))
    whenever(locationRepository.streamUserLocation(any(), any())).thenReturn(Observable.never())

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(FacilityChangeLocationPermissionChanged(RuntimePermissionResult.GRANTED))
    verify(screen).showProgressIndicator()

    testComputationScheduler.advanceTimeBy(secondsSpentWaitingForLocation, TimeUnit.SECONDS)
    verify(screen).hideProgressIndicator()
  }

  @Test
  fun `when screen starts and location permission is available then progress indicator should be shown`() {
    whenever(locationRepository.streamUserLocation(any(), any())).thenReturn(Observable.never())

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(FacilityChangeLocationPermissionChanged(RuntimePermissionResult.GRANTED))
    uiEvents.onNext(FacilityChangeLocationPermissionChanged(RuntimePermissionResult.GRANTED))

    verify(screen, times(1)).showProgressIndicator()
  }

  @Test
  @Parameters(method = "params for permission denials")
  fun `when screen starts and location permission was denied then progress indicator should not be shown`(
      deniedResult: RuntimePermissionResult
  ) {
    whenever(locationRepository.streamUserLocation(any(), any())).thenReturn(Observable.never())

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(FacilityChangeLocationPermissionChanged(deniedResult))

    verify(screen, never()).showProgressIndicator()
  }

  @Test
  @Parameters(method = "params for location updates")
  fun `when a location update is received then progress indicator should be hidden`(
      locationUpdate: LocationUpdate
  ) {
    whenever(locationRepository.streamUserLocation(any(), any())).thenReturn(Observable.just(locationUpdate))

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(FacilityChangeLocationPermissionChanged(RuntimePermissionResult.GRANTED))

    verify(screen).hideProgressIndicator()
  }

  @Suppress("unused")
  fun `params for location updates`() = listOf(
      LocationUpdate.Unavailable,
      LocationUpdate.Available(location = Coordinates(0.0, 0.0), timeSinceBootWhenRecorded = Duration.ofNanos(0))
  )

  @Test
  fun `when both facilities and location are fetched only then should facilities be shown`() {
    configProvider.onNext(configTemplate.copy(locationListenerExpiry = Duration.ofSeconds(5)))

    val facilities = listOf(
        PatientMocker.facility(name = "Facility 1"),
        PatientMocker.facility(name = "Facility 2"))
    whenever(facilityRepository.facilitiesInCurrentGroup(any(), any())).thenReturn(Observable.just(facilities))

    val locationUpdates = PublishSubject.create<LocationUpdate>()
    whenever(locationRepository.streamUserLocation(any(), any())).thenReturn(locationUpdates)

    uiEvents.run {
      onNext(ScreenCreated())
      onNext(FacilityChangeSearchQueryChanged(""))
      onNext(FacilityChangeLocationPermissionChanged(RuntimePermissionResult.GRANTED))
    }
    verify(screen, never()).updateFacilities(any(), any())

    locationUpdates.onNext(LocationUpdate.Available(Coordinates(0.0, 0.0), timeSinceBootWhenRecorded = Duration.ofNanos(0)))
    verify(screen).updateFacilities(any(), any())
  }

  @Test
  fun `when facilities are fetched, but location listener expires then facilities should still be shown`() {
    configProvider.onNext(configTemplate.copy(locationListenerExpiry = Duration.ofSeconds(5)))

    val facilities = listOf(
        PatientMocker.facility(name = "Facility 1"),
        PatientMocker.facility(name = "Facility 2"))
    whenever(facilityRepository.facilitiesInCurrentGroup(any(), any())).thenReturn(Observable.just(facilities))
    whenever(locationRepository.streamUserLocation(any(), any())).thenReturn(Observable.never())

    uiEvents.run {
      onNext(ScreenCreated())
      onNext(FacilityChangeSearchQueryChanged("f"))
      onNext(FacilityChangeLocationPermissionChanged(RuntimePermissionResult.GRANTED))
    }
    verify(screen, never()).updateFacilities(any(), any())

    testComputationScheduler.advanceTimeBy(6, TimeUnit.SECONDS)
    verify(screen).updateFacilities(any(), any())
  }

  @Test
  @Parameters(method = "params for user location updates")
  fun `search field should only be shown when a user location update is received`(
      locationUpdate: LocationUpdate
  ) {
    uiEvents.onNext(ScreenCreated())

    val inOrder = inOrder(screen)
    inOrder.verify(screen).showToolbarWithoutSearchField()

    uiEvents.onNext(FacilityChangeUserLocationUpdated(LocationUpdate.Unavailable))
    inOrder.verify(screen).showToolbarWithSearchField()
  }

  @Suppress("unused")
  fun `params for user location updates`(): List<Any> {
    return listOf(
        LocationUpdate.Unavailable,
        LocationUpdate.Available(location = Coordinates(0.0, 0.0), timeSinceBootWhenRecorded = Duration.ofNanos(0)))
  }

}
