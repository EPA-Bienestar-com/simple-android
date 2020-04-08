package org.simple.clinic.editpatient

import com.spotify.mobius.Next
import com.spotify.mobius.Next.next
import com.spotify.mobius.Update
import org.simple.clinic.editpatient.EditPatientValidationError.BOTH_DATEOFBIRTH_AND_AGE_ABSENT
import org.simple.clinic.editpatient.EditPatientValidationError.COLONY_OR_VILLAGE_EMPTY
import org.simple.clinic.editpatient.EditPatientValidationError.DATE_OF_BIRTH_IN_FUTURE
import org.simple.clinic.editpatient.EditPatientValidationError.DATE_OF_BIRTH_PARSE_ERROR
import org.simple.clinic.editpatient.EditPatientValidationError.DISTRICT_EMPTY
import org.simple.clinic.editpatient.EditPatientValidationError.FULL_NAME_EMPTY
import org.simple.clinic.editpatient.EditPatientValidationError.PHONE_NUMBER_EMPTY
import org.simple.clinic.editpatient.EditPatientValidationError.PHONE_NUMBER_LENGTH_TOO_LONG
import org.simple.clinic.editpatient.EditPatientValidationError.PHONE_NUMBER_LENGTH_TOO_SHORT
import org.simple.clinic.editpatient.EditPatientValidationError.STATE_EMPTY
import org.simple.clinic.mobius.dispatch
import org.simple.clinic.mobius.next
import org.simple.clinic.registration.phone.PhoneNumberValidator
import org.simple.clinic.widgets.ageanddateofbirth.UserInputAgeValidator
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator

class EditPatientUpdate(
    private val numberValidator: PhoneNumberValidator,
    private val dobValidator: UserInputDateValidator,
    private val ageValidator: UserInputAgeValidator
) : Update<EditPatientModel, EditPatientEvent, EditPatientEffect> {
  private val errorsForEventType = mapOf(
      PhoneNumberChanged::class to setOf(PHONE_NUMBER_EMPTY, PHONE_NUMBER_LENGTH_TOO_LONG, PHONE_NUMBER_LENGTH_TOO_SHORT),
      NameChanged::class to setOf(FULL_NAME_EMPTY),
      ColonyOrVillageChanged::class to setOf(COLONY_OR_VILLAGE_EMPTY),
      StateChanged::class to setOf(STATE_EMPTY),
      DistrictChanged::class to setOf(DISTRICT_EMPTY),
      AgeChanged::class to setOf(BOTH_DATEOFBIRTH_AND_AGE_ABSENT),
      DateOfBirthChanged::class to setOf(DATE_OF_BIRTH_PARSE_ERROR, DATE_OF_BIRTH_IN_FUTURE)
  )

  override fun update(
      model: EditPatientModel,
      event: EditPatientEvent
  ): Next<EditPatientModel, EditPatientEffect> {
    return when (event) {
      is NameChanged -> onTextFieldChanged(event) { model.updateName(event.name) }
      is GenderChanged -> next(model.updateGender(event.gender))
      is PhoneNumberChanged -> onTextFieldChanged(event) { model.updatePhoneNumber(event.phoneNumber) }
      is ColonyOrVillageChanged -> onTextFieldChanged(event) { model.updateColonyOrVillage(event.colonyOrVillage) }
      is DistrictChanged -> onTextFieldChanged(event) { model.updateDistrict(event.district) }
      is StateChanged -> onTextFieldChanged(event) { model.updateState(event.state) }
      is DateOfBirthFocusChanged -> onDateOfBirthFocusChanged(event)
      is DateOfBirthChanged -> onDateOfBirthChanged(model, event)
      is AgeChanged -> onTextFieldChanged(event) { model.updateAge(event.age) }
      is ZoneChanged -> next(model.updateZone(event.zone))
      is StreetAddressChanged -> next(model.updateStreetAddress(event.streetAddress))
      is BackClicked -> onBackClicked(model)
      is PatientSaved -> dispatch(GoBackEffect)
      is SaveClicked -> onSaveClicked(model)
      is AlternativeIdChanged -> next(model.updateBangladeshNationalId(event.alternativeId))
    }
  }

  private fun onTextFieldChanged(
      event: EditPatientEvent,
      modifier: () -> EditPatientModel
  ): Next<EditPatientModel, EditPatientEffect> = next(
      modifier(),
      HideValidationErrorsEffect(errorsForEventType.getValue(event::class))
  )

  private fun onDateOfBirthFocusChanged(
      event: DateOfBirthFocusChanged
  ): Next<EditPatientModel, EditPatientEffect> {
    val showOrHideLabelEffect = if (event.hasFocus) {
      ShowDatePatternInDateOfBirthLabelEffect
    } else {
      HideDatePatternInDateOfBirthLabelEffect
    }
    return dispatch(showOrHideLabelEffect)
  }

  private fun onDateOfBirthChanged(
      model: EditPatientModel,
      event: DateOfBirthChanged
  ): Next<EditPatientModel, EditPatientEffect> {
    val effects = if (event.dateOfBirth.isBlank()) {
      setOf(HideValidationErrorsEffect(errorsForEventType.getValue(event::class)))
    } else {
      setOf(ShowDatePatternInDateOfBirthLabelEffect, HideValidationErrorsEffect(errorsForEventType.getValue(event::class)))
    }

    return next(model.updateDateOfBirth(event.dateOfBirth), effects)
  }

  private fun onBackClicked(
      model: EditPatientModel
  ): Next<EditPatientModel, EditPatientEffect> {
    val effect = if (model.savedEntry != model.ongoingEntry) {
      ShowDiscardChangesAlertEffect
    } else {
      GoBackEffect
    }
    return dispatch(effect)
  }

  private fun onSaveClicked(
      model: EditPatientModel
  ): Next<EditPatientModel, EditPatientEffect> {
    val validationErrors = model.ongoingEntry.validate(model.savedPhoneNumber, numberValidator, dobValidator, ageValidator)
    val effect = if (validationErrors.isEmpty()) {
      val (_, ongoingEntry, savedPatient, savedAddress, savedPhoneNumber, savedBangladeshId) = model
      SavePatientEffect(ongoingEntry, savedPatient, savedAddress, savedPhoneNumber, savedBangladeshId)
    } else {
      ShowValidationErrorsEffect(validationErrors)
    }

    return dispatch(effect)
  }
}
