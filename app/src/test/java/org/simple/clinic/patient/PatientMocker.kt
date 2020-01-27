package org.simple.clinic.patient

import com.nhaarman.mockito_kotlin.mock
import org.simple.clinic.appconfig.Country
import org.simple.clinic.bloodsugar.BloodSugarMeasurement
import org.simple.clinic.bloodsugar.BloodSugarReading
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.facility.Facility
import org.simple.clinic.facility.FacilityConfig
import org.simple.clinic.facility.FacilityPayload
import org.simple.clinic.home.overdue.OverdueAppointment
import org.simple.clinic.location.Coordinates
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.Answer.No
import org.simple.clinic.medicalhistory.Answer.Yes
import org.simple.clinic.medicalhistory.MedicalHistory
import org.simple.clinic.overdue.Appointment
import org.simple.clinic.overdue.AppointmentCancelReason
import org.simple.clinic.patient.ReminderConsent.Granted
import org.simple.clinic.patient.businessid.BusinessId
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.protocol.Protocol
import org.simple.clinic.protocol.ProtocolDrug
import org.simple.clinic.storage.Timestamps
import org.simple.clinic.user.LoggedInUserPayload
import org.simple.clinic.user.User
import org.simple.clinic.user.UserStatus
import org.simple.clinic.util.randomGender
import org.simple.clinic.util.randomPatientPhoneNumberType
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset.UTC
import java.net.URI
import java.util.Random
import java.util.UUID
import kotlin.reflect.KClass

private fun <T : Enum<T>> randomOfEnum(enumClass: KClass<T>): T {
  return enumClass.java.enumConstants!!.asList().shuffled().first()
}

object PatientMocker {

  private val random = Random()

