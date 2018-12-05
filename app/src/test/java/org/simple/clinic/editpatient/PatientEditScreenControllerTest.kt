package org.simple.clinic.editpatient

import com.google.common.truth.Truth
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.editpatient.PatientEditValidationError.BOTH_DATEOFBIRTH_AND_AGE_ABSENT
import org.simple.clinic.editpatient.PatientEditValidationError.COLONY_OR_VILLAGE_EMPTY
import org.simple.clinic.editpatient.PatientEditValidationError.DATE_OF_BIRTH_IN_FUTURE
import org.simple.clinic.editpatient.PatientEditValidationError.DISTRICT_EMPTY
import org.simple.clinic.editpatient.PatientEditValidationError.FULL_NAME_EMPTY
import org.simple.clinic.editpatient.PatientEditValidationError.INVALID_DATE_OF_BIRTH
import org.simple.clinic.editpatient.PatientEditValidationError.PHONE_NUMBER_EMPTY
import org.simple.clinic.editpatient.PatientEditValidationError.PHONE_NUMBER_LENGTH_TOO_LONG
import org.simple.clinic.editpatient.PatientEditValidationError.PHONE_NUMBER_LENGTH_TOO_SHORT
import org.simple.clinic.editpatient.PatientEditValidationError.STATE_EMPTY
import org.simple.clinic.patient.Age
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.Gender.FEMALE
import org.simple.clinic.patient.Gender.MALE
import org.simple.clinic.patient.Gender.TRANSGENDER
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.PatientAddress
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.patient.PatientPhoneNumber
import org.simple.clinic.patient.PatientPhoneNumberType
import org.simple.clinic.patient.PatientProfile
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.registration.phone.PhoneNumberValidator
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.BLANK
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.LENGTH_TOO_LONG
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.LENGTH_TOO_SHORT
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.VALID
import org.simple.clinic.util.Just
import org.simple.clinic.util.None
import org.simple.clinic.util.TestClock
import org.simple.clinic.util.toOptional
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.ageanddateofbirth.DateOfBirthAndAgeVisibility.*
import org.simple.clinic.widgets.ageanddateofbirth.DateOfBirthFormatValidator
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@RunWith(JUnitParamsRunner::class)
class PatientEditScreenControllerTest {

  private val uiEvents = PublishSubject.create<UiEvent>()
  val clock: TestClock = TestClock()

  private lateinit var screen: PatientEditScreen
  private lateinit var patientRepository: PatientRepository
  private lateinit var numberValidator: PhoneNumberValidator
  private lateinit var controller: PatientEditScreenController
  var config = PatientEditConfig(isEditAgeAndDobEnabled = false)

  private lateinit var errorConsumer: (Throwable) -> Unit
  lateinit var dateOfBirthFormatValidator: DateOfBirthFormatValidator
  val dateOfBirthFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

  @Before
  fun setUp() {
    screen = mock()
    patientRepository = mock()
    numberValidator = mock()
    dateOfBirthFormatValidator = mock()

    controller = PatientEditScreenController(
        patientRepository,
        numberValidator,
        Single.fromCallable { config },
        clock,
        dateOfBirthFormatValidator,
        dateOfBirthFormat)

    errorConsumer = { throw it }

    uiEvents
        .compose(controller)
        .subscribe({ uiChange -> uiChange(screen) }, { e -> errorConsumer(e) })
  }

  @Test
  @Parameters(method = "params for prefilling fields on screen created")
  fun `when screen is created then the existing patient data must be prefilled`(
      patient: Patient,
      address: PatientAddress,
      shouldSetColonyOrVillage: Boolean,
      phoneNumber: PatientPhoneNumber?,
      shouldSetPhoneNumber: Boolean,
      shouldSetAge: Boolean,
      shouldSetDateOfBirth: Boolean
  ) {
    whenever(patientRepository.patient(any())).thenReturn(Observable.just(Just(patient)))
    whenever(patientRepository.address(patient.addressUuid)).thenReturn(Observable.just(Just(address)))
    whenever(patientRepository.phoneNumbers(patient.uuid)).thenReturn(Observable.just(phoneNumber.toOptional()))

    uiEvents.onNext(PatientEditScreenCreated(patient.uuid))

    if (shouldSetColonyOrVillage) {
      verify(screen).setColonyOrVillage(address.colonyOrVillage!!)
    } else {
      verify(screen, never()).setColonyOrVillage(any())
    }

    verify(screen).setDistrict(address.district)
    verify(screen).setState(address.state)
    verify(screen).setGender(patient.gender)
    verify(screen).setPatientName(patient.fullName)

    if (shouldSetPhoneNumber) {
      verify(screen).setPatientPhoneNumber(phoneNumber!!.number)
    } else {
      verify(screen, never()).setPatientPhoneNumber(any())
    }

    if (shouldSetAge) {
      verify(screen).setPatientAge(any())
    } else {
      verify(screen, never()).setPatientAge(any())
    }

    if (shouldSetDateOfBirth) {
      verify(screen).setPatientDateofBirth(patient.dateOfBirth!!)
    } else {
      verify(screen, never()).setPatientDateofBirth(any())
    }
  }

  @Suppress("Unused")
  private fun `params for prefilling fields on screen created`(): List<List<Any?>> {
    fun generateTestDataWithAge(
        colonyOrVillage: String?,
        phoneNumber: String?,
        age: Int
    ): List<Any?> {
      val patientToReturn = PatientMocker.patient(age = Age(
          value = age,
          updatedAt = Instant.now(clock),
          computedDateOfBirth = LocalDate.now(clock)
      ), dateOfBirth = null)
      val addressToReturn = PatientMocker.address(uuid = patientToReturn.addressUuid, colonyOrVillage = colonyOrVillage)
      val phoneNumberToReturn = phoneNumber?.let { PatientMocker.phoneNumber(patientUuid = patientToReturn.uuid, number = it) }

      return listOf(
          patientToReturn,
          addressToReturn,
          colonyOrVillage.isNullOrBlank().not(),
          phoneNumberToReturn,
          phoneNumberToReturn != null,
          true,
          false)
    }

    fun generateTestDataWithDateOfBirth(
        colonyOrVillage: String?,
        phoneNumber: String?,
        dateOfBirth: LocalDate
    ): List<Any?> {
      val patientToReturn = PatientMocker.patient(dateOfBirth = dateOfBirth, age = null)
      val addressToReturn = PatientMocker.address(uuid = patientToReturn.addressUuid, colonyOrVillage = colonyOrVillage)
      val phoneNumberToReturn = phoneNumber?.let { PatientMocker.phoneNumber(patientUuid = patientToReturn.uuid, number = it) }

      return listOf(
          patientToReturn,
          addressToReturn,
          colonyOrVillage.isNullOrBlank().not(),
          phoneNumberToReturn,
          phoneNumberToReturn != null,
          false,
          true)
    }

    return listOf(
        generateTestDataWithAge(colonyOrVillage = "Colony", phoneNumber = "1111111111", age = 23),
        generateTestDataWithAge(colonyOrVillage = null, phoneNumber = "1111111111", age = 23),
        generateTestDataWithAge(colonyOrVillage = "", phoneNumber = "1111111111", age = 23),
        generateTestDataWithAge(colonyOrVillage = "Colony", phoneNumber = null, age = 23),
        generateTestDataWithDateOfBirth(colonyOrVillage = "Colony", phoneNumber = null, dateOfBirth = LocalDate.parse("1995-11-28")))
  }

