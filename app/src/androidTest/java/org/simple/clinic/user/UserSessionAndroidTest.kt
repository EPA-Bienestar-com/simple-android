package org.simple.clinic.user

import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.facility.FacilitySyncApiV1
import org.simple.clinic.login.LoginResult
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.registration.FindUserResult
import org.simple.clinic.registration.RegistrationResult
import org.simple.clinic.registration.SaveUserLocallyResult
import org.simple.clinic.util.Just
import java.util.UUID
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class UserSessionAndroidTest {

  @Inject
  lateinit var userSession: UserSession

  @Inject
  lateinit var facilityRepository: FacilityRepository

  @Inject
  lateinit var appDatabase: AppDatabase

  @Inject
  lateinit var testData: TestData

  @Inject
  lateinit var facilityApi: FacilitySyncApiV1

  @Before
  fun setUp() {
    TestClinicApp.appComponent().inject(this)
    userSession.logout().blockingAwait()
  }

  @Test
  fun when_correct_login_params_are_given_then_login_should_happen_and_session_data_should_be_persisted() {
    val lawgon = userSession
        .saveOngoingLoginEntry(testData.qaOngoingLoginEntry())
        .andThen(userSession.loginWithOtp("0000"))
        .blockingGet()

    assertThat(lawgon).isInstanceOf(LoginResult.Success::class.java)
    assertThat(userSession.accessToken()).isEqualTo(Just("7d728cc7e54aa148e84befda6d6d570f67ac60b3410445a1fb0e8d2216fcde44"))

    val (loggedInUser) = userSession.loggedInUser().blockingFirst()
    assertThat(userSession.isUserLoggedIn()).isTrue()
    assertThat(loggedInUser!!.status).isEqualTo(UserStatus.APPROVED_FOR_SYNCING)

    val currentFacility = facilityRepository
        .currentFacility(userSession)
        .blockingFirst()
    assertThat(currentFacility.uuid).isEqualTo(UUID.fromString("43dad34c-139e-4e5f-976e-a3ef1d9ac977"))
  }

  @Test
  fun when_incorrect_login_params_are_given_then_login_should_fail() {
    val lawgon = userSession
        .saveOngoingLoginEntry(OngoingLoginEntry(testData.qaUserUuid(), "9919299", "0102"))
        .andThen(userSession.loginWithOtp("0000"))
        .blockingGet()

    assertThat(lawgon).isInstanceOf(LoginResult.ServerError::class.java)
    assertThat(userSession.isUserLoggedIn()).isFalse()

    val (accessToken) = userSession.accessToken()
    assertThat(accessToken).isNull()
  }

  @Test
  fun when_logging_in_from_registration_entry_user_should_be_logged_in_locally() {
    val facilities = facilityApi.pull(10)
        .map { it.facilities }
        .map { it.map { it.toDatabaseModel(SyncStatus.DONE) } }
        .blockingGet()
    appDatabase.facilityDao().save(facilities)

    val selectedFacilities = facilities.subList(0, 2)
    val ongoingRegistrationEntry = testData.ongoingRegistrationEntry(selectedFacilities)
    userSession.saveOngoingRegistrationEntry(ongoingRegistrationEntry)
        .andThen(userSession.loginFromOngoingRegistrationEntry())
        .blockingAwait()

    assertThat(userSession.isUserLoggedIn()).isTrue()
    val (loggedInUser) = userSession.loggedInUser().blockingFirst()
    assertThat(loggedInUser!!.status).isEqualTo(UserStatus.WAITING_FOR_APPROVAL)

    val currentFacility = facilityRepository
        .currentFacility(userSession)
        .blockingFirst()
    assertThat(currentFacility.uuid).isEqualTo(selectedFacilities.first().uuid)

    val isRegistrationEntryPresent = userSession.isOngoingRegistrationEntryPresent().blockingGet()
    assertThat(isRegistrationEntryPresent).isFalse()
  }

  @Test
  fun when_user_is_logged_out_then_all_app_data_should_get_cleared() {
    userSession
        .saveOngoingLoginEntry(testData.qaOngoingLoginEntry())
        .andThen(userSession.loginWithOtp("0000"))
        .toCompletable()
        .andThen(userSession.logout())
        .blockingAwait()

    val (loggedInUser) = userSession.loggedInUser().blockingFirst()
    assertThat(loggedInUser).isNull()
    assertThat(userSession.isUserLoggedIn()).isFalse()
  }

  @Test
  fun when_registering_a_user_is_registered_then_the_logged_in_user_should_be_sent_to_the_server() {
    val facilities = facilityApi.pull(10)
        .map { it.facilities }
        .map { it.map { it.toDatabaseModel(SyncStatus.DONE) } }
        .blockingGet()
    appDatabase.facilityDao().save(facilities)

    val selectedFacilities = facilities.subList(0, 2)
    val ongoingRegistrationEntry = testData.ongoingRegistrationEntry(selectedFacilities)

    val registrationResult = userSession
        .saveOngoingRegistrationEntry(ongoingRegistrationEntry)
        .andThen(userSession.loginFromOngoingRegistrationEntry())
        .andThen(userSession.register())
        .blockingGet()

    assertThat(registrationResult).isInstanceOf(RegistrationResult.Success::class.java)
    val (accessToken) = userSession.accessToken()
    assertThat(accessToken).isNotNull()
  }

  @Test
  fun when_saving_a_user_locally_it_should_save_the_user_locally_with_a_status_of_not_signed_in() {
    val findUserResult = userSession.findExistingUser(testData.qaOngoingLoginEntry().phoneNumber).blockingGet()
    assertThat(findUserResult).isInstanceOf(FindUserResult.Found::class.java)

    val foundUserPayload = (findUserResult as FindUserResult.Found).user
    val result = userSession.syncFacilityAndSaveUser(foundUserPayload).blockingGet()

    assertThat(result).isInstanceOf(SaveUserLocallyResult::class.java)

    val user = appDatabase.userDao().userImmediate()!!
    assertThat(user.uuid).isEqualTo(foundUserPayload.uuid)
    assertThat(user.loggedInStatus).isEqualTo(User.LoggedInStatus.NOT_LOGGED_IN)

    val facilityUUIDsForUser = appDatabase.userFacilityMappingDao().facilityUuids(user.uuid).blockingFirst()
    assertThat(facilityUUIDsForUser.size).isEqualTo(foundUserPayload.facilityUuids.size)
    assertThat(facilityUUIDsForUser).containsAllIn(foundUserPayload.facilityUuids)
  }
}
