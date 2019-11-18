package org.simple.clinic.login

import com.f2prateek.rx.preferences2.Preference
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.moshi.Moshi
import dagger.Lazy
import io.reactivex.Completable
import io.reactivex.Single
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.analytics.Analytics
import org.simple.clinic.analytics.MockAnalyticsReporter
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.login.LoginResult.NetworkError
import org.simple.clinic.login.LoginResult.ServerError
import org.simple.clinic.login.LoginResult.Success
import org.simple.clinic.login.LoginResult.UnexpectedError
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.sync.DataSync
import org.simple.clinic.user.User
import org.simple.clinic.user.User.LoggedInStatus.LOGGED_IN
import org.simple.clinic.util.Just
import org.simple.clinic.util.Optional
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.scheduler.SchedulersProvider
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import org.simple.clinic.util.toUser
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class LoginUserWithOtpTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val loginApi: LoginApi = mock()
  private val dataSync: DataSync = mock()
  private val dataSyncLazy: Lazy<DataSync> = Lazy { dataSync }
  private val userDao: User.RoomDao = mock()
  private val facilityRepository = mock<FacilityRepository>()
  private val schedulersProvider: SchedulersProvider = TrampolineSchedulersProvider()
  private val moshi = Moshi.Builder().build()
  private val accessTokenPref = mock<Preference<Optional<String>>>()
  private val analyticsReporter: MockAnalyticsReporter = MockAnalyticsReporter()

  private val phoneNumber = "1234567890"
  private val pin = "1234"
  private val otp = "000000"
  private val loginRequest = LoginRequest(UserPayload(phoneNumber, pin, otp))

  private val userUuid = UUID.fromString("34bf96ef-06f3-4d1c-9ef1-9a55cb1904e9")
  private val facilityUuid = UUID.fromString("18bd9def-ab06-491f-97bf-8fbd74f213cd")
  private val accessToken = "token"
  private val loggedInUserPayload = PatientMocker.loggedInUserPayload(uuid = userUuid, registrationFacilityUuid = facilityUuid)
  private val loginResponse = LoginResponse(accessToken, loggedInUserPayload)

  private val loginUserWithOtp = LoginUserWithOtp(
      loginApi = loginApi,
      dataSync = dataSyncLazy,
      userDao = userDao,
      facilityRepository = facilityRepository,
      schedulersProvider = schedulersProvider,
      moshi = moshi,
      accessTokenPreference = accessTokenPref
  )

  @Before
  fun setUp() {
    Analytics.addReporter(analyticsReporter)
  }

  @After
  fun tearDown() {
    Analytics.clearReporters()
  }

  @Test
  fun `when the login call is successful, the access token must be saved`() {
    // given
    whenever(loginApi.login(loginRequest)) doReturn Single.just(loginResponse)
    whenever(facilityRepository.associateUserWithFacilities(loggedInUserPayload.toUser(LOGGED_IN), listOf(facilityUuid), facilityUuid)) doReturn Completable.complete()
    whenever(dataSync.syncTheWorld()) doReturn Completable.complete()

    // when
    val loginResult = loginUserWithOtp.loginWithOtp(phoneNumber = phoneNumber, pin = pin, otp = otp).blockingGet()

    // then
    verify(accessTokenPref).set(Just(accessToken))
    assertThat(loginResult).isEqualTo(Success)
  }

  @Test
  fun `when the login call is successful, the user must be saved`() {
    // given
    whenever(loginApi.login(loginRequest)) doReturn Single.just(loginResponse)
    val user = loggedInUserPayload.toUser(LOGGED_IN)
    whenever(facilityRepository.associateUserWithFacilities(user, listOf(facilityUuid), facilityUuid)) doReturn Completable.complete()
    whenever(dataSync.syncTheWorld()) doReturn Completable.complete()

    // when
    val loginResult = loginUserWithOtp.loginWithOtp(phoneNumber = phoneNumber, pin = pin, otp = otp).blockingGet()

    // then
    verify(userDao).createOrUpdate(user)
    assertThat(loginResult).isEqualTo(Success)
  }

  @Test
  fun `when the login call is successful, the user must be reported to analytics`() {
    // given
    whenever(loginApi.login(loginRequest)) doReturn Single.just(loginResponse)
    val user = loggedInUserPayload.toUser(LOGGED_IN)
    whenever(facilityRepository.associateUserWithFacilities(user, listOf(facilityUuid), facilityUuid)) doReturn Completable.complete()
    whenever(dataSync.syncTheWorld()) doReturn Completable.complete()

    // when
    val loginResult = loginUserWithOtp.loginWithOtp(phoneNumber = phoneNumber, pin = pin, otp = otp).blockingGet()

    // then
    assertThat(analyticsReporter.user).isEqualTo(user)
    assertThat(loginResult).isEqualTo(Success)
  }

  @Test
  fun `when the login call is successful, sync all data`() {
    // given
    var allDataSynced = false
    whenever(loginApi.login(loginRequest)) doReturn Single.just(loginResponse)
    val user = loggedInUserPayload.toUser(LOGGED_IN)
    whenever(facilityRepository.associateUserWithFacilities(user, listOf(facilityUuid), facilityUuid)) doReturn Completable.complete()
    whenever(dataSync.syncTheWorld()) doReturn Completable.fromAction { allDataSynced = true }

    // when
    val loginResult = loginUserWithOtp.loginWithOtp(phoneNumber = phoneNumber, pin = pin, otp = otp).blockingGet()

    // then
    assertThat(analyticsReporter.user).isEqualTo(user)
    assertThat(allDataSynced).isTrue()
    assertThat(loginResult).isEqualTo(Success)
  }

  @Test
  fun `when the login call fails with a network error, the network error result must be returned`() {
    // given
    whenever(loginApi.login(loginRequest)) doReturn Single.error<LoginResponse>(IOException())

    // when
    val loginResult = loginUserWithOtp.loginWithOtp(phoneNumber = phoneNumber, pin = pin, otp = otp).blockingGet()

    // then
    verifyZeroInteractions(accessTokenPref)
    verifyZeroInteractions(userDao)
    verifyZeroInteractions(facilityRepository)
    assertThat(analyticsReporter.user).isNull()
    assertThat(loginResult).isEqualTo(NetworkError)
  }

  @Test
  fun `when the login call fails with an unauthenticated error, the server error result must be returned`() {
    // given
    // TODO(vs): 2019-11-15 This is tied to the implementation detail, extract to another class
    val errorReason = "user is not present"
    val errorJson = """{
        "errors": {
          "user": [
            "$errorReason"
          ]
        }
      }"""
    val error = HttpException(Response.error<LoginResponse>(401, ResponseBody.create(MediaType.parse("text"), errorJson)))
    whenever(loginApi.login(loginRequest)) doReturn Single.error<LoginResponse>(error)

    // when
    val loginResult = loginUserWithOtp.loginWithOtp(phoneNumber = phoneNumber, pin = pin, otp = otp).blockingGet()

    // then
    verifyZeroInteractions(accessTokenPref)
    verifyZeroInteractions(userDao)
    verifyZeroInteractions(facilityRepository)
    assertThat(analyticsReporter.user).isNull()
    assertThat(loginResult).isEqualTo(ServerError(errorReason))
  }

  @Test
  fun `when the login call fails with any other error, a generic error response should be returned`() {
    // given
    whenever(loginApi.login(loginRequest)) doReturn Single.error<LoginResponse>(RuntimeException())

    // when
    val loginResult = loginUserWithOtp.loginWithOtp(phoneNumber = phoneNumber, pin = pin, otp = otp).blockingGet()

    // then
    verifyZeroInteractions(accessTokenPref)
    verifyZeroInteractions(userDao)
    verifyZeroInteractions(facilityRepository)
    assertThat(analyticsReporter.user).isNull()
    assertThat(loginResult).isEqualTo(UnexpectedError)
  }
}