  @Test
  fun `when save is clicked, patient name should be validated`() {
    whenever(numberValidator.validate(any(), any())).thenReturn(VALID)
    whenever(patientRepository.patient(any())).thenReturn(Observable.just(PatientMocker.patient().toOptional()))
    whenever(patientRepository.address(any())).thenReturn(Observable.just(PatientMocker.address().toOptional()))
    whenever(patientRepository.phoneNumbers(any())).thenReturn(Observable.just(None))

    whenever(patientRepository.updatePhoneNumberForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.createPhoneNumberForPatient(any(), any(), any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updateAddressForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updatePatient(any())).thenReturn(Completable.complete())

    uiEvents.onNext(PatientEditScreenCreated(UUID.randomUUID()))

    uiEvents.onNext(PatientEditPhoneNumberTextChanged(""))
    uiEvents.onNext(PatientEditGenderChanged(MALE))
    uiEvents.onNext(PatientEditColonyOrVillageChanged("Colony"))
    uiEvents.onNext(PatientEditDistrictTextChanged("District"))
    uiEvents.onNext(PatientEditStateTextChanged("State"))
    uiEvents.onNext(PatientEditAgeTextChanged("1"))

    uiEvents.onNext(PatientEditPatientNameTextChanged(""))
    uiEvents.onNext(PatientEditSaveClicked())

    verify(screen).showValidationErrors(setOf(FULL_NAME_EMPTY))
  }

  @Test
  @Parameters(method = "params for validating phone numbers")
  fun `when save is clicked, phone number should be validated`(
      alreadyPresentPhoneNumber: PatientPhoneNumber?,
      numberValidationResult: PhoneNumberValidator.Result,
      expectedError: PatientEditValidationError?
  ) {
    whenever(patientRepository.phoneNumbers(any())).thenReturn(Observable.just(alreadyPresentPhoneNumber.toOptional()))
    whenever(patientRepository.patient(any())).thenReturn(Observable.just(PatientMocker.patient().toOptional()))
    whenever(patientRepository.address(any())).thenReturn(Observable.just(PatientMocker.address().toOptional()))

    whenever(numberValidator.validate(any(), any())).thenReturn(numberValidationResult)

    whenever(patientRepository.updatePhoneNumberForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.createPhoneNumberForPatient(any(), any(), any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updateAddressForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updatePatient(any())).thenReturn(Completable.complete())

    uiEvents.onNext(PatientEditScreenCreated(UUID.randomUUID()))

    uiEvents.onNext(PatientEditGenderChanged(MALE))
    uiEvents.onNext(PatientEditColonyOrVillageChanged("Colony"))
    uiEvents.onNext(PatientEditDistrictTextChanged("District"))
    uiEvents.onNext(PatientEditStateTextChanged("State"))
    uiEvents.onNext(PatientEditPatientNameTextChanged("Name"))
    uiEvents.onNext(PatientEditAgeTextChanged("1"))

    uiEvents.onNext(PatientEditPhoneNumberTextChanged(""))
    uiEvents.onNext(PatientEditSaveClicked())

    if (expectedError == null) {
      verify(screen, never()).showValidationErrors(any())
    } else {
      verify(screen).showValidationErrors(setOf(expectedError))
    }
  }

  @Suppress("Unused")
  private fun `params for validating phone numbers`(): List<List<Any?>> {
    return listOf(
        listOf(null, BLANK, null),
        listOf(null, LENGTH_TOO_LONG, PHONE_NUMBER_LENGTH_TOO_LONG),
        listOf(null, LENGTH_TOO_SHORT, PHONE_NUMBER_LENGTH_TOO_SHORT),
        listOf(PatientMocker.phoneNumber(), BLANK, PHONE_NUMBER_EMPTY),
        listOf(PatientMocker.phoneNumber(), LENGTH_TOO_SHORT, PHONE_NUMBER_LENGTH_TOO_SHORT),
        listOf(PatientMocker.phoneNumber(), LENGTH_TOO_LONG, PHONE_NUMBER_LENGTH_TOO_LONG)
    )
  }

  @Test
  fun `when save is clicked, the colony should be validated`() {
    whenever(numberValidator.validate(any(), any())).thenReturn(VALID)
    whenever(patientRepository.patient(any())).thenReturn(Observable.just(PatientMocker.patient().toOptional()))
    whenever(patientRepository.address(any())).thenReturn(Observable.just(PatientMocker.address().toOptional()))
    whenever(patientRepository.phoneNumbers(any())).thenReturn(Observable.just(None))
    whenever(patientRepository.updatePhoneNumberForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.createPhoneNumberForPatient(any(), any(), any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updateAddressForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updatePatient(any())).thenReturn(Completable.complete())

    uiEvents.onNext(PatientEditScreenCreated(UUID.randomUUID()))

    uiEvents.onNext(PatientEditPhoneNumberTextChanged(""))
    uiEvents.onNext(PatientEditGenderChanged(MALE))
    uiEvents.onNext(PatientEditDistrictTextChanged("District"))
    uiEvents.onNext(PatientEditStateTextChanged("State"))
    uiEvents.onNext(PatientEditPatientNameTextChanged("Name"))
    uiEvents.onNext(PatientEditAgeTextChanged("1"))

    uiEvents.onNext(PatientEditColonyOrVillageChanged(""))
    uiEvents.onNext(PatientEditSaveClicked())

    verify(screen).showValidationErrors(setOf(COLONY_OR_VILLAGE_EMPTY))
  }

  @Test
  fun `when save is clicked, the district should be validated`() {
    whenever(numberValidator.validate(any(), any())).thenReturn(VALID)
    whenever(patientRepository.patient(any())).thenReturn(Observable.just(PatientMocker.patient().toOptional()))
    whenever(patientRepository.address(any())).thenReturn(Observable.just(PatientMocker.address().toOptional()))
    whenever(patientRepository.phoneNumbers(any())).thenReturn(Observable.just(None))
    whenever(patientRepository.updatePhoneNumberForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.createPhoneNumberForPatient(any(), any(), any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updateAddressForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updatePatient(any())).thenReturn(Completable.complete())

    uiEvents.onNext(PatientEditScreenCreated(UUID.randomUUID()))

    uiEvents.onNext(PatientEditPhoneNumberTextChanged(""))
    uiEvents.onNext(PatientEditGenderChanged(MALE))
    uiEvents.onNext(PatientEditColonyOrVillageChanged("Colony"))
    uiEvents.onNext(PatientEditStateTextChanged("State"))
    uiEvents.onNext(PatientEditPatientNameTextChanged("Name"))
    uiEvents.onNext(PatientEditAgeTextChanged("1"))

    uiEvents.onNext(PatientEditDistrictTextChanged(""))
    uiEvents.onNext(PatientEditSaveClicked())

    verify(screen).showValidationErrors(setOf(DISTRICT_EMPTY))
  }

  @Test
  fun `when save is clicked, the state should be validated`() {
    whenever(dateOfBirthFormatValidator.validate(any(), any())).thenReturn(DateOfBirthFormatValidator.Result.VALID)
    whenever(numberValidator.validate(any(), any())).thenReturn(PhoneNumberValidator.Result.VALID)
    whenever(patientRepository.patient(any())).thenReturn(Observable.just(PatientMocker.patient().toOptional()))
    whenever(patientRepository.address(any())).thenReturn(Observable.just(PatientMocker.address().toOptional()))
    whenever(patientRepository.phoneNumbers(any())).thenReturn(Observable.just(None))

    whenever(patientRepository.updatePhoneNumberForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.createPhoneNumberForPatient(any(), any(), any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updateAddressForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updatePatient(any())).thenReturn(Completable.complete())

    uiEvents.onNext(PatientEditScreenCreated(UUID.randomUUID()))

    uiEvents.onNext(PatientEditPhoneNumberTextChanged(""))
    uiEvents.onNext(PatientEditGenderChanged(MALE))
    uiEvents.onNext(PatientEditColonyOrVillageChanged("Colony"))
    uiEvents.onNext(PatientEditDistrictTextChanged("District"))
    uiEvents.onNext(PatientEditPatientNameTextChanged("Name"))
    uiEvents.onNext(PatientEditAgeTextChanged("1"))

    uiEvents.onNext(PatientEditStateTextChanged(""))
    uiEvents.onNext(PatientEditSaveClicked())

    verify(screen).showValidationErrors(setOf(STATE_EMPTY))
  }

  @Test
  fun `when save is clicked, the age should be validated`() {
    whenever(numberValidator.validate(any(), any())).thenReturn(VALID)
    whenever(patientRepository.patient(any())).thenReturn(Observable.just(PatientMocker.patient().toOptional()))
    whenever(patientRepository.address(any())).thenReturn(Observable.just(PatientMocker.address().toOptional()))
    whenever(patientRepository.phoneNumbers(any())).thenReturn(Observable.just(None))
    whenever(patientRepository.updatePhoneNumberForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.createPhoneNumberForPatient(any(), any(), any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updateAddressForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updatePatient(any())).thenReturn(Completable.complete())

    uiEvents.onNext(PatientEditScreenCreated(UUID.randomUUID()))
    uiEvents.onNext(PatientEditPhoneNumberTextChanged(""))
    uiEvents.onNext(PatientEditGenderChanged(MALE))
    uiEvents.onNext(PatientEditColonyOrVillageChanged("Colony"))
    uiEvents.onNext(PatientEditDistrictTextChanged("District"))
    uiEvents.onNext(PatientEditPatientNameTextChanged("Name"))
    uiEvents.onNext(PatientEditStateTextChanged("State"))
    uiEvents.onNext(PatientEditAgeTextChanged(""))
    uiEvents.onNext(PatientEditSaveClicked())

    verify(screen).showValidationErrors(setOf(BOTH_DATEOFBIRTH_AND_AGE_ABSENT))
  }

  @Test
  @Parameters(value = [
    "01-01-2000|INVALID_PATTERN|INVALID_DATE_OF_BIRTH",
    "01-01-2000|DATE_IS_IN_FUTURE|DATE_OF_BIRTH_IN_FUTURE"
  ])
  fun `when save is clicked, the date of birth should be validated`(
      dateOfBirth: String,
      dateOfBirthValidationResult: DateOfBirthFormatValidator.Result,
      expectedError: PatientEditValidationError
  ) {
    whenever(dateOfBirthFormatValidator.validate(any(), any())).thenReturn(dateOfBirthValidationResult)
    whenever(numberValidator.validate(any(), any())).thenReturn(VALID)
    whenever(patientRepository.patient(any())).thenReturn(Observable.just(PatientMocker.patient().toOptional()))
    whenever(patientRepository.address(any())).thenReturn(Observable.just(PatientMocker.address().toOptional()))
    whenever(patientRepository.phoneNumbers(any())).thenReturn(Observable.just(None))
    whenever(patientRepository.updatePhoneNumberForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.createPhoneNumberForPatient(any(), any(), any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updateAddressForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updatePatient(any())).thenReturn(Completable.complete())

    uiEvents.onNext(PatientEditScreenCreated(UUID.randomUUID()))
    uiEvents.onNext(PatientEditPhoneNumberTextChanged(""))
    uiEvents.onNext(PatientEditGenderChanged(MALE))
    uiEvents.onNext(PatientEditColonyOrVillageChanged("Colony"))
    uiEvents.onNext(PatientEditDistrictTextChanged("District"))
    uiEvents.onNext(PatientEditPatientNameTextChanged("Name"))
    uiEvents.onNext(PatientEditStateTextChanged("State"))
    uiEvents.onNext(PatientEditDateOfBirthTextChanged(dateOfBirth))
    uiEvents.onNext(PatientEditSaveClicked())

    verify(screen).showValidationErrors(setOf(expectedError))
  }

  @Test
  @Parameters(method = "params for validating all fields on save clicks")
  fun `when save is clicked, all fields should be validated`(
      alreadyPresentPhoneNumber: PatientPhoneNumber?,
      name: String,
      numberValidationResult: PhoneNumberValidator.Result,
      colonyOrVillage: String,
      district: String,
      state: String,
      age: String?,
      dateOfBirthValidationResult: DateOfBirthFormatValidator.Result?,
      dateOfBirth: String?,
      expectedErrors: Set<PatientEditValidationError>
  ) {
    whenever(patientRepository.phoneNumbers(any())).thenReturn(Observable.just(alreadyPresentPhoneNumber.toOptional()))
    whenever(patientRepository.patient(any())).thenReturn(Observable.just(PatientMocker.patient().toOptional()))
    whenever(patientRepository.address(any())).thenReturn(Observable.just(PatientMocker.address().toOptional()))
    whenever(patientRepository.updatePhoneNumberForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.createPhoneNumberForPatient(any(), any(), any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updateAddressForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updatePatient(any())).thenReturn(Completable.complete())
    whenever(numberValidator.validate(any(), any())).thenReturn(numberValidationResult)
    if(dateOfBirthValidationResult != null) {
      whenever(dateOfBirthFormatValidator.validate(any(), any())).thenReturn(dateOfBirthValidationResult)
    }

    uiEvents.onNext(PatientEditScreenCreated(UUID.randomUUID()))

    uiEvents.onNext(PatientEditPatientNameTextChanged(name))
    uiEvents.onNext(PatientEditPhoneNumberTextChanged(""))
    uiEvents.onNext(PatientEditColonyOrVillageChanged(colonyOrVillage))
    uiEvents.onNext(PatientEditDistrictTextChanged(district))
    uiEvents.onNext(PatientEditStateTextChanged(state))
    uiEvents.onNext(PatientEditGenderChanged(MALE))
    if(age != null) {
      uiEvents.onNext(PatientEditDateOfBirthTextChanged(""))
      uiEvents.onNext(PatientEditAgeTextChanged(age))
    }
    if(dateOfBirth != null) {
      uiEvents.onNext(PatientEditAgeTextChanged(""))
      uiEvents.onNext(PatientEditDateOfBirthTextChanged(dateOfBirth))
    }

    if(age == null && dateOfBirth == null) {
      uiEvents.onNext(PatientEditAgeTextChanged(""))
    }

    uiEvents.onNext(PatientEditSaveClicked())

    if (expectedErrors.isNotEmpty()) {
      // This is order dependent because finding the first field
      // with error is only possible once the errors are set.
      val inOrder = inOrder(screen)

      inOrder.verify(screen).showValidationErrors(expectedErrors)
      inOrder.verify(screen).scrollToFirstFieldWithError()

    } else {
      verify(screen, never()).showValidationErrors(any())
      verify(screen, never()).scrollToFirstFieldWithError()
    }
  }

  @Suppress("Unused")
  private fun `params for validating all fields on save clicks`(): List<List<Any?>> {
    return listOf(
        listOf(
            PatientMocker.phoneNumber(),
            "",
            BLANK,
            "",
            "",
            "",
            "1",
            null,
            null,
            setOf(FULL_NAME_EMPTY, PHONE_NUMBER_EMPTY, COLONY_OR_VILLAGE_EMPTY, DISTRICT_EMPTY, STATE_EMPTY)
        ),
        listOf(
            null,
            "",
            BLANK,
            "",
            "",
            "",
            "",
            null,
            null,
            setOf(FULL_NAME_EMPTY, COLONY_OR_VILLAGE_EMPTY, DISTRICT_EMPTY, STATE_EMPTY, BOTH_DATEOFBIRTH_AND_AGE_ABSENT)
        ),
        listOf(
            PatientMocker.phoneNumber(),
            "",
            LENGTH_TOO_SHORT,
            "Colony",
            "",
            "",
            "1",
            null,
            null,
            setOf(FULL_NAME_EMPTY, PHONE_NUMBER_LENGTH_TOO_SHORT, DISTRICT_EMPTY, STATE_EMPTY)
        ),
        listOf(
            null,
            "",
            LENGTH_TOO_SHORT,
            "Colony",
            "",
            "",
            "",
            null,
            null,
            setOf(FULL_NAME_EMPTY, PHONE_NUMBER_LENGTH_TOO_SHORT, DISTRICT_EMPTY, STATE_EMPTY, BOTH_DATEOFBIRTH_AND_AGE_ABSENT)
        ),
        listOf(
            PatientMocker.phoneNumber(),
            "Name",
            LENGTH_TOO_LONG,
            "",
            "District",
            "",
            "1",
            null,
            null,
            setOf(PHONE_NUMBER_LENGTH_TOO_LONG, COLONY_OR_VILLAGE_EMPTY, STATE_EMPTY)
        ),
        listOf(
            null,
            "Name",
            LENGTH_TOO_LONG,
            "",
            "District",
            "",
            null,
            DateOfBirthFormatValidator.Result.INVALID_PATTERN,
            "01/01/2000",
            setOf(PHONE_NUMBER_LENGTH_TOO_LONG, COLONY_OR_VILLAGE_EMPTY, STATE_EMPTY, INVALID_DATE_OF_BIRTH)
        ),
        listOf(
            PatientMocker.phoneNumber(),
            "",
            VALID,
            "Colony",
            "District",
            "",
            null,
            null,
            null,
            setOf(FULL_NAME_EMPTY, STATE_EMPTY, BOTH_DATEOFBIRTH_AND_AGE_ABSENT)
        ),
        listOf(
            null,
            "",
            VALID,
            "Colony",
            "District",
            "",
            null,
            DateOfBirthFormatValidator.Result.DATE_IS_IN_FUTURE,
            "01-01-2000",
            setOf(FULL_NAME_EMPTY, STATE_EMPTY, DATE_OF_BIRTH_IN_FUTURE)
        ),
        listOf(
            null,
            "",
            BLANK,
            "Colony",
            "District",
            "State",
            "",
            null,
            null,
            setOf(FULL_NAME_EMPTY, BOTH_DATEOFBIRTH_AND_AGE_ABSENT)
        ),
        listOf(
            PatientMocker.phoneNumber(),
            "Name",
            VALID,
            "Colony",
            "District",
            "State",
            "1",
            null,
            null,
            emptySet<PatientEditValidationError>()
        ),
        listOf(
            null,
            "Name",
            VALID,
            "Colony",
            "District",
            "State",
            null,
            DateOfBirthFormatValidator.Result.VALID,
            "01-01-2000",
            emptySet<PatientEditValidationError>()
        )
    )
  }

  @Test
  @Parameters(method = "params for hiding errors on text changes")
  fun `when input changes, errors corresponding to the input must be hidden`(
      inputChange: UiEvent,
      expectedErrorsToHide: Set<PatientEditValidationError>
  ) {
    whenever(patientRepository.phoneNumbers(any())).thenReturn(Observable.just(None))
    whenever(patientRepository.patient(any())).thenReturn(Observable.just(PatientMocker.patient().toOptional()))
    whenever(patientRepository.address(any())).thenReturn(Observable.just(PatientMocker.address().toOptional()))
    whenever(numberValidator.validate(any(), any())).thenReturn(BLANK)

    uiEvents.onNext(PatientEditScreenCreated(UUID.randomUUID()))
    uiEvents.onNext(PatientEditSaveClicked())
    uiEvents.onNext(inputChange)

    if (expectedErrorsToHide.isNotEmpty()) {
      verify(screen).hideValidationErrors(expectedErrorsToHide)
    } else {
      verify(screen, never()).hideValidationErrors(any())
    }
  }

  @Suppress("Unused")
  private fun `params for hiding errors on text changes`(): List<List<Any>> {
    return listOf(
        listOf(PatientEditPatientNameTextChanged(""), setOf(FULL_NAME_EMPTY)),
        listOf(PatientEditPatientNameTextChanged("Name"), setOf(FULL_NAME_EMPTY)),
        listOf(PatientEditPhoneNumberTextChanged(""), setOf(PHONE_NUMBER_EMPTY, PHONE_NUMBER_LENGTH_TOO_SHORT, PHONE_NUMBER_LENGTH_TOO_LONG)),
        listOf(PatientEditPhoneNumberTextChanged("12345"), setOf(PHONE_NUMBER_EMPTY, PHONE_NUMBER_LENGTH_TOO_SHORT, PHONE_NUMBER_LENGTH_TOO_LONG)),
        listOf(PatientEditColonyOrVillageChanged(""), setOf(COLONY_OR_VILLAGE_EMPTY)),
        listOf(PatientEditColonyOrVillageChanged("Colony"), setOf(COLONY_OR_VILLAGE_EMPTY)),
        listOf(PatientEditStateTextChanged(""), setOf(STATE_EMPTY)),
        listOf(PatientEditStateTextChanged("State"), setOf(STATE_EMPTY)),
        listOf(PatientEditDistrictTextChanged(""), setOf(DISTRICT_EMPTY)),
        listOf(PatientEditDistrictTextChanged("District"), setOf(DISTRICT_EMPTY)),
        listOf(PatientEditAgeTextChanged("1"), setOf(BOTH_DATEOFBIRTH_AND_AGE_ABSENT)),
        listOf(PatientEditDateOfBirthTextChanged("20/02/1990"), setOf(DATE_OF_BIRTH_IN_FUTURE, INVALID_DATE_OF_BIRTH)),
        listOf(PatientEditGenderChanged(TRANSGENDER), emptySet<PatientEditValidationError>())
    )
  }

  @Test
  @Parameters(method = "params for saving patient on save clicked")
  fun `when save is clicked, the patient details must be updated if there are no errors`(
      existingSavedPatient: Patient,
      existingSavedAddress: PatientAddress,
      existingSavedPhoneNumber: PatientPhoneNumber?,
      numberValidationResult: PhoneNumberValidator.Result,
      inputEvents: List<UiEvent>,
      shouldSavePatient: Boolean,
      expectedSavedPatient: Patient?,
      expectedSavedPatientAddress: PatientAddress?,
      expectedSavedPatientPhoneNumber: PatientPhoneNumber?
  ) {
    whenever(patientRepository.patient(existingSavedPatient.uuid)).thenReturn(Observable.just(existingSavedPatient.toOptional()))
    whenever(patientRepository.phoneNumbers(existingSavedPatient.uuid)).thenReturn(Observable.just(existingSavedPhoneNumber.toOptional()))
    whenever(patientRepository.address(existingSavedAddress.uuid)).thenReturn(Observable.just(existingSavedAddress.toOptional()))

    whenever(patientRepository.updatePatient(any())).thenReturn(Completable.complete())
    whenever(patientRepository.updateAddressForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updatePhoneNumberForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.createPhoneNumberForPatient(any(), any(), any(), any())).thenReturn(Completable.complete())

    whenever(numberValidator.validate(any(), any())).thenReturn(numberValidationResult)

    uiEvents.onNext(PatientEditScreenCreated(existingSavedPatient.uuid))
    inputEvents.forEach { uiEvents.onNext(it) }
    uiEvents.onNext(PatientEditSaveClicked())

    if (shouldSavePatient) {
      verify(patientRepository).updatePatient(expectedSavedPatient!!)
      verify(patientRepository).updateAddressForPatient(expectedSavedPatient.uuid, expectedSavedPatientAddress!!)

      if (expectedSavedPatientPhoneNumber != null) {
        if (existingSavedPhoneNumber == null) {
          verify(patientRepository).createPhoneNumberForPatient(
              patientUuid = expectedSavedPatientPhoneNumber.patientUuid,
              number = expectedSavedPatientPhoneNumber.number,
              phoneNumberType = PatientPhoneNumberType.MOBILE,
              active = true
          )
        } else {
          verify(patientRepository).updatePhoneNumberForPatient(expectedSavedPatient.uuid, expectedSavedPatientPhoneNumber)
        }

      } else {
        verify(patientRepository, never()).createPhoneNumberForPatient(any(), any(), any(), any())
        verify(patientRepository, never()).updatePhoneNumberForPatient(any(), any())
      }
      verify(screen).goBack()

    } else {
      verify(patientRepository, never()).updatePatient(any())
      verify(patientRepository, never()).updateAddressForPatient(any(), any())
      verify(patientRepository, never()).updatePhoneNumberForPatient(any(), any())
      verify(patientRepository, never()).createPhoneNumberForPatient(any(), any(), any(), any())
      verify(screen, never()).goBack()
    }
  }

  @Suppress("Unused")
  private fun `params for saving patient on save clicked`(): List<List<Any?>> {

    fun generatePatientProfile(shouldAddNumber: Boolean): PatientProfile {
      val patientUuid = UUID.randomUUID()
      val addressUuid = UUID.randomUUID()

      return PatientProfile(
          patient = PatientMocker.patient(uuid = patientUuid, addressUuid = addressUuid),
          address = PatientMocker.address(uuid = addressUuid),
          phoneNumbers = if (shouldAddNumber) listOf(PatientMocker.phoneNumber(patientUuid = patientUuid)) else emptyList()
      )
    }

    fun generateTestData(
        patientProfile: PatientProfile,
        numberValidationResult: PhoneNumberValidator.Result,
        inputEvents: List<UiEvent>,
        shouldSavePatient: Boolean,
        createExpectedPatient: (Patient) -> Patient = { it },
        createExpectedAddress: (PatientAddress) -> PatientAddress = { it },
        createExpectedPhoneNumber: (UUID, PatientPhoneNumber?) -> PatientPhoneNumber? = { id, phoneNumber -> phoneNumber }
    ): List<Any?> {

      val expectedPatientPhoneNumber = if (shouldSavePatient) {
        val alreadySavedPhoneNumber = if (patientProfile.phoneNumbers.isEmpty()) null else patientProfile.phoneNumbers.first()
        createExpectedPhoneNumber(patientProfile.patient.uuid, alreadySavedPhoneNumber)

      } else null

      val preCreateInputEvents = listOf(
          PatientEditPatientNameTextChanged(patientProfile.patient.fullName),
          PatientEditDistrictTextChanged(patientProfile.address.district),
          PatientEditColonyOrVillageChanged(patientProfile.address.colonyOrVillage ?: ""),
          PatientEditStateTextChanged(patientProfile.address.state),
          PatientEditGenderChanged(patientProfile.patient.gender),
          PatientEditPhoneNumberTextChanged(patientProfile.phoneNumbers.firstOrNull()?.number ?: ""),

          // TODO: actually test this when implementing save patient
          PatientEditAgeTextChanged("1")
      )

      return listOf(
          patientProfile.patient,
          patientProfile.address,
          if (patientProfile.phoneNumbers.isEmpty()) null else patientProfile.phoneNumbers.first(),
          numberValidationResult,
          preCreateInputEvents + inputEvents,
          shouldSavePatient,
          if (shouldSavePatient) createExpectedPatient(patientProfile.patient) else null,
          if (shouldSavePatient) createExpectedAddress(patientProfile.address) else null,
          expectedPatientPhoneNumber
      )
    }

    return listOf(
        generateTestData(
            patientProfile = generatePatientProfile(false),
            numberValidationResult = VALID,
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Name"),
                PatientEditDistrictTextChanged("District"),
                PatientEditColonyOrVillageChanged("Colony"),
                PatientEditStateTextChanged("State"),
                PatientEditGenderChanged(MALE),
                PatientEditPhoneNumberTextChanged("12345678")),
            shouldSavePatient = true,
            createExpectedPatient = { it.copy(fullName = "Name", gender = MALE) },
            createExpectedAddress = { it.copy(district = "District", colonyOrVillage = "Colony", state = "State") },
            createExpectedPhoneNumber = { patientId, alreadyPresentPhoneNumber ->
              alreadyPresentPhoneNumber?.copy(number = "12345678") ?: PatientMocker.phoneNumber(patientUuid = patientId, number = "12345678")
            }),
        generateTestData(
            patientProfile = generatePatientProfile(shouldAddNumber = false),
            numberValidationResult = VALID,
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Name"),
                PatientEditDistrictTextChanged("District"),
                PatientEditColonyOrVillageChanged("Colony"),
                PatientEditStateTextChanged("State"),
                PatientEditGenderChanged(MALE),
                PatientEditPhoneNumberTextChanged("")),
            shouldSavePatient = true,
            createExpectedPatient =
            { it.copy(fullName = "Name", gender = MALE) },
            createExpectedAddress =
            { it.copy(district = "District", colonyOrVillage = "Colony", state = "State") }),
        generateTestData(
            patientProfile = generatePatientProfile(true),
            numberValidationResult = VALID,
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Name"),
                PatientEditDistrictTextChanged("District"),
                PatientEditStateTextChanged("State"),
                PatientEditGenderChanged(TRANSGENDER),
                PatientEditPhoneNumberTextChanged("123456")),
            shouldSavePatient = true,
            createExpectedPatient = { it.copy(fullName = "Name", gender = TRANSGENDER) },
            createExpectedAddress = { it.copy(district = "District", state = "State") },
            createExpectedPhoneNumber = { patientId, alreadyPresentPhoneNumber ->
              alreadyPresentPhoneNumber?.copy(number = "123456") ?: PatientMocker.phoneNumber(patientUuid = patientId, number = "123456")
            }),
        generateTestData(
            patientProfile = generatePatientProfile(true),
            numberValidationResult = VALID,
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Name 1"),
                PatientEditDistrictTextChanged("District"),
                PatientEditStateTextChanged("State 1"),
                PatientEditGenderChanged(TRANSGENDER),
                PatientEditPhoneNumberTextChanged("123456"),
                PatientEditStateTextChanged("State 2"),
                PatientEditPatientNameTextChanged("Name 2"),
                PatientEditPhoneNumberTextChanged("1234567")),
            shouldSavePatient = true,
            createExpectedPatient = { it.copy(fullName = "Name 2", gender = TRANSGENDER) },
            createExpectedAddress = { it.copy(district = "District", state = "State 2") },
            createExpectedPhoneNumber = { patientId, alreadyPresentPhoneNumber ->
              alreadyPresentPhoneNumber?.copy(number = "1234567") ?: PatientMocker.phoneNumber(patientUuid = patientId, number = "1234567")
            }),
        generateTestData(
            patientProfile = generatePatientProfile(true),
            numberValidationResult = LENGTH_TOO_SHORT,
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Name"),
                PatientEditDistrictTextChanged("District"),
                PatientEditStateTextChanged("State"),
                PatientEditGenderChanged(TRANSGENDER)),
            shouldSavePatient = false),
        generateTestData(
            patientProfile = generatePatientProfile(false),
            numberValidationResult = VALID,
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Name 1"),
                PatientEditDistrictTextChanged("District"),
                PatientEditStateTextChanged("State 1"),
                PatientEditGenderChanged(TRANSGENDER),
                PatientEditPhoneNumberTextChanged("123456"),
                PatientEditStateTextChanged("State 2"),
                PatientEditPatientNameTextChanged("Name 2"),
                PatientEditPhoneNumberTextChanged("1234567"),
                PatientEditPatientNameTextChanged("")),
            shouldSavePatient = false),
        generateTestData(
            patientProfile = generatePatientProfile(true),
            numberValidationResult = LENGTH_TOO_LONG,
            inputEvents = listOf(
                PatientEditPatientNameTextChanged(""),
                PatientEditDistrictTextChanged("District"),
                PatientEditStateTextChanged("State"),
                PatientEditGenderChanged(TRANSGENDER)),
            shouldSavePatient = false),
        generateTestData(
            patientProfile = generatePatientProfile(true),
            numberValidationResult = VALID,
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Name"),
                PatientEditDistrictTextChanged(""),
                PatientEditStateTextChanged("State"),
                PatientEditGenderChanged(TRANSGENDER)),
            shouldSavePatient = false),
        generateTestData(
            patientProfile = generatePatientProfile(false),
            numberValidationResult = BLANK,
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Name"),
                PatientEditDistrictTextChanged("District"),
                PatientEditStateTextChanged(""),
                PatientEditGenderChanged(FEMALE)),
            shouldSavePatient = false)
    )
  }

