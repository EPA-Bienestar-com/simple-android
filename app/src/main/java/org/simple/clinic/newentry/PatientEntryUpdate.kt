package org.simple.clinic.newentry

import com.spotify.mobius.Next
import com.spotify.mobius.Next.next
import com.spotify.mobius.Update
import org.simple.clinic.mobius.dispatch
import org.simple.clinic.mobius.next
import org.simple.clinic.newentry.Field.ColonyOrVillage
import org.simple.clinic.newentry.Field.DateOfBirth
import org.simple.clinic.newentry.Field.District
import org.simple.clinic.newentry.Field.FullName
import org.simple.clinic.newentry.Field.PhoneNumber
import org.simple.clinic.newentry.Field.State
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.OngoingNewPatientEntry
import org.simple.clinic.registration.phone.PhoneNumberValidator
import org.simple.clinic.util.Optional
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator

typealias PatientEntryNext = Next<PatientEntryModel, PatientEntryEffect>

class PatientEntryUpdate(
    private val phoneNumberValidator: PhoneNumberValidator,
    private val dobValidator: UserInputDateValidator
) : Update<PatientEntryModel, PatientEntryEvent, PatientEntryEffect> {
  override fun update(model: PatientEntryModel, event: PatientEntryEvent): PatientEntryNext {
    return when (event) {
      is OngoingEntryFetched -> onOngoingEntryFetched(model, event.patientEntry)
      is GenderChanged -> onGenderChanged(model, event.gender)
      is AgeChanged -> next(model.withAge(event.age), HideEmptyDateOfBirthAndAgeError)
      is DateOfBirthChanged -> next(model.withDateOfBirth(event.dateOfBirth), HideValidationError(DateOfBirth))
      is FullNameChanged -> next(model.withFullName(event.fullName), HideValidationError(FullName))
      is PhoneNumberChanged -> next(model.withPhoneNumber(event.phoneNumber), HideValidationError(PhoneNumber))
      is ColonyOrVillageChanged -> next(model.withColonyOrVillage(event.colonyOrVillage), HideValidationError(ColonyOrVillage))
      is DistrictChanged -> next(model.withDistrict(event.district), HideValidationError(District))
      is StateChanged -> next(model.withState(event.state), HideValidationError(State))
      is DateOfBirthFocusChanged -> onDateOfBirthFocusChanged(model, event.hasFocus)
      is SaveClicked -> onSaveClicked(model.patientEntry)
      is ReminderConsentChanged -> next(model.reminderConsentChanged(event.reminderConsent))
      PatientEntrySaved -> dispatch(OpenMedicalHistoryEntryScreen)
    }
  }

  private fun onOngoingEntryFetched(
      model: PatientEntryModel,
      patientEntry: OngoingNewPatientEntry
  ): PatientEntryNext =
      next(model.patientEntryFetched(patientEntry), PrefillFields(patientEntry))

  private fun onGenderChanged(model: PatientEntryModel, gender: Optional<Gender>): PatientEntryNext {
    val updatedModel = model.withGender(gender)
    return if (gender.isNotEmpty() && model.isSelectingGenderForTheFirstTime) {
      next(updatedModel, HideValidationError(Field.Gender), ScrollFormOnGenderSelection)
    } else {
      next(updatedModel)
    }
  }

  private fun onDateOfBirthFocusChanged(model: PatientEntryModel, hasFocus: Boolean): PatientEntryNext {
    val hasDateOfBirth = model.patientEntry.personalDetails?.dateOfBirth?.isNotBlank() == true
    return dispatch(ShowDatePatternInDateOfBirthLabel(hasFocus || hasDateOfBirth))
  }

  private fun onSaveClicked(
      patientEntry: OngoingNewPatientEntry
  ): PatientEntryNext {
    val validationErrors = patientEntry.validationErrors(dobValidator, phoneNumberValidator)
    val effect = if (validationErrors.isEmpty()) {
      SavePatient(patientEntry)
    } else {
      ShowValidationErrors(validationErrors)
    }
    return dispatch(effect)
  }
}
