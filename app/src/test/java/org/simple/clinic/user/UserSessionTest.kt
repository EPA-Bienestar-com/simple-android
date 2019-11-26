package org.simple.clinic.user

import android.content.SharedPreferences
import com.f2prateek.rx.preferences2.Preference
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.analytics.Analytics
import org.simple.clinic.analytics.MockAnalyticsReporter
import org.simple.clinic.appconfig.Country
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.security.PasswordHasher
import org.simple.clinic.security.pin.BruteForceProtection
import org.simple.clinic.storage.files.ClearAllFilesResult
import org.simple.clinic.storage.files.FileStorage
import org.simple.clinic.sync.DataSync
import org.simple.clinic.user.User.LoggedInStatus.LOGGED_IN
import org.simple.clinic.user.User.LoggedInStatus.NOT_LOGGED_IN
import org.simple.clinic.user.User.LoggedInStatus.OTP_REQUESTED
import org.simple.clinic.user.User.LoggedInStatus.RESETTING_PIN
import org.simple.clinic.user.User.LoggedInStatus.RESET_PIN_REQUESTED
import org.simple.clinic.user.User.LoggedInStatus.UNAUTHORIZED
import org.simple.clinic.user.UserStatus.ApprovedForSyncing
import org.simple.clinic.user.UserStatus.DisapprovedForSyncing
import org.simple.clinic.user.UserStatus.WaitingForApproval
import org.simple.clinic.util.Optional
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.assertLatestValue
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import java.io.IOException
import java.util.UUID