  @Test
  @Parameters(value = ["true", "false"])
  fun `when the edit age & dob feature flag is set, the feature on the screen must be enabled`(editAgeAndDateOfBirthEnabled: Boolean) {
    config = config.copy(isEditAgeAndDobEnabled = editAgeAndDateOfBirthEnabled)

    whenever(patientRepository.patient(any())).thenReturn(Observable.never())
    whenever(patientRepository.phoneNumbers(any())).thenReturn(Observable.never())

    uiEvents.onNext(PatientEditScreenCreated(UUID.randomUUID()))

    if (editAgeAndDateOfBirthEnabled) {
      verify(screen).enableEditAgeAndDateOfBirthFeature()
    } else {
      verify(screen).disableEditAgeAndDateOfBirthFeature()
    }
  }

  @Test
  fun `when data of birth has focus, the date format should be shown in the label`() {
    uiEvents.onNext(PatientEditDateOfBirthTextChanged(""))
    uiEvents.onNext(PatientEditDateOfBirthFocusChanged(hasFocus = true))
    uiEvents.onNext(PatientEditDateOfBirthFocusChanged(hasFocus = false))
    uiEvents.onNext(PatientEditDateOfBirthFocusChanged(hasFocus = true))
    uiEvents.onNext(PatientEditDateOfBirthFocusChanged(hasFocus = false))

    verify(screen, times(2)).showDatePatternInDateOfBirthLabel()
    verify(screen, times(2)).hideDatePatternInDateOfBirthLabel()
  }

