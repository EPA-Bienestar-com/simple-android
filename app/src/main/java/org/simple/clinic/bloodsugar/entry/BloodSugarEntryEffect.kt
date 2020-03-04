package org.simple.clinic.bloodsugar.entry

import org.simple.clinic.bloodsugar.BloodSugarReading
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.UUID

sealed class BloodSugarEntryEffect

sealed class PrefillDate : BloodSugarEntryEffect() {
  companion object {
    fun forNewEntry(): PrefillDate {
      return PrefillCurrentDate
    }

    fun forUpdateEntry(date: Instant): PrefillDate {
      return PrefillSpecificDate(date)
    }
  }

  object PrefillCurrentDate : PrefillDate()

  data class PrefillSpecificDate(val date: Instant) : PrefillDate()
}

object HideBloodSugarErrorMessage : BloodSugarEntryEffect()

object HideDateErrorMessage : BloodSugarEntryEffect()

object Dismiss : BloodSugarEntryEffect()

object ShowDateEntryScreen : BloodSugarEntryEffect()

data class ShowBloodSugarValidationError(val result: BloodSugarValidator.Result) : BloodSugarEntryEffect()

data class ShowBloodSugarEntryScreen(val date: LocalDate) : BloodSugarEntryEffect()

data class ShowDateValidationError(val result: UserInputDateValidator.Result) : BloodSugarEntryEffect()

data class CreateNewBloodSugarEntry(
    val patientUuid: UUID,
    val userEnteredDate: LocalDate,
    val prefilledDate: LocalDate,
    val bloodSugarReading: BloodSugarReading
) : BloodSugarEntryEffect() {
  val wasDateChanged: Boolean
    get() = userEnteredDate != prefilledDate
}

data class UpdateBloodSugarEntry(
    val bloodSugarMeasurementUuid: UUID,
    val userEnteredDate: LocalDate,
    val prefilledDate: LocalDate,
    val bloodSugarReading: BloodSugarReading
) : BloodSugarEntryEffect() {
  val wasDateChanged: Boolean
    get() = userEnteredDate != prefilledDate
}

object SetBloodSugarSavedResultAndFinish : BloodSugarEntryEffect()

data class FetchBloodSugarMeasurement(val bloodSugarMeasurementUuid: UUID) : BloodSugarEntryEffect()

data class SetBloodSugarReading(val bloodSugarReading: String) : BloodSugarEntryEffect()

data class ShowConfirmRemoveBloodSugarDialog(val bloodSugarMeasurementUuid: UUID) : BloodSugarEntryEffect()