  fun patient(
      uuid: UUID = UUID.randomUUID(),
      addressUuid: UUID = UUID.randomUUID(),
      fullName: String = "name",
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class),
      status: PatientStatus = PatientStatus.Active,
      gender: Gender = randomGender(),
      dateOfBirth: LocalDate? = LocalDate.now(UTC),
      age: Age? = null,
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      recordedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ): Patient {
    return Patient(
        uuid = uuid,
        addressUuid = addressUuid,
        fullName = fullName,
        gender = gender,
        dateOfBirth = dateOfBirth,
        age = age,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        recordedAt = recordedAt,
        syncStatus = syncStatus,
        reminderConsent = Granted
    )
  }

  fun address(
      uuid: UUID = UUID.randomUUID(),
      streetAddress: String? = "street address",
      colonyOrVillage: String? = "colony/village",
      district: String = "district",
      zone: String? = "zone",
      state: String = "state",
      country: String = "India",
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ): PatientAddress {
    return PatientAddress(
        uuid = uuid,
        streetAddress = streetAddress,
        colonyOrVillage = colonyOrVillage,
        zone = zone,
        district = district,
        state = state,
        country = country,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt)
  }

  fun bp(
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      systolic: Int = random.nextInt(100) + 100,
      diastolic: Int = random.nextInt(100),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class),
      userUuid: UUID = UUID.randomUUID(),
      facilityUuid: UUID = UUID.randomUUID(),
      deletedAt: Instant? = null,
      recordedAt: Instant = Instant.now()
  ): BloodPressureMeasurement {
    return BloodPressureMeasurement(
        uuid = uuid,
        systolic = systolic,
        diastolic = diastolic,
        syncStatus = syncStatus,
        userUuid = userUuid,
        facilityUuid = facilityUuid,
        patientUuid = patientUuid,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        recordedAt = recordedAt
    )
  }

  fun facility(
      uuid: UUID = mock(),
      name: String = "some facility",
      streetAddress: String? = "some street",
      district: String = "district",
      state: String = "state",
      facilityType: String? = null,
      villageOrColony: String? = null,
      country: String = "India",
      pinCode: String? = null,
      protocolUuid: UUID? = UUID.randomUUID(),
      groupUuid: UUID? = UUID.randomUUID(),
      location: Coordinates? = Coordinates(latitude = 1.908537, longitude = 73.537524),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class),
      facilityConfig: FacilityConfig = FacilityConfig(diabetesManagementEnabled = false)
  ): Facility {
    return Facility(
        uuid = uuid,
        name = name,
        facilityType = facilityType,
        streetAddress = streetAddress,
        villageOrColony = villageOrColony,
        district = district,
        state = state,
        country = country,
        pinCode = pinCode,
        protocolUuid = protocolUuid,
        groupUuid = groupUuid,
        location = location,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
        deletedAt = deletedAt,
        config = facilityConfig
    )
  }

  fun facilityPayload(
      uuid: UUID = mock(),
      name: String = "some facility",
      streetAddress: String? = "some street",
      district: String = "district",
      state: String = "state",
      facilityType: String? = null,
      villageOrColony: String? = null,
      country: String = "India",
      pinCode: String? = null,
      protocolUuid: UUID = UUID.randomUUID(),
      groupUuid: UUID = UUID.randomUUID(),
      locationLatitude: Double? = 1.908537,
      locationLongitude: Double? = 73.537524,
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      facilityConfig: FacilityConfig = FacilityConfig(diabetesManagementEnabled = false)
  ): FacilityPayload {
    return FacilityPayload(
        uuid = uuid,
        name = name,
        facilityType = facilityType,
        streetAddress = streetAddress,
        villageOrColony = villageOrColony,
        district = district,
        state = state,
        country = country,
        pinCode = pinCode,
        protocolUuid = protocolUuid,
        groupUuid = groupUuid,
        locationLatitude = locationLatitude,
        locationLongitude = locationLongitude,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        config = facilityConfig
    )
  }

  fun prescription(
      uuid: UUID = mock(),
      name: String = "drug name",
      dosage: String? = "dosage",
      isProtocolDrug: Boolean = false,
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class),
      patientUuid: UUID = UUID.randomUUID(),
      facilityUuid: UUID = UUID.randomUUID(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      isDeleted: Boolean = false
  ): PrescribedDrug {
    return PrescribedDrug(
        uuid = uuid,
        name = name,
        dosage = dosage,
        rxNormCode = "rx-norm-code",
        isDeleted = isDeleted,
        isProtocolDrug = isProtocolDrug,
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt)
  }

  fun protocol(uuid: UUID, followUpDays: Int) = Protocol(
      uuid = uuid,
      name = "name",
      followUpDays = followUpDays,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      syncStatus = SyncStatus.DONE,
      deletedAt = null
  )

  fun protocolDrug(
      uuid: UUID = UUID.randomUUID(),
      name: String = "drug name",
      dosage: String = "5mg",
      protocolUuid: UUID = UUID.randomUUID(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant = Instant.now(),
      order: Int = 0
  ): ProtocolDrug {
    return ProtocolDrug(
        uuid = uuid,
        name = name,
        rxNormCode = "rxnormcode-1",
        dosage = dosage,
        protocolUuid = protocolUuid,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        order = order)
  }

  fun appointment(
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      scheduledDate: LocalDate = LocalDate.now(UTC),
      facilityUuid: UUID = UUID.randomUUID(),
      status: Appointment.Status = Appointment.Status.Scheduled,
      cancelReason: AppointmentCancelReason? = AppointmentCancelReason.PatientNotResponding,
      syncStatus: SyncStatus = SyncStatus.PENDING,
      agreedToVisit: Boolean? = null,
      remindOn: LocalDate? = LocalDate.now(UTC).minusDays(2),
      appointmentType: Appointment.AppointmentType = Appointment.AppointmentType.random(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      creationFacilityUuid: UUID? = null
  ): Appointment {
    return Appointment(
        uuid = uuid,
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        scheduledDate = scheduledDate,
        status = status,
        cancelReason = cancelReason,
        remindOn = remindOn,
        agreedToVisit = agreedToVisit,
        appointmentType = appointmentType,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        creationFacilityUuid = creationFacilityUuid
    )
  }

  fun overdueAppointment(
      userUuid: UUID = UUID.randomUUID(),
      facilityUuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      bpUuid: UUID = UUID.randomUUID(),
      phoneNumberUuid: UUID = UUID.randomUUID(),
      appointmentUuid: UUID = UUID.randomUUID(),
      name: String = "somebody",
      bloodPressureMeasurement: BloodPressureMeasurement = bp(
          uuid = bpUuid,
          userUuid = userUuid,
          facilityUuid = facilityUuid,
          patientUuid = patientUuid
      ),
      isHighRisk: Boolean = false,
      gender: Gender = Gender.Transgender,
      dateOfBirth: LocalDate? = LocalDate.now(UTC).minusYears(30),
      age: Age? = null,
      phoneNumber: PatientPhoneNumber? = phoneNumber(uuid = phoneNumberUuid, patientUuid = patientUuid),
      appointment: Appointment = appointment(uuid = appointmentUuid, patientUuid = patientUuid, facilityUuid = facilityUuid)
  ): OverdueAppointment {
    return OverdueAppointment(
        fullName = name,
        gender = gender,
        dateOfBirth = dateOfBirth,
        age = age,
        phoneNumber = phoneNumber,
        appointment = appointment,
        bloodPressure = bloodPressureMeasurement,
        isAtHighRisk = isHighRisk
        )
  }

  fun loggedInUser(
      uuid: UUID = UUID.randomUUID(),
      name: String = "a name",
      phone: String = "a phone",
      pinDigest: String = "a hash",
      status: UserStatus = UserStatus.WaitingForApproval,
      loggedInStatus: User.LoggedInStatus = User.LoggedInStatus.LOGGED_IN,
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now()
  ): User {
    return User(
        uuid = uuid,
        fullName = name,
        phoneNumber = phone,
        pinDigest = pinDigest,
        createdAt = createdAt,
        status = status,
        updatedAt = updatedAt,
        loggedInStatus = loggedInStatus
    )
  }

  fun loggedInUserPayload(
      uuid: UUID = UUID.randomUUID(),
      name: String = "a name",
      phone: String = "a phone",
      pinDigest: String = "a hash",
      registrationFacilityUuid: UUID = UUID.randomUUID(),
      status: UserStatus = UserStatus.WaitingForApproval,
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now()
  ): LoggedInUserPayload {
    return LoggedInUserPayload(
        uuid = uuid,
        fullName = name,
        phoneNumber = phone,
        pinDigest = pinDigest,
        registrationFacilityId = registrationFacilityUuid,
        createdAt = createdAt,
        status = status,
        updatedAt = updatedAt)
  }

  fun patientSearchResult(
      uuid: UUID = UUID.randomUUID(),
      fullName: String = "Ashok Kumar",
      phoneNumber: String = "3.14159"
  ): PatientSearchResult {
    return PatientSearchResult(
        uuid = uuid,
        fullName = fullName,
        gender = mock(),
        dateOfBirth = null,
        age = mock(),
        status = PatientStatus.Active,
        createdAt = mock(),
        updatedAt = mock(),
        address = address(),
        syncStatus = mock(),
        phoneNumber = phoneNumber,
        phoneType = mock(),
        phoneUuid = mock(),
        phoneActive = true,
        phoneCreatedAt = mock(),
        phoneUpdatedAt = mock(),
        lastBp = PatientSearchResult.LastBp(
            uuid = UUID.randomUUID(),
            takenOn = Instant.now(),
            takenAtFacilityName = "Some Facility",
            takenAtFacilityUuid = UUID.randomUUID()))
  }

  fun medicalHistory(
      hasHadHeartAttack: Answer = Yes,
      hasHadStroke: Answer = No,
      hasHadKidneyDisease: Answer = Yes,
      diagnosedWithHypertension: Answer = No,
      isOnTreatmentForHypertension: Answer = Yes,
      hasDiabetes: Answer = Yes,
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      syncStatus: SyncStatus = SyncStatus.PENDING,
      deletedAt: Instant? = null
  ): MedicalHistory {
    return MedicalHistory(
        uuid = UUID.randomUUID(),
        patientUuid = UUID.randomUUID(),
        diagnosedWithHypertension = diagnosedWithHypertension,
        isOnTreatmentForHypertension = isOnTreatmentForHypertension,
        hasHadHeartAttack = hasHadHeartAttack,
        hasHadStroke = hasHadStroke,
        hasHadKidneyDisease = hasHadKidneyDisease,
        hasDiabetes = hasDiabetes,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt)
  }

  fun phoneNumber(
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      number: String = "1111111111",
      phoneType: PatientPhoneNumberType = randomPatientPhoneNumberType(),
      active: Boolean = true,
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ) = PatientPhoneNumber(
      uuid = uuid,
      patientUuid = patientUuid,
      number = number,
      phoneType = phoneType,
      active = active,
      createdAt = createdAt,
      updatedAt = updatedAt,
      deletedAt = deletedAt
  )

  fun recentPatient(
      uuid: UUID = UUID.randomUUID(),
      fullName: String = "fullName",
      gender: Gender = randomGender(),
      dateOfBirth: LocalDate? = null,
      age: Age? = null,
      updatedAt: Instant = Instant.now()
  ) = RecentPatient(
      uuid = uuid,
      fullName = fullName,
      gender = gender,
      dateOfBirth = dateOfBirth,
      age = age,
      updatedAt = updatedAt
  )

  fun businessId(
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      identifier: Identifier = Identifier(UUID.randomUUID().toString(), Identifier.IdentifierType.random()),
      metaDataVersion: BusinessId.MetaDataVersion = BusinessId.MetaDataVersion.random(),
      metadata: String = "meta",
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ) = BusinessId(uuid = uuid,
      patientUuid = patientUuid,
      identifier = identifier,
      metaData = metadata,
      metaDataVersion = metaDataVersion,
      createdAt = createdAt,
      updatedAt = updatedAt,
      deletedAt = deletedAt)

  fun patientProfile(
      patientUuid: UUID = UUID.randomUUID(),
      addressUuid: UUID = UUID.randomUUID()
  ): PatientProfile {
    return PatientProfile(
        patient = patient(uuid = patientUuid, addressUuid = addressUuid),
        address = address(uuid = addressUuid),
        phoneNumbers = emptyList(),
        businessIds = emptyList()
    )
  }

  fun country(
      isoCountryCode: String = "IN",
      endpoint: String = "https://simple.org",
      displayName: String = "India",
      isdCode: String = "91"
  ): Country {
    return Country(
        isoCountryCode = isoCountryCode,
        endpoint = URI.create(endpoint),
        displayName = displayName,
        isdCode = isdCode
    )
  }

  fun bloodSugar(
      uuid: UUID = UUID.randomUUID(),
      reading: BloodSugarReading = BloodSugarReading(600, org.simple.clinic.bloodsugar.Random),
      patientUuid: UUID = UUID.randomUUID(),
      recordedAt: Instant = Instant.now(),
      userUuid: UUID = UUID.randomUUID(),
      facilityUuid: UUID = UUID.randomUUID(),
      timestamps: Timestamps = Timestamps(Instant.now(), Instant.now(), null),
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class)
  ): BloodSugarMeasurement {
    return BloodSugarMeasurement(
        uuid = uuid,
        reading = reading,
        patientUuid = patientUuid,
        recordedAt = recordedAt,
        userUuid = userUuid,
        facilityUuid = facilityUuid,
        timestamps = timestamps,
        syncStatus = syncStatus
    )
  }
}