  @Test
  fun `when date of birth text changes, the date format should be shown in the label`() {
    uiEvents.onNext(PatientEditDateOfBirthFocusChanged(hasFocus = false))
    uiEvents.onNext(PatientEditDateOfBirthTextChanged("01/01/1990"))
    uiEvents.onNext(PatientEditDateOfBirthTextChanged(""))
    uiEvents.onNext(PatientEditDateOfBirthTextChanged("01/01/1990"))

    verify(screen, times(2)).showDatePatternInDateOfBirthLabel()
    verify(screen).hideDatePatternInDateOfBirthLabel()
  }

  @Test
  fun `date-of-birth and age fields should only be visible while one of them is empty`() {
    uiEvents.onNext(PatientEditAgeTextChanged(""))
    uiEvents.onNext(PatientEditDateOfBirthTextChanged(""))
    verify(screen).setDateOfBirthAndAgeVisibility(BOTH_VISIBLE)

    uiEvents.onNext(PatientEditDateOfBirthTextChanged("1"))
    verify(screen).setDateOfBirthAndAgeVisibility(DATE_OF_BIRTH_VISIBLE)

    uiEvents.onNext(PatientEditDateOfBirthTextChanged(""))
    uiEvents.onNext(PatientEditAgeTextChanged("1"))
    verify(screen).setDateOfBirthAndAgeVisibility(AGE_VISIBLE)
  }

