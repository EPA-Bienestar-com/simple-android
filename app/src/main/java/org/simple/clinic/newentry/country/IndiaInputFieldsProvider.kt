package org.simple.clinic.newentry.country

import org.simple.clinic.R
import org.simple.clinic.newentry.form.AgeField
import org.simple.clinic.newentry.form.DateOfBirthField
import org.simple.clinic.newentry.form.DistrictField
import org.simple.clinic.newentry.form.GenderField
import org.simple.clinic.newentry.form.InputField
import org.simple.clinic.newentry.form.LandlineOrMobileField
import org.simple.clinic.newentry.form.PatientNameField
import org.simple.clinic.newentry.form.StateField
import org.simple.clinic.newentry.form.StreetAddressField
import org.simple.clinic.newentry.form.VillageOrColonyField
import org.simple.clinic.patient.Gender
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class IndiaInputFieldsProvider (
    private val dateTimeFormatter: DateTimeFormatter,
    private val today: LocalDate
): InputFieldsProvider {

  override fun provide(): List<InputField<*>> {
    return listOf(
        PatientNameField(R.string.patiententry_full_name),
        AgeField(R.string.patiententry_age),
        DateOfBirthField(dateTimeFormatter, today, R.string.patiententry_date_of_birth_unfocused),
        LandlineOrMobileField(R.string.patiententry_phone_number),
        StreetAddressField(R.string.patiententry_street_address),
        GenderField(_labelResId = 0, allowedGenders = setOf(Gender.Male, Gender.Female, Gender.Transgender)),
        VillageOrColonyField(R.string.patiententry_village_colony_ward),
        DistrictField(R.string.patiententry_district),
        StateField(R.string.patiententry_state)
    )
  }
}
