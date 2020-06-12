package org.simple.clinic.bloodsugar

import android.os.Parcelable
import androidx.annotation.StringRes
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.R
import org.simple.clinic.bloodsugar.entry.ValidationResult
import org.simple.clinic.bloodsugar.entry.ValidationResult.ErrorBloodSugarEmpty
import org.simple.clinic.bloodsugar.entry.ValidationResult.ErrorBloodSugarTooHigh
import org.simple.clinic.bloodsugar.entry.ValidationResult.ErrorBloodSugarTooLow
import org.simple.clinic.bloodsugar.entry.ValidationResult.Valid

@Parcelize
data class BloodSugarReading(val value: String, val type: BloodSugarMeasurementType) : Parcelable {
  val isHigh: Boolean
    get() {
      return when (type) {
        Random,
        PostPrandial -> value.toFloat() >= 200
        Fasting -> value.toFloat() >= 126
        HbA1c -> value.toFloat() >= 7.0
        else -> false
      }
    }

  val isLow: Boolean
    get() {
      return when (type) {
        Random,
        PostPrandial,
        Fasting -> value.toFloat() < 70
        else -> false
      }
    }

  val displayValue: String
    get() {
      return when (type) {
        Random -> value.toFloat().toInt().toString()
        PostPrandial -> value.toFloat().toInt().toString()
        Fasting -> value.toFloat().toInt().toString()
        HbA1c -> value.toFloat().toString()
        is Unknown -> value.toInt().toString()
      }
    }

  @get:StringRes
  val displayType: Int
    get() = when (type) {
      Random -> R.string.bloodsugar_reading_type_rbs
      PostPrandial -> R.string.bloodsugar_reading_type_ppbs
      Fasting -> R.string.bloodsugar_reading_type_fbs
      HbA1c -> R.string.bloodsugar_reading_type_hba1c
      else -> throw IllegalArgumentException("Unknown blood sugar type $type")
    }

  @get:StringRes
  val displayUnit: Int
    get() = when (type) {
      Random, PostPrandial, Fasting, is Unknown -> R.string.bloodsugar_reading_unit_type_mg_dl
      HbA1c -> R.string.bloodsugar_reading_unit_type_percentage
    }

  val displayUnitSeparator: String
    get() {
      return when (type) {
        Random, PostPrandial, Fasting, is Unknown -> " "
        HbA1c -> ""
      }
    }

  fun readingChanged(newReading: String): BloodSugarReading = copy(value = newReading)

  fun validate(): ValidationResult {
    if (value.isBlank()) {
      return ErrorBloodSugarEmpty
    }

    val bloodSugarNumber = value.toFloat()
    val minAllowedBloodSugarValue = if (type is HbA1c) 3 else 30
    val maxAllowedBloodSugarValue = if (type is HbA1c) 25 else 1000

    return when {
      bloodSugarNumber < minAllowedBloodSugarValue -> ErrorBloodSugarTooLow(type)
      bloodSugarNumber > maxAllowedBloodSugarValue -> ErrorBloodSugarTooHigh(type)
      else -> Valid(bloodSugarNumber)
    }
  }
}