  @Test()
  fun `when both date-of-birth and age fields have text then an assertion error should be thrown`() {
    errorConsumer = { Truth.assertThat(it).isInstanceOf(AssertionError::class.java) }

    uiEvents.onNext(PatientEditDateOfBirthTextChanged("1"))
    uiEvents.onNext(PatientEditAgeTextChanged("1"))
  }

  @Test
  @Parameters(method = "params for confirming discard changes")
  fun `when back is clicked, the confirm discard changes popup must be shown if there have been changes`(
      existingSavedPatient: Patient,
      existingSavedAddress: PatientAddress,
      existingSavedPhoneNumber: PatientPhoneNumber?,
      inputEvents: List<UiEvent>,
      shouldShowConfirmDiscardChangesPopup: Boolean
  ) {
    whenever(patientRepository.patient(existingSavedPatient.uuid)).thenReturn(Observable.just(existingSavedPatient.toOptional()))
    whenever(patientRepository.phoneNumbers(existingSavedPatient.uuid)).thenReturn(Observable.just(existingSavedPhoneNumber.toOptional()))
    whenever(patientRepository.address(existingSavedAddress.uuid)).thenReturn(Observable.just(existingSavedAddress.toOptional()))

    uiEvents.onNext(PatientEditScreenCreated(existingSavedPatient.uuid))
    inputEvents.forEach { uiEvents.onNext(it) }
    uiEvents.onNext(PatientEditBackClicked())

    if (shouldShowConfirmDiscardChangesPopup) {
      verify(screen).showDiscardChangesAlert()
      verify(screen, never()).goBack()
    } else {
      verify(screen).goBack()
      verify(screen, never()).showDiscardChangesAlert()
    }
  }

