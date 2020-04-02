package org.simple.clinic.registration.phone

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.clearInvocations
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.facility.FacilityPullResult
import org.simple.clinic.facility.FacilitySync
import org.simple.clinic.user.OngoingLoginEntry
import org.simple.clinic.user.OngoingRegistrationEntry
import org.simple.clinic.user.UserSession
import org.simple.clinic.user.UserStatus
import org.simple.clinic.user.finduser.FindUserResult
import org.simple.clinic.user.finduser.FindUserResult.Found_Old
import org.simple.clinic.user.finduser.FindUserResult.NetworkError
import org.simple.clinic.user.finduser.FindUserResult.NotFound
import org.simple.clinic.user.finduser.FindUserResult.UnexpectedError
import org.simple.clinic.user.finduser.UserLookup
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.widgets.UiEvent

class RegistrationPhoneScreenControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val screen = mock<RegistrationPhoneScreen>()
  private val userSession = mock<UserSession>()
  private val numberValidator = IndianPhoneNumberValidator()
  private val findUserWithPhoneNumber = mock<UserLookup>()
  private val facilitySync = mock<FacilitySync>()

  private val uiEvents: Subject<UiEvent> = PublishSubject.create<UiEvent>()

  private val controller: RegistrationPhoneScreenController = RegistrationPhoneScreenController(
      userSession = userSession,
      userLookup = findUserWithPhoneNumber,
      numberValidator = numberValidator,
      facilitySync = facilitySync
  )

  @Before
  fun setUp() {
    whenever(userSession.isOngoingRegistrationEntryPresent())
        .doReturn(Single.never())
    whenever(userSession.isUserUnauthorized())
        .doReturn(Observable.never())

    uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(screen) }
  }

  @Test
  fun `when screen is created and an existing ongoing entry is absent then an empty ongoing entry should be created`() {
    whenever(userSession.saveOngoingRegistrationEntry(any())).doReturn(Completable.complete())
    whenever(userSession.isOngoingRegistrationEntryPresent()).doReturn(Single.just(false))
    whenever(userSession.ongoingRegistrationEntry()).doReturn(Single.just(OngoingRegistrationEntry()))

    uiEvents.onNext(RegistrationPhoneScreenCreated())

    verify(userSession).saveOngoingRegistrationEntry(argThat { uuid != null })
  }

  @Test
  fun `when screen is created and an existing ongoing entry is present then an empty ongoing entry should not be created`() {
    whenever(userSession.saveOngoingRegistrationEntry(any())).doReturn(Completable.complete())
    whenever(userSession.isOngoingRegistrationEntryPresent()).doReturn(Single.just(true))
    whenever(userSession.ongoingRegistrationEntry()).doReturn(Single.never())

    uiEvents.onNext(RegistrationPhoneScreenCreated())

    verify(userSession, never()).saveOngoingRegistrationEntry(any())
  }

  @Test
  fun `when screen is created then existing details should be pre-filled`() {
    val ongoingEntry = OngoingRegistrationEntry(phoneNumber = "123")
    whenever(userSession.ongoingRegistrationEntry()).doReturn(Single.just(ongoingEntry))
    whenever(userSession.isOngoingRegistrationEntryPresent()).doReturn(Single.just(true))

    uiEvents.onNext(RegistrationPhoneScreenCreated())

    verify(userSession, never()).saveOngoingRegistrationEntry(any())
    verify(screen).preFillUserDetails(argThat { phoneNumber == ongoingEntry.phoneNumber })
  }

  @Test
  fun `when proceed is clicked with a valid number then the ongoing entry should be updated and then the next screen should be opened`() {
    val validNumber = "1234567890"
    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.Success)
    whenever(userSession.ongoingRegistrationEntry()).doReturn(Single.just(OngoingRegistrationEntry()))
    whenever(userSession.saveOngoingRegistrationEntry(OngoingRegistrationEntry(phoneNumber = validNumber))).doReturn(Completable.complete())
    whenever(findUserWithPhoneNumber.find_old(validNumber)).doReturn(Single.just<FindUserResult>(NotFound))

    uiEvents.onNext(RegistrationPhoneNumberTextChanged(validNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())

    verify(userSession).saveOngoingRegistrationEntry(OngoingRegistrationEntry(phoneNumber = validNumber))
    verify(screen).openRegistrationNameEntryScreen()
  }

  @Test
  fun `proceed button clicks should only be accepted if the input phone number is valid`() {
    val invalidNumber = "12345"
    val validNumber = "1234567890"
    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.Success)
    whenever(userSession.ongoingRegistrationEntry()).doReturn(Single.just(OngoingRegistrationEntry()))
    whenever(userSession.saveOngoingRegistrationEntry(OngoingRegistrationEntry(phoneNumber = validNumber))).doReturn(Completable.complete())
    whenever(findUserWithPhoneNumber.find_old(validNumber)).doReturn(Single.just<FindUserResult>(NotFound))

    uiEvents.onNext(RegistrationPhoneNumberTextChanged(invalidNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())
    verifyZeroInteractions(userSession)

    uiEvents.onNext(RegistrationPhoneNumberTextChanged(validNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())
    verify(userSession).saveOngoingRegistrationEntry(OngoingRegistrationEntry(phoneNumber = validNumber))
    verify(screen).openRegistrationNameEntryScreen()
  }

  @Test
  fun `when proceed is clicked with an invalid number then an error should be shown`() {
    val invalidNumber = "12345"
    uiEvents.onNext(RegistrationPhoneNumberTextChanged(invalidNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())

    verify(screen).showInvalidNumberError()
    verify(userSession, never()).saveOngoingRegistrationEntry(any())
    verify(screen, never()).openRegistrationNameEntryScreen()
  }

  @Test
  fun `when input text is changed then any visible errors should be removed`() {
    uiEvents.onNext(RegistrationPhoneNumberTextChanged(""))
    verify(screen).hideAnyError()
  }

  @Test
  fun `when proceed is clicked with a valid phone number then a network call should be made to check if the phone number belongs to an existing user`() {
    val inputNumber = "1234567890"
    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.Success)
    whenever(findUserWithPhoneNumber.find_old(inputNumber)).doReturn(Single.never())

    uiEvents.onNext(RegistrationPhoneNumberTextChanged(inputNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())

    verify(screen).showProgressIndicator()
    verify(findUserWithPhoneNumber).find_old(inputNumber)
  }

  @Test
  fun `when the network call for checking phone number fails then an error should be shown`() {
    val inputNumber = "1234567890"

    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.Success)
    whenever(findUserWithPhoneNumber.find_old(inputNumber))
        .doReturn(Single.just<FindUserResult>(UnexpectedError))
        .doReturn(Single.just<FindUserResult>(NetworkError))

    uiEvents.onNext(RegistrationPhoneNumberTextChanged(inputNumber))

    uiEvents.onNext(RegistrationPhoneDoneClicked())
    verify(screen).showProgressIndicator()
    verify(screen).hideProgressIndicator()
    verify(screen).showUnexpectedErrorMessage()

    clearInvocations(screen)

    uiEvents.onNext(RegistrationPhoneDoneClicked())
    verify(screen).showProgressIndicator()
    verify(screen).hideProgressIndicator()
    verify(screen).showNetworkErrorMessage()
  }

  @Test
  fun `when the phone number belongs to an existing user then an ongoing login entry should be created and login PIN entry screen should be opened`() {
    val inputNumber = "1234567890"
    val userPayload = TestData.loggedInUserPayload(phone = inputNumber)
    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.Success)
    whenever(findUserWithPhoneNumber.find_old(inputNumber)).doReturn(Single.just<FindUserResult>(Found_Old(userPayload)))
    whenever(userSession.saveOngoingLoginEntry(any())).doReturn(Completable.complete())
    whenever(userSession.clearOngoingRegistrationEntry()).doReturn(Completable.complete())

    uiEvents.onNext(RegistrationPhoneNumberTextChanged(inputNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())

    verify(userSession).saveOngoingLoginEntry(OngoingLoginEntry(
        uuid = userPayload.uuid,
        phoneNumber = inputNumber,
        pin = null,
        fullName = userPayload.fullName,
        pinDigest = userPayload.pinDigest,
        registrationFacilityUuid = userPayload.registrationFacilityId,
        status = userPayload.status,
        createdAt = userPayload.createdAt,
        updatedAt = userPayload.updatedAt
    ))
    verify(userSession).clearOngoingRegistrationEntry()
    verify(screen).openLoginPinEntryScreen()
    verify(screen, never()).showAccessDeniedScreen(userPayload.fullName)
  }

  @Test
  fun `when the phone number belongs to an existing user and creating ongoing entry fails, an error should be shown`() {
    val inputNumber = "1234567890"
    val userPayload = TestData.loggedInUserPayload(phone = inputNumber)
    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.Success)
    whenever(findUserWithPhoneNumber.find_old(inputNumber)).doReturn(Single.just<FindUserResult>(Found_Old(userPayload)))
    whenever(userSession.clearOngoingRegistrationEntry()).doReturn(Completable.complete())
    whenever(userSession.saveOngoingLoginEntry(any())).doReturn(Completable.error(RuntimeException()))

    uiEvents.onNext(RegistrationPhoneNumberTextChanged(inputNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())

    verify(userSession).saveOngoingLoginEntry(OngoingLoginEntry(
        uuid = userPayload.uuid,
        phoneNumber = inputNumber,
        pin = null,
        fullName = userPayload.fullName,
        pinDigest = userPayload.pinDigest,
        registrationFacilityUuid = userPayload.registrationFacilityId,
        status = userPayload.status,
        createdAt = userPayload.createdAt,
        updatedAt = userPayload.updatedAt
    ))
    verify(screen, never()).openLoginPinEntryScreen()
    verify(screen, never()).showAccessDeniedScreen(userPayload.fullName)
    verify(screen).showUnexpectedErrorMessage()
  }

  @Test
  fun `when the existing user is denied access then access denied screen should show`() {
    val inputNumber = "1234567890"
    val userPayload = TestData.loggedInUserPayload(phone = inputNumber, status = UserStatus.DisapprovedForSyncing)
    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.Success)
    whenever(findUserWithPhoneNumber.find_old(inputNumber)).doReturn(Single.just<FindUserResult>(Found_Old(userPayload)))

    uiEvents.onNext(RegistrationPhoneNumberTextChanged(inputNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())

    verify(screen).showAccessDeniedScreen(userPayload.fullName)
    verify(userSession, never()).saveOngoingLoginEntry(any())
    verify(userSession, never()).clearOngoingRegistrationEntry()
    verify(screen, never()).openLoginPinEntryScreen()
  }

  @Test
  fun `when proceed is clicked then any existing error should be cleared`() {
    val inputNumber = "1234567890"
    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.Success)
    whenever(findUserWithPhoneNumber.find_old(inputNumber)).doReturn(Single.never())

    uiEvents.onNext(RegistrationPhoneNumberTextChanged(inputNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())

    verify(screen, times(2)).hideAnyError()
  }

  @Test
  fun `when the screen is created and a local logged in user exists, show the logged out dialog if the user is unauthorized`() {
    whenever(userSession.isUserUnauthorized()).doReturn(Observable.just(true))

    uiEvents.onNext(RegistrationPhoneScreenCreated())

    verify(screen).showLoggedOutOfDeviceDialog()
  }

  @Test
  fun `when the screen is created and a local logged in user exists, do not show the logged out dialog if the user is unauthorized`() {
    whenever(userSession.isUserUnauthorized()).doReturn(Observable.just(false))

    uiEvents.onNext(RegistrationPhoneScreenCreated())

    verify(screen, never()).showLoggedOutOfDeviceDialog()
  }

  @Test
  fun `before a phone number is looked up, the facilities must be synced`() {
    // given
    val phoneNumber = "1234567890"
    whenever(findUserWithPhoneNumber.find_old(phoneNumber)) doReturn Single.never<FindUserResult>()
    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.Success)

    // when
    uiEvents.onNext(RegistrationPhoneNumberTextChanged(phoneNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())

    // then
    verify(screen).showProgressIndicator()
    verify(facilitySync).pullWithResult()
  }

  @Test
  fun `when pulling the facilities fails, the number must not be looked up`() {
    // given
    val phoneNumber = "1234567890"
    whenever(findUserWithPhoneNumber.find_old(phoneNumber)) doReturn Single.never<FindUserResult>()
    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.NetworkError)

    // when
    uiEvents.onNext(RegistrationPhoneNumberTextChanged(phoneNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())

    // then
    verify(findUserWithPhoneNumber, never()).find_old(phoneNumber)
  }

  @Test
  fun `when pulling the facilities fails with a network error, the network error message must be shown`() {
    // given
    val phoneNumber = "1234567890"
    whenever(findUserWithPhoneNumber.find_old(phoneNumber)) doReturn Single.never<FindUserResult>()
    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.NetworkError)

    // when
    uiEvents.onNext(RegistrationPhoneNumberTextChanged(phoneNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())

    // then
    verify(screen).hideProgressIndicator()
    verify(screen).showNetworkErrorMessage()
    verify(findUserWithPhoneNumber, never()).find_old(phoneNumber)
  }

  @Test
  fun `when pulling the facilities fails with any other error, the unexpected error message must be shown`() {
    // given
    val phoneNumber = "1234567890"
    whenever(findUserWithPhoneNumber.find_old(phoneNumber)) doReturn Single.never<FindUserResult>()
    whenever(facilitySync.pullWithResult()) doReturn Single.just<FacilityPullResult>(FacilityPullResult.UnexpectedError)

    // when
    uiEvents.onNext(RegistrationPhoneNumberTextChanged(phoneNumber))
    uiEvents.onNext(RegistrationPhoneDoneClicked())

    // then
    verify(screen).hideProgressIndicator()
    verify(screen).showUnexpectedErrorMessage()
    verify(findUserWithPhoneNumber, never()).find_old(phoneNumber)
  }
}
