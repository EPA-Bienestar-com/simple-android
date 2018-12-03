package org.simple.clinic.patient

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.widgets.ageanddateofbirth.DateOfBirthFormatValidator
import org.simple.clinic.widgets.ageanddateofbirth.DateOfBirthFormatValidator.Result
import org.simple.clinic.patient.OngoingNewPatientEntry.Address
import org.simple.clinic.patient.OngoingNewPatientEntry.PersonalDetails
import org.simple.clinic.patient.OngoingNewPatientEntry.PhoneNumber
import org.simple.clinic.registration.phone.PhoneNumberValidator
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.VALID
import org.simple.clinic.registration.phone.PhoneNumberValidator.Type.LANDLINE_OR_MOBILE

@RunWith(JUnitParamsRunner::class)
class OngoingNewPatientEntryTest {

  @Test
  @Parameters(method = "values")
  fun `validation should fail for invalid values for phone numbers in India`(
      isValid: Boolean,
      fullname: String,
      dateOfBirth: String?,
      age: String?,
      colonyOrVillage: String,
      district: String,
      state: String,
      phoneValidationResult: PhoneNumberValidator.Result
  ) {
    val entry = OngoingNewPatientEntry(
        personalDetails = PersonalDetails(fullname, dateOfBirth, age, Gender.MALE),
        address = Address(colonyOrVillage, district, state),
        phoneNumber = PhoneNumber(""))

    val dobValidator = DateOfBirthFormatValidator()
    val numValidator = mock<PhoneNumberValidator>()
    whenever(numValidator.validate("", LANDLINE_OR_MOBILE)).thenReturn(phoneValidationResult)

    assertThat(entry.validationErrors(dobValidator, numValidator)).apply {
      if (isValid) {
        isEmpty()
      } else {
        isNotEmpty()
      }
    }
  }

  fun values(): Array<Any> {
    return arrayOf(
        arrayOf(false, " ", " ", " ", " ", " ", " ", PhoneNumberValidator.Result.BLANK),
        arrayOf(false, " ", null, null, " ", " ", " ", PhoneNumberValidator.Result.BLANK),
        arrayOf(false, "Ashok Kumar", "01-01-1971", "47", "colony", "state", "district", PhoneNumberValidator.Result.LENGTH_TOO_SHORT),
        arrayOf(false, "Ashok Kumar", "01/01/1971", null, "colony", "state", "district", PhoneNumberValidator.Result.BLANK),
        arrayOf(false, "Ashok Kumar", "01/01/1971", null, "colony", "state", "district", PhoneNumberValidator.Result.LENGTH_TOO_SHORT),
        arrayOf(false, "Ashok Kumar", "01/01/1971", null, "colony", "state", "district", PhoneNumberValidator.Result.LENGTH_TOO_LONG),
        arrayOf(true, "Ashok Kumar", "01/01/1971", null, "colony", "state", "district", PhoneNumberValidator.Result.VALID)
    )
  }

  @Test
  fun `future date-of-birth should not be accepted`() {
    val entry = OngoingNewPatientEntry(
        personalDetails = PersonalDetails("Ashok", "01/01/3000", "", Gender.MALE),
        address = Address("colony", "district", "state"),
        phoneNumber = PhoneNumber("phone-number"))

    val mockDobValidator = mock<DateOfBirthFormatValidator>()
    whenever(mockDobValidator.validate("01/01/3000")).thenReturn(Result.DATE_IS_IN_FUTURE)

    val mockNumValidator = mock<PhoneNumberValidator>()
    whenever(mockNumValidator.validate("phone-number", LANDLINE_OR_MOBILE)).thenReturn(VALID)

    val validationErrors = entry.validationErrors(mockDobValidator, mockNumValidator)

    assertThat(validationErrors).hasSize(1)
    assertThat(validationErrors).contains(PatientEntryValidationError.DATE_OF_BIRTH_IN_FUTURE)
  }
}