  @Suppress("Unused")
  private fun `params for confirming discard changes`(): List<List<Any?>> {

    fun generatePatientProfile(
        name: String? = null,
        phoneNumber: String? = null,
        gender: Gender? = null,
        ageValue: Int? = null,
        dateOfBirthString: String? = null,
        colonyOrVillage: String? = null,
        district: String? = null,
        state: String? = null
    ): PatientProfile {
      val patientUuid = UUID.randomUUID()
      val addressUuid = UUID.randomUUID()

      return PatientProfile(
          patient = PatientMocker.patient(uuid = patientUuid, addressUuid = addressUuid),
          address = PatientMocker.address(uuid = addressUuid),
          phoneNumbers = phoneNumber?.let { listOf(PatientMocker.phoneNumber(patientUuid = patientUuid, number = it)) } ?: emptyList()
      ).let { profile ->
        if (gender != null) {
          return@let profile.copy(patient = profile.patient.copy(gender = gender))
        }
        profile

      }.let { profile ->
        if (name != null) {
          return@let profile.copy(patient = profile.patient.copy(fullName = name))
        }
        profile

      }.let { profile ->
        if (colonyOrVillage != null) {
          return@let profile.copy(address = profile.address.copy(colonyOrVillage = colonyOrVillage))
        }
        profile

      }.let { profile ->
        if (district != null) {
          return@let profile.copy(address = profile.address.copy(district = district))
        }
        profile

      }.let { profile ->
        if (state != null) {
          return@let profile.copy(address = profile.address.copy(state = state))
        }
        profile

      }.let { profile ->
        if(ageValue != null) {
          val age = Age(value = ageValue, updatedAt = Instant.now(clock), computedDateOfBirth = LocalDate.now(clock))
          return@let profile.copy(patient = profile.patient.copy(age = age, dateOfBirth = null))

        } else if(dateOfBirthString != null) {
          val dateOfBirth = LocalDate.parse(dateOfBirthString)
          return@let profile.copy(patient = profile.patient.copy(age = null, dateOfBirth = dateOfBirth))
        }
        profile

      }
    }

    fun generateTestData(
        patientProfile: PatientProfile,
        inputEvents: List<UiEvent>,
        shouldShowConfirmDiscardChangesPopup: Boolean
    ): List<Any?> {
      val preCreateInputEvents = listOf(
          PatientEditPatientNameTextChanged(patientProfile.patient.fullName),
          PatientEditDistrictTextChanged(patientProfile.address.district),
          PatientEditColonyOrVillageChanged(patientProfile.address.colonyOrVillage ?: ""),
          PatientEditStateTextChanged(patientProfile.address.state),
          PatientEditGenderChanged(patientProfile.patient.gender),
          PatientEditPhoneNumberTextChanged(patientProfile.phoneNumbers.firstOrNull()?.number ?: "")
      ) + patientProfile.let { (patient, _, _) ->
        if(patient.age != null) {
          listOf(PatientEditAgeTextChanged(patient.age!!.value.toString()))
        } else {
          listOf(PatientEditDateOfBirthTextChanged(patient.dateOfBirth!!.format(dateOfBirthFormat)))
        }
      }

      return listOf(
          patientProfile.patient,
          patientProfile.address,
          if (patientProfile.phoneNumbers.isEmpty()) null else patientProfile.phoneNumbers.first(),
          preCreateInputEvents + inputEvents,
          shouldShowConfirmDiscardChangesPopup)
    }

    return listOf(
        generateTestData(
            patientProfile = generatePatientProfile(),
            inputEvents = emptyList(),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(
                name = "Anish",
                phoneNumber = "123456",
                gender = FEMALE,
                colonyOrVillage = "Bathinda",
                district = "Hoshiarpur",
                state = "Bengaluru",
                ageValue = 30),
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Anisha"),
                PatientEditPhoneNumberTextChanged("12345"),
                PatientEditGenderChanged(TRANSGENDER),
                PatientEditColonyOrVillageChanged("Batinda"),
                PatientEditDistrictTextChanged("Hosiarpur"),
                PatientEditStateTextChanged("Bangalore"),
                PatientEditAgeTextChanged("32")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(
                name = "Anish",
                phoneNumber = "123456",
                gender = FEMALE,
                colonyOrVillage = "Bathinda",
                district = "Hoshiarpur",
                state = "Bengaluru",
                ageValue = 30),
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Anisha"),
                PatientEditPhoneNumberTextChanged("12345"),
                PatientEditGenderChanged(TRANSGENDER),
                PatientEditColonyOrVillageChanged("Batinda"),
                PatientEditDistrictTextChanged("Hosiarpur"),
                PatientEditStateTextChanged("Bangalore"),
                PatientEditAgeTextChanged(""),
                PatientEditDateOfBirthTextChanged("13/06/1995")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(
                name = "Anish",
                phoneNumber = "123456",
                gender = FEMALE,
                colonyOrVillage = "Bathinda",
                district = "Hoshiarpur",
                state = "Bengaluru",
                dateOfBirthString = "1995-06-13"),
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Anisha"),
                PatientEditPhoneNumberTextChanged("12345"),
                PatientEditGenderChanged(TRANSGENDER),
                PatientEditColonyOrVillageChanged("Batinda"),
                PatientEditDistrictTextChanged("Hosiarpur"),
                PatientEditStateTextChanged("Bangalore"),
                PatientEditDateOfBirthTextChanged("13/06/1994")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(
                name = "Anish",
                phoneNumber = "123456",
                gender = FEMALE,
                colonyOrVillage = "Bathinda",
                district = "Hoshiarpur",
                state = "Bengaluru",
                dateOfBirthString = "1995-06-13"),
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Anisha"),
                PatientEditPhoneNumberTextChanged("12345"),
                PatientEditGenderChanged(TRANSGENDER),
                PatientEditColonyOrVillageChanged("Batinda"),
                PatientEditDistrictTextChanged("Hosiarpur"),
                PatientEditStateTextChanged("Bangalore"),
                PatientEditDateOfBirthTextChanged(""),
                PatientEditAgeTextChanged("30")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(
                name = "Anish",
                phoneNumber = "123456",
                gender = FEMALE,
                colonyOrVillage = "Bathinda",
                district = "Hoshiarpur",
                state = "Bengaluru",
                ageValue = 30),
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Anisha"),
                PatientEditPhoneNumberTextChanged("12345"),
                PatientEditGenderChanged(TRANSGENDER),
                PatientEditColonyOrVillageChanged("Batinda"),
                PatientEditDistrictTextChanged("Hosiarpur"),
                PatientEditStateTextChanged("Bangalore"),
                PatientEditAgeTextChanged("31"),
                PatientEditPatientNameTextChanged("Anish"),
                PatientEditPhoneNumberTextChanged("123456"),
                PatientEditGenderChanged(FEMALE),
                PatientEditColonyOrVillageChanged("Bathinda"),
                PatientEditDistrictTextChanged("Hoshiarpur"),
                PatientEditStateTextChanged("Bengaluru"),
                PatientEditAgeTextChanged("30")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(
                name = "Anish",
                phoneNumber = "123456",
                gender = FEMALE,
                colonyOrVillage = "Bathinda",
                district = "Hoshiarpur",
                state = "Bengaluru",
                dateOfBirthString = "1995-06-13"),
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Anisha"),
                PatientEditPhoneNumberTextChanged("12345"),
                PatientEditGenderChanged(TRANSGENDER),
                PatientEditColonyOrVillageChanged("Batinda"),
                PatientEditDistrictTextChanged("Hosiarpur"),
                PatientEditStateTextChanged("Bangalore"),
                PatientEditDateOfBirthTextChanged("13/06/1996"),
                PatientEditPatientNameTextChanged("Anish"),
                PatientEditPhoneNumberTextChanged("123456"),
                PatientEditGenderChanged(FEMALE),
                PatientEditColonyOrVillageChanged("Bathinda"),
                PatientEditDistrictTextChanged("Hoshiarpur"),
                PatientEditStateTextChanged("Bengaluru"),
                PatientEditDateOfBirthTextChanged("13/06/1995")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(name = "Anish"),
            inputEvents = listOf(PatientEditPatientNameTextChanged("Anisha")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(name = "Anish"),
            inputEvents = listOf(
                PatientEditPatientNameTextChanged("Anisha"),
                PatientEditPatientNameTextChanged("Anish")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(name = "Anish"),
            inputEvents = listOf(PatientEditPatientNameTextChanged("Anish")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(phoneNumber = null),
            inputEvents = listOf(PatientEditPhoneNumberTextChanged("12345")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(phoneNumber = null),
            inputEvents = listOf(
                PatientEditPhoneNumberTextChanged("12345"),
                PatientEditPhoneNumberTextChanged("")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(phoneNumber = null),
            inputEvents = listOf(PatientEditPhoneNumberTextChanged("")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(phoneNumber = "1234567"),
            inputEvents = listOf(PatientEditPhoneNumberTextChanged("12345")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(phoneNumber = "1234567"),
            inputEvents = listOf(
                PatientEditPhoneNumberTextChanged("123456"),
                PatientEditPhoneNumberTextChanged("1234567")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(colonyOrVillage = "Batinda"),
            inputEvents = listOf(PatientEditColonyOrVillageChanged("Bathinda")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(colonyOrVillage = "Batinda"),
            inputEvents = listOf(
                PatientEditColonyOrVillageChanged("Bathinda"),
                PatientEditColonyOrVillageChanged("Batinda")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(colonyOrVillage = "Bathinda"),
            inputEvents = listOf(PatientEditColonyOrVillageChanged("Bathinda")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(district = "Hosiarpur"),
            inputEvents = listOf(PatientEditDistrictTextChanged("Hoshiarpur")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(district = "Hosiarpur"),
            inputEvents = listOf(
                PatientEditDistrictTextChanged("Hoshiarpur"),
                PatientEditDistrictTextChanged("Hosiarpur")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(district = "Hoshiarpur"),
            inputEvents = listOf(PatientEditDistrictTextChanged("Hoshiarpur")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(state = "Bengaluru"),
            inputEvents = listOf(PatientEditStateTextChanged("Bangalore")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(state = "Bengaluru"),
            inputEvents = listOf(
                PatientEditStateTextChanged("Bangalore"),
                PatientEditStateTextChanged("Bengaluru")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(state = "Bengaluru"),
            inputEvents = listOf(PatientEditStateTextChanged("Bengaluru")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(gender = MALE),
            inputEvents = listOf(PatientEditGenderChanged(FEMALE)),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(gender = MALE),
            inputEvents = listOf(
                PatientEditGenderChanged(FEMALE),
                PatientEditGenderChanged(MALE)),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(gender = MALE),
            inputEvents = listOf(PatientEditGenderChanged(MALE)),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(ageValue = 30),
            inputEvents = listOf(PatientEditAgeTextChanged("30")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(ageValue = 30),
            inputEvents = listOf(
                PatientEditAgeTextChanged("31"),
                PatientEditAgeTextChanged("30")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(ageValue = 30),
            inputEvents = listOf(PatientEditAgeTextChanged("31")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(ageValue = 30),
            inputEvents = listOf(
                PatientEditAgeTextChanged(""),
                PatientEditDateOfBirthTextChanged("13/06/1995")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(ageValue = 30),
            inputEvents = listOf(
                PatientEditAgeTextChanged(""),
                PatientEditDateOfBirthTextChanged("13/06/1995"),
                PatientEditDateOfBirthTextChanged(""),
                PatientEditAgeTextChanged("30")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(dateOfBirthString = "1995-06-13"),
            inputEvents = listOf(PatientEditDateOfBirthTextChanged("13/06/1995")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(dateOfBirthString = "1995-06-13"),
            inputEvents = listOf(
                PatientEditDateOfBirthTextChanged("13/06/1996"),
                PatientEditDateOfBirthTextChanged("13/06/1995")),
            shouldShowConfirmDiscardChangesPopup = false),
        generateTestData(
            patientProfile = generatePatientProfile(dateOfBirthString = "1995-06-13"),
            inputEvents = listOf(PatientEditDateOfBirthTextChanged("13/06/1996")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(dateOfBirthString = "1995-06-13"),
            inputEvents = listOf(
                PatientEditDateOfBirthTextChanged(""),
                PatientEditAgeTextChanged("30")),
            shouldShowConfirmDiscardChangesPopup = true),
        generateTestData(
            patientProfile = generatePatientProfile(dateOfBirthString = "1995-06-13"),
            inputEvents = listOf(
                PatientEditDateOfBirthTextChanged(""),
                PatientEditAgeTextChanged("30"),
                PatientEditAgeTextChanged(""),
                PatientEditDateOfBirthTextChanged("13/06/1995")),
            shouldShowConfirmDiscardChangesPopup = false))
  }
}
