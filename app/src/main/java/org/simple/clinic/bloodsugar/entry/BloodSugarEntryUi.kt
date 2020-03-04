package org.simple.clinic.bloodsugar.entry

import org.simple.clinic.bloodsugar.BloodSugarMeasurementType
import org.threeten.bp.LocalDate
import java.util.UUID

interface BloodSugarEntryUi {
  fun setBloodSugarSavedResultAndFinish()
  fun hideBloodSugarErrorMessage()
  fun showBloodSugarEmptyError()
  fun showBloodSugarHighError(measurementType: BloodSugarMeasurementType)
  fun showBloodSugarLowError(measurementType: BloodSugarMeasurementType)
  fun showBloodSugarEntryScreen()
  fun showDateEntryScreen()
  fun showInvalidDateError()
  fun showDateIsInFutureError()
  fun hideDateErrorMessage()
  fun setDateOnInputFields(dayOfMonth: String, month: String, twoDigitYear: String)
  fun showDateOnDateButton(date: LocalDate)
  fun showRemoveButton()
  fun hideRemoveButton()
  fun setBloodSugarReading(bloodSugarReading: String)
  fun dismiss()
  fun showConfirmRemoveBloodSugarDialog(bloodSugarMeasurementUuid: UUID)
  fun showEntryTitle(measurementType: BloodSugarMeasurementType)
  fun showEditTitle(measurementType: BloodSugarMeasurementType)
}