@RunWith(JUnitParamsRunner::class)
class UserSessionTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val accessTokenPref = mock<Preference<Optional<String>>>()
  private val facilityRepository = mock<FacilityRepository>()
  private val patientRepository = mock<PatientRepository>()
  private val sharedPrefs = mock<SharedPreferences>()
  private val appDatabase = mock<AppDatabase>()
  private val passwordHasher = mock<PasswordHasher>()
  private val userDao = mock<User.RoomDao>()
  private val reporter = MockAnalyticsReporter()
  private val ongoingLoginEntryRepository = mock<OngoingLoginEntryRepository>()
  private var bruteForceProtection = mock<BruteForceProtection>()

  private val dataSync = mock<DataSync>()
  private val dataSyncLazy = dagger.Lazy { dataSync }
  private val medicalHistoryPullToken = mock<Preference<Optional<String>>>()
  private val appointmentPullToken = mock<Preference<Optional<String>>>()
  private val prescriptionPullToken = mock<Preference<Optional<String>>>()
  private val bpPullToken = mock<Preference<Optional<String>>>()
  private val patientPullToken = mock<Preference<Optional<String>>>()
  private val fileStorage = mock<FileStorage>()
  private val reportPendingRecords = mock<ReportPendingRecordsToAnalytics>()
  private val onboardingCompletePreference = mock<Preference<Boolean>>()
  private val selectedCountryPreference = mock<Preference<Optional<Country>>>()
  private val userUuid: UUID = UUID.fromString("866bccab-0117-4471-9d5d-cf6f2f1a64c1")
  private val schedulersProvider = TrampolineSchedulersProvider()

  private val userSession = UserSession(
      facilityRepository = facilityRepository,
      sharedPreferences = sharedPrefs,
      appDatabase = appDatabase,
      passwordHasher = passwordHasher,
      dataSync = dataSyncLazy,
      ongoingLoginEntryRepository = ongoingLoginEntryRepository,
      bruteForceProtection = bruteForceProtection,
      fileStorage = fileStorage,
      reportPendingRecords = reportPendingRecords,
      schedulersProvider = schedulersProvider,
      selectedCountryPreference = selectedCountryPreference,
      accessTokenPreference = accessTokenPref,
      patientSyncPullToken = patientPullToken,
      bpSyncPullToken = bpPullToken,
      prescriptionSyncPullToken = prescriptionPullToken,
      appointmentSyncPullToken = appointmentPullToken,
      medicalHistorySyncPullToken = medicalHistoryPullToken,
      onboardingComplete = onboardingCompletePreference
  )

  @Before
  fun setUp() {
    whenever(patientRepository.clearPatientData()).thenReturn(Completable.never())
    whenever(appDatabase.userDao()).thenReturn(userDao)
    whenever(facilityRepository.associateUserWithFacilities(any(), any(), any())).thenReturn(Completable.never())
    whenever(ongoingLoginEntryRepository.entry()).thenReturn(Single.never())
    whenever(bruteForceProtection.resetFailedAttempts()).thenReturn(Completable.never())
    whenever(userDao.user()).thenReturn(Flowable.never())

    Analytics.addReporter(reporter)
  }

  @After
  fun tearDown() {
    reporter.clear()
    Analytics.clearReporters()
  }

  @Test
  fun `when ongoing registration entry is cleared then isOngoingRegistrationEntryPresent() should emit false`() {
    userSession.saveOngoingRegistrationEntry(OngoingRegistrationEntry())
        .andThen(userSession.clearOngoingRegistrationEntry())
        .andThen(userSession.isOngoingRegistrationEntryPresent())
        .test()
        .await()
        .assertValue(false)
  }

  @Test
  fun `user approved for syncing changes should be notified correctly`() {
    fun createUser(loggedInStatus: User.LoggedInStatus, userStatus: UserStatus): List<User> {
      return listOf(PatientMocker.loggedInUser(status = userStatus, loggedInStatus = loggedInStatus))
    }

    val userSubject = PublishSubject.create<List<User>>()
    whenever(userDao.user())
        .thenReturn(userSubject.toFlowable(BackpressureStrategy.BUFFER))

    val observer = userSession.canSyncData().test()

    userSubject.apply {
      onNext(createUser(loggedInStatus = LOGGED_IN, userStatus = WaitingForApproval))
      observer.assertLatestValue(false)

      onNext(createUser(loggedInStatus = LOGGED_IN, userStatus = ApprovedForSyncing))
      observer.assertLatestValue(true)

      onNext(createUser(loggedInStatus = LOGGED_IN, userStatus = DisapprovedForSyncing))
      observer.assertLatestValue(false)

      onNext(createUser(loggedInStatus = NOT_LOGGED_IN, userStatus = WaitingForApproval))
      observer.assertLatestValue(false)
      onNext(createUser(loggedInStatus = NOT_LOGGED_IN, userStatus = ApprovedForSyncing))
      observer.assertLatestValue(false)
      onNext(createUser(loggedInStatus = NOT_LOGGED_IN, userStatus = DisapprovedForSyncing))
      observer.assertLatestValue(false)

      onNext(createUser(loggedInStatus = LOGGED_IN, userStatus = ApprovedForSyncing))
      observer.assertLatestValue(true)

      onNext(createUser(loggedInStatus = OTP_REQUESTED, userStatus = WaitingForApproval))
      observer.assertLatestValue(false)
      onNext(createUser(loggedInStatus = OTP_REQUESTED, userStatus = ApprovedForSyncing))
      observer.assertLatestValue(false)
      onNext(createUser(loggedInStatus = OTP_REQUESTED, userStatus = DisapprovedForSyncing))
      observer.assertLatestValue(false)

      onNext(emptyList())
      observer.assertLatestValue(false)

      onNext(createUser(loggedInStatus = LOGGED_IN, userStatus = ApprovedForSyncing))
      observer.assertLatestValue(true)

      onNext(createUser(loggedInStatus = RESETTING_PIN, userStatus = WaitingForApproval))
      observer.assertLatestValue(false)
      onNext(createUser(loggedInStatus = RESETTING_PIN, userStatus = ApprovedForSyncing))
      observer.assertLatestValue(false)
      onNext(createUser(loggedInStatus = RESETTING_PIN, userStatus = DisapprovedForSyncing))
      observer.assertLatestValue(false)

      onNext(createUser(loggedInStatus = LOGGED_IN, userStatus = ApprovedForSyncing))
      observer.assertLatestValue(true)

      onNext(createUser(loggedInStatus = RESET_PIN_REQUESTED, userStatus = WaitingForApproval))
      observer.assertLatestValue(false)
      onNext(createUser(loggedInStatus = RESET_PIN_REQUESTED, userStatus = ApprovedForSyncing))
      observer.assertLatestValue(false)
      onNext(createUser(loggedInStatus = RESET_PIN_REQUESTED, userStatus = DisapprovedForSyncing))
      observer.assertLatestValue(false)

      onNext(createUser(loggedInStatus = LOGGED_IN, userStatus = ApprovedForSyncing))
      observer.assertLatestValue(true)

      onNext(emptyList())
      observer.assertLatestValue(false)
    }
  }

  @Test
  fun `logout should work as expected`() {
    whenever(fileStorage.clearAllFiles()).thenReturn(ClearAllFilesResult.Success)
    val preferencesEditor = mock<SharedPreferences.Editor>()
    whenever(preferencesEditor.clear()).thenReturn(preferencesEditor)
    whenever(sharedPrefs.edit()).thenReturn(preferencesEditor)
    whenever(preferencesEditor.putString(eq("key"), any())) doReturn preferencesEditor
    whenever(selectedCountryPreference.key()) doReturn "key"
    whenever(sharedPrefs.getString(eq("key"), any())) doReturn ""
    var pendingRecordsReported = false
    whenever(reportPendingRecords.report()).thenReturn(Completable.complete().doOnSubscribe { pendingRecordsReported = true })

    val result = userSession.logout().blockingGet()

    assertThat(result).isSameAs(UserSession.LogoutResult.Success)

    verify(fileStorage).clearAllFiles()

    val inorderForPreferences = inOrder(preferencesEditor, onboardingCompletePreference)
    inorderForPreferences.verify(preferencesEditor).clear()
    inorderForPreferences.verify(preferencesEditor).apply()
    inorderForPreferences.verify(onboardingCompletePreference).set(true)

    val inorderForDatabase = inOrder(reportPendingRecords, appDatabase)
    inorderForDatabase.verify(reportPendingRecords).report()
    inorderForDatabase.verify(appDatabase).clearAllTables()

    assertThat(pendingRecordsReported).isTrue()
  }

  @Test
  fun `when clearing private files works partially the logout must succeed`() {
    whenever(fileStorage.clearAllFiles()).thenReturn(ClearAllFilesResult.PartiallyDeleted)
    whenever(reportPendingRecords.report()).thenReturn(Completable.complete())
    val preferencesEditor = mock<SharedPreferences.Editor>()
    whenever(preferencesEditor.clear()).thenReturn(preferencesEditor)
    whenever(sharedPrefs.edit()).thenReturn(preferencesEditor)
    whenever(preferencesEditor.putString(eq("key"), any())) doReturn preferencesEditor
    whenever(selectedCountryPreference.key()) doReturn "key"
    whenever(sharedPrefs.getString(eq("key"), any())) doReturn ""

    val result = userSession.logout().blockingGet()

    assertThat(result).isSameAs(UserSession.LogoutResult.Success)
  }

  @Test
  @Parameters(method = "params for logout clear files failures")
  fun `when clearing private files fails the logout must fail`(cause: Throwable) {
    whenever(fileStorage.clearAllFiles()).thenReturn(ClearAllFilesResult.Failure(cause))
    whenever(reportPendingRecords.report()).thenReturn(Completable.complete())
    val preferencesEditor = mock<SharedPreferences.Editor>()
    whenever(preferencesEditor.clear()).thenReturn(preferencesEditor)
    whenever(sharedPrefs.edit()).thenReturn(preferencesEditor)
    whenever(preferencesEditor.putString(eq("key"), any())) doReturn preferencesEditor
    whenever(selectedCountryPreference.key()) doReturn "key"
    whenever(sharedPrefs.getString(eq("key"), any())) doReturn ""

    val result = userSession.logout().blockingGet()

    assertThat(result).isEqualTo(UserSession.LogoutResult.Failure(cause))
  }

  @Suppress("Unused")
  private fun `params for logout clear files failures`(): List<Any> {
    return listOf(IOException(), RuntimeException())
  }

  @Test
  @Parameters(method = "params for logout clear preferences failures")
  fun `when clearing shared preferences fails, the logout must fail`(cause: Throwable) {
    whenever(fileStorage.clearAllFiles()).thenReturn(ClearAllFilesResult.Success)
    whenever(reportPendingRecords.report()).thenReturn(Completable.complete())
    val preferencesEditor = mock<SharedPreferences.Editor>()
    whenever(preferencesEditor.clear()).thenReturn(preferencesEditor)
    whenever(preferencesEditor.apply()).thenThrow(cause)
    whenever(sharedPrefs.edit()).thenReturn(preferencesEditor)
    whenever(preferencesEditor.putString(eq("key"), any())) doReturn preferencesEditor
    whenever(selectedCountryPreference.key()) doReturn "key"
    whenever(sharedPrefs.getString(eq("key"), any())) doReturn ""

    val result = userSession.logout().blockingGet()

    assertThat(result).isEqualTo(UserSession.LogoutResult.Failure(cause))
  }

  @Suppress("Unused")
  private fun `params for logout clear preferences failures`(): List<Any> {
    return listOf(NullPointerException(), RuntimeException())
  }

  @Test
  @Parameters(method = "params for logout clear database failures")
  fun `when clearing app database fails, the logout must fail`(cause: Throwable) {
    whenever(fileStorage.clearAllFiles()).thenReturn(ClearAllFilesResult.Success)
    whenever(reportPendingRecords.report()).thenReturn(Completable.complete())
    val preferencesEditor = mock<SharedPreferences.Editor>()
    whenever(preferencesEditor.clear()).thenReturn(preferencesEditor)
    whenever(sharedPrefs.edit()).thenReturn(preferencesEditor)
    whenever(appDatabase.clearAllTables()).thenThrow(cause)

    val result = userSession.logout().blockingGet()

    assertThat(result).isEqualTo(UserSession.LogoutResult.Failure(cause))
  }

  @Suppress("Unused")
  private fun `params for logout clear database failures`(): List<Any> {
    return listOf(NullPointerException(), RuntimeException())
  }

  @Test
  @Parameters(method = "params for failures during logout when pending sync records fails")
  fun `when reporting pending records fails, logout must not be affected`(cause: Throwable) {
    whenever(fileStorage.clearAllFiles()).thenReturn(ClearAllFilesResult.Success)
    whenever(reportPendingRecords.report()).thenReturn(Completable.error(cause))
    val preferencesEditor = mock<SharedPreferences.Editor>()
    whenever(preferencesEditor.clear()).thenReturn(preferencesEditor)
    whenever(sharedPrefs.edit()).thenReturn(preferencesEditor)
    whenever(preferencesEditor.putString(eq("key"), any())) doReturn preferencesEditor
    whenever(selectedCountryPreference.key()) doReturn "key"
    whenever(sharedPrefs.getString(eq("key"), any())) doReturn ""

    val result = userSession.logout().blockingGet()

    verify(fileStorage).clearAllFiles()

    val inorderForPreferences = inOrder(preferencesEditor, onboardingCompletePreference)
    inorderForPreferences.verify(preferencesEditor).clear()
    inorderForPreferences.verify(preferencesEditor).apply()
    inorderForPreferences.verify(onboardingCompletePreference).set(true)

    verify(appDatabase).clearAllTables()

    assertThat(result).isEqualTo(UserSession.LogoutResult.Success)
  }

  @Suppress("Unused")
  private fun `params for failures during logout when pending sync records fails`(): List<Any> {
    return listOf(NullPointerException(), RuntimeException())
  }

  @Test
  @Parameters(method = "params for checking if user is unauthorized")
  fun `checking whether the user is unauthorized should work as expected`(
      loggedInStatus: List<User.LoggedInStatus>,
      expectedIsUnauthorized: List<Boolean>
  ) {
    val user = PatientMocker
        .loggedInUser()
        .let { userTemplate ->
          loggedInStatus.map { userTemplate.copy(loggedInStatus = it) }
        }
        .map { listOf(it) }

    whenever(userDao.user()).thenReturn(Flowable.fromIterable(user))

    val isUnauthorized = userSession.isUserUnauthorized().blockingIterable().toList()

    assertThat(isUnauthorized).isEqualTo(expectedIsUnauthorized)
  }

  @Suppress("Unused")
  private fun `params for checking if user is unauthorized`(): List<List<Any>> {
    fun testCase(
        loggedInStatus: List<User.LoggedInStatus>,
        expectedIsUnauthorized: List<Boolean>
    ) = listOf(loggedInStatus, expectedIsUnauthorized)

    return listOf(
        testCase(
            loggedInStatus = listOf(NOT_LOGGED_IN),
            expectedIsUnauthorized = listOf(false)
        ),
        testCase(
            loggedInStatus = listOf(NOT_LOGGED_IN, NOT_LOGGED_IN),
            expectedIsUnauthorized = listOf(false)
        ),
        testCase(
            loggedInStatus = listOf(OTP_REQUESTED),
            expectedIsUnauthorized = listOf(false)
        ),
        testCase(
            loggedInStatus = listOf(OTP_REQUESTED, OTP_REQUESTED),
            expectedIsUnauthorized = listOf(false)
        ),
        testCase(
            loggedInStatus = listOf(LOGGED_IN),
            expectedIsUnauthorized = listOf(false)
        ),
        testCase(
            loggedInStatus = listOf(LOGGED_IN, LOGGED_IN),
            expectedIsUnauthorized = listOf(false)
        ),
        testCase(
            loggedInStatus = listOf(RESETTING_PIN),
            expectedIsUnauthorized = listOf(false)
        ),
        testCase(
            loggedInStatus = listOf(RESETTING_PIN, RESETTING_PIN),
            expectedIsUnauthorized = listOf(false)
        ),
        testCase(
            loggedInStatus = listOf(RESET_PIN_REQUESTED),
            expectedIsUnauthorized = listOf(false)
        ),
        testCase(
            loggedInStatus = listOf(RESET_PIN_REQUESTED, RESET_PIN_REQUESTED),
            expectedIsUnauthorized = listOf(false)
        ),
        testCase(
            loggedInStatus = listOf(UNAUTHORIZED),
            expectedIsUnauthorized = listOf(true)
        ),
        testCase(
            loggedInStatus = listOf(UNAUTHORIZED, UNAUTHORIZED),
            expectedIsUnauthorized = listOf(true)
        ),
        testCase(
            loggedInStatus = listOf(UNAUTHORIZED, UNAUTHORIZED, UNAUTHORIZED),
            expectedIsUnauthorized = listOf(true)
        ),
        testCase(
            loggedInStatus = listOf(UNAUTHORIZED, UNAUTHORIZED, LOGGED_IN, UNAUTHORIZED, UNAUTHORIZED, LOGGED_IN),
            expectedIsUnauthorized = listOf(true, false, true, false)
        ),
        testCase(
            loggedInStatus = listOf(NOT_LOGGED_IN, UNAUTHORIZED),
            expectedIsUnauthorized = listOf(false, true)
        ),
        testCase(
            loggedInStatus = listOf(NOT_LOGGED_IN, UNAUTHORIZED, LOGGED_IN, UNAUTHORIZED),
            expectedIsUnauthorized = listOf(false, true, false, true)
        ),
        testCase(
            loggedInStatus = listOf(NOT_LOGGED_IN, UNAUTHORIZED, UNAUTHORIZED, LOGGED_IN, LOGGED_IN),
            expectedIsUnauthorized = listOf(false, true, false)
        ),
        testCase(
            loggedInStatus = listOf(NOT_LOGGED_IN, OTP_REQUESTED, LOGGED_IN, UNAUTHORIZED, LOGGED_IN, UNAUTHORIZED),
            expectedIsUnauthorized = listOf(false, true, false, true)
        )
    )
  }

  @Test
  fun `when user logout happens, clear the logged in user from analytics`() {
    // given
    whenever(reportPendingRecords.report()).thenReturn(Completable.complete())

    val user = PatientMocker.loggedInUser(uuid = userUuid)
    reporter.setLoggedInUser(user, false)
    assertThat(reporter.user).isNotNull()

    // when
    userSession.logout().blockingGet()

    // then
    assertThat(reporter.user).isNull()
  }
}
