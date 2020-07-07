package org.simple.clinic

import io.bloco.faker.Faker
import org.simple.clinic.appconfig.Country
import org.simple.clinic.bloodsugar.BloodSugarMeasurement
import org.simple.clinic.bloodsugar.BloodSugarMeasurementType
import org.simple.clinic.bloodsugar.BloodSugarReading
import org.simple.clinic.bloodsugar.Random
import org.simple.clinic.bloodsugar.sync.BloodSugarMeasurementPayload
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.BloodPressureReading
import org.simple.clinic.bp.sync.BloodPressureMeasurementPayload
import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.drugs.sync.PrescribedDrugPayload
import org.simple.clinic.facility.Facility
import org.simple.clinic.facility.FacilityConfig
import org.simple.clinic.facility.FacilityPayload
import org.simple.clinic.home.overdue.OverdueAppointment
import org.simple.clinic.home.overdue.OverduePatientAddress
import org.simple.clinic.location.Coordinates
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.MedicalHistory
import org.simple.clinic.medicalhistory.sync.MedicalHistoryPayload
import org.simple.clinic.overdue.Appointment
import org.simple.clinic.overdue.AppointmentCancelReason
import org.simple.clinic.overdue.AppointmentPayload
import org.simple.clinic.patient.Age
import org.simple.clinic.patient.DeletedReason
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.OngoingNewPatientEntry
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.PatientAddress
import org.simple.clinic.patient.PatientPhoneNumber
import org.simple.clinic.patient.PatientPhoneNumberType
import org.simple.clinic.patient.PatientProfile
import org.simple.clinic.patient.PatientSearchResult
import org.simple.clinic.patient.PatientStatus
import org.simple.clinic.patient.RecentPatient
import org.simple.clinic.patient.ReminderConsent
import org.simple.clinic.patient.ReminderConsent.Granted
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.businessid.BusinessId
import org.simple.clinic.patient.businessid.BusinessId.MetaDataVersion
import org.simple.clinic.patient.businessid.BusinessId.MetaDataVersion.BpPassportMetaDataV1
import org.simple.clinic.patient.businessid.BusinessIdMetaData
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.patient.sync.BusinessIdPayload
import org.simple.clinic.patient.sync.PatientAddressPayload
import org.simple.clinic.patient.sync.PatientPayload
import org.simple.clinic.patient.sync.PatientPhoneNumberPayload
import org.simple.clinic.protocol.Protocol
import org.simple.clinic.protocol.ProtocolDrug
import org.simple.clinic.protocol.sync.ProtocolDrugPayload
import org.simple.clinic.protocol.sync.ProtocolPayload
import org.simple.clinic.storage.Timestamps
import org.simple.clinic.summary.teleconsultation.api.TeleconsultPhoneNumber
import org.simple.clinic.summary.teleconsultation.api.TeleconsultationsResponse
import org.simple.clinic.user.LoggedInUserPayload
import org.simple.clinic.user.OngoingLoginEntry
import org.simple.clinic.user.OngoingRegistrationEntry
import org.simple.clinic.user.User
import org.simple.clinic.user.UserStatus
import org.simple.clinic.util.randomDeletedReason
import org.simple.clinic.util.randomGender
import org.simple.clinic.util.randomMedicalHistoryAnswer
import org.simple.clinic.util.randomOfEnum
import org.simple.clinic.util.randomPatientPhoneNumberType
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset.UTC
import java.net.URI
import java.util.UUID
import kotlin.random.nextInt

object TestData {

  private val faker = Faker("en-IND")

  fun patientProfile(
      patientUuid: UUID = UUID.randomUUID(),
      patientAddressUuid: UUID = UUID.randomUUID(),
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class),
      generatePhoneNumber: Boolean = faker.bool.bool(),
      generateBusinessId: Boolean = faker.bool.bool(),
      patientStatus: PatientStatus = PatientStatus.Active,
      patientDeletedAt: Instant? = null,
      patientName: String = faker.name.name(),
      patientPhoneNumber: String? = if (generatePhoneNumber) faker.phoneNumber.phoneNumber() else null,
      businessId: BusinessId? = if (generateBusinessId) businessId(patientUuid = patientUuid) else null,
      dateOfBirth: LocalDate? = LocalDate.parse("1980-01-01"),
      age: Age? = Age(value = kotlin.random.Random.nextInt(30..100), updatedAt = Instant.parse("2018-01-01T00:00:00Z")),
      gender: Gender = randomGender(),
      patientDeletedReason: DeletedReason? = null,
      patientCreatedAt: Instant = Instant.now(),
      patientUpdatedAt: Instant = Instant.now(),
      patientRecordedAt: Instant = Instant.parse("2018-01-01T00:00:00Z")
  ): PatientProfile {
    val phoneNumbers = if (!patientPhoneNumber.isNullOrBlank()) {
      listOf(patientPhoneNumber(patientUuid = patientUuid, number = patientPhoneNumber))
    } else {
      emptyList()
    }
    val businessIds = if (businessId != null) {
      listOf(businessId)
    } else {
      emptyList()
    }

    return PatientProfile(
        patient = patient(
            uuid = patientUuid,
            fullName = patientName,
            syncStatus = syncStatus,
            addressUuid = patientAddressUuid,
            status = patientStatus,
            deletedAt = patientDeletedAt,
            age = age,
            dateOfBirth = dateOfBirth,
            gender = gender,
            deletedReason = patientDeletedReason,
            createdAt = patientCreatedAt,
            updatedAt = patientUpdatedAt,
            recordedAt = patientRecordedAt
        ),
        address = patientAddress(uuid = patientAddressUuid),
        phoneNumbers = phoneNumbers,
        businessIds = businessIds)
  }

  fun patient(
      uuid: UUID = UUID.randomUUID(),
      addressUuid: UUID = UUID.randomUUID(),
      fullName: String = faker.name.name(),
      gender: Gender = randomGender(),
      dateOfBirth: LocalDate? = LocalDate.parse("1980-01-01"),
      age: Age? = Age(value = Math.random().times(100).toInt(), updatedAt = Instant.now()),
      status: PatientStatus = PatientStatus.random(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      recordedAt: Instant = Instant.now(),
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class),
      reminderConsent: ReminderConsent = Granted,
      deletedReason: DeletedReason? = null
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
        reminderConsent = reminderConsent,
        deletedReason = deletedReason
    )
  }

  fun patientPhoneNumber(
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      number: String = faker.phoneNumber.phoneNumber(),
      phoneType: PatientPhoneNumberType = randomPatientPhoneNumberType(),
      active: Boolean = faker.bool.bool(),
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

  fun patientAddress(
      uuid: UUID = UUID.randomUUID(),
      streetAddress: String? = faker.address.streetName(),
      colonyOrVillage: String? = faker.address.streetAddress(),
      district: String = faker.address.city(),
      zone: String? = faker.address.secondaryAddress(),
      state: String = faker.address.state(),
      country: String? = faker.address.country(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ) = PatientAddress(
      uuid = uuid,
      streetAddress = streetAddress,
      colonyOrVillage = colonyOrVillage,
      zone = zone,
      district = district,
      state = state,
      country = country,
      createdAt = createdAt,
      updatedAt = updatedAt,
      deletedAt = deletedAt
  )

  fun businessId(
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      identifier: Identifier = Identifier(value = UUID.randomUUID().toString(), type = Identifier.IdentifierType.BpPassport),
      meta: String = "",
      metaDataVersion: MetaDataVersion = BpPassportMetaDataV1,
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ) = BusinessId(
      uuid = uuid,
      patientUuid = patientUuid,
      identifier = identifier,
      metaData = meta,
      metaDataVersion = metaDataVersion,
      createdAt = createdAt,
      updatedAt = updatedAt,
      deletedAt = deletedAt
  )

  fun patientPayload(
      uuid: UUID = UUID.randomUUID(),
      fullName: String = faker.name.name(),
      gender: Gender = randomGender(),
      age: Int? = Math.random().times(100).toInt(),
      dateOfBirth: LocalDate? = null,
      ageUpdatedAt: Instant? = Instant.now(),
      status: PatientStatus = PatientStatus.random(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      recordedAt: Instant = Instant.now(),
      address: PatientAddressPayload = addressPayload(),
      phoneNumbers: List<PatientPhoneNumberPayload>? = listOf(phoneNumberPayload()),
      businessIds: List<BusinessIdPayload> = listOf(businessIdPayload()),
      deletedReason: DeletedReason? = randomDeletedReason()
  ): PatientPayload {
    return PatientPayload(
        uuid = uuid,
        fullName = fullName,
        gender = gender,
        dateOfBirth = dateOfBirth,
        age = age,
        ageUpdatedAt = ageUpdatedAt,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        address = address,
        phoneNumbers = phoneNumbers,
        businessIds = businessIds,
        recordedAt = recordedAt,
        reminderConsent = Granted,
        deletedReason = deletedReason
    )
  }

  fun addressPayload(
      uuid: UUID = UUID.randomUUID(),
      streetAddress: String? = faker.address.streetName(),
      colonyOrVillage: String? = faker.address.streetAddress(),
      district: String = faker.address.city(),
      zone: String? = faker.address.secondaryAddress(),
      state: String = faker.address.state(),
      country: String? = faker.address.country(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ): PatientAddressPayload {
    return PatientAddressPayload(
        uuid = uuid,
        colonyOrVillage = colonyOrVillage,
        streetAddress = streetAddress,
        district = district,
        zone = zone,
        state = state,
        country = country,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )
  }

  fun phoneNumberPayload(
      uuid: UUID = UUID.randomUUID(),
      number: String = faker.phoneNumber.phoneNumber(),
      type: PatientPhoneNumberType = randomPatientPhoneNumberType(),
      active: Boolean = true,
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ): PatientPhoneNumberPayload {
    return PatientPhoneNumberPayload(
        uuid = uuid,
        number = number,
        type = type,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt)
  }

  fun businessIdPayload(
      uuid: UUID = UUID.randomUUID(),
      identifier: String = UUID.randomUUID().toString(),
      identifierType: Identifier.IdentifierType = Identifier.IdentifierType.BpPassport,
      metaDataVersion: MetaDataVersion = BpPassportMetaDataV1,
      meta: String = run {
        BusinessIdMetaData.BpPassportMetaDataV1(
            assigningUserUuid = UUID.fromString("4e3442df-ffa4-4a66-9d5f-672d3135c460"),
            assigningFacilityUuid = UUID.fromString("faec54dc-1c5d-4768-83c5-80e7f272f8fe")
        )
        ""
      },
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ): BusinessIdPayload {
    return BusinessIdPayload(
        uuid = uuid,
        identifier = identifier,
        identifierType = identifierType,
        metaDataVersion = metaDataVersion,
        metaData = meta,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )
  }

  /**
   * [uuid] is not optional because dummy facility IDs should never be sent to
   * the server. Doing so may result in data loss due to foreign key constraints.
   */
  fun facility(
      uuid: UUID = UUID.randomUUID(),
      name: String = faker.company.name(),
      district: String = faker.address.city(),
      state: String = faker.address.state(),
      facilityType: String? = null,
      streetAddress: String? = null,
      villageOrColony: String? = null,
      country: String = faker.address.country(),
      pinCode: String? = null,
      protocolUuid: UUID? = UUID.randomUUID(),
      groupUuid: UUID? = UUID.randomUUID(),
      location: Coordinates? = Coordinates(
          latitude = faker.number.between(1.908537, 59.299800),
          longitude = faker.number.between(73.537524, 18.209118)),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class),
      deletedAt: Instant? = null,
      facilityConfig: FacilityConfig = FacilityConfig(diabetesManagementEnabled = false, teleconsultationEnabled = false)
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
      uuid: UUID = UUID.randomUUID(),
      name: String = faker.company.name(),
      district: String = faker.address.city(),
      state: String = faker.address.state(),
      protocolUuid: UUID = UUID.randomUUID(),
      groupUuid: UUID = UUID.randomUUID(),
      facilityType: String? = null,
      streetAddress: String? = null,
      villageOrColony: String? = null,
      country: String = faker.address.country(),
      pinCode: String? = null,
      locationLatitude: Double? = faker.number.between(1.908537, 59.299800),
      locationLongitude: Double? = faker.number.between(73.537524, 18.209118),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      facilityConfig: FacilityConfig = FacilityConfig(diabetesManagementEnabled = false, teleconsultationEnabled = false)
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

  fun loggedInUser(
      uuid: UUID = UUID.randomUUID(),
      name: String = faker.name.name(),
      phone: String = faker.phoneNumber.phoneNumber(),
      pinDigest: String = "pin-digest",
      status: UserStatus = UserStatus.random(),
      loggedInStatus: User.LoggedInStatus = randomOfEnum(User.LoggedInStatus::class),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      registrationFacilityUuid: UUID = UUID.fromString("fa85410a-54ca-449c-b3d6-7caf9def1474"),
      currentFacilityUuid: UUID = registrationFacilityUuid
  ): User {
    return User(
        uuid = uuid,
        fullName = name,
        phoneNumber = phone,
        pinDigest = pinDigest,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        loggedInStatus = loggedInStatus,
        registrationFacilityUuid = registrationFacilityUuid,
        currentFacilityUuid = currentFacilityUuid
    )
  }

  fun ongoingRegistrationEntry(
      uuid: UUID = UUID.randomUUID(),
      phoneNumber: String = faker.number.number(10),
      pin: String = "1111",
      registrationFacility: Facility
  ): OngoingRegistrationEntry {
    return OngoingRegistrationEntry(
        uuid = uuid,
        phoneNumber = phoneNumber,
        fullName = faker.name.name(),
        pin = pin,
        pinConfirmation = pin,
        facilityId = registrationFacility.uuid)
  }

  fun bpPayload(
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      facilityUuid: UUID = UUID.fromString("faec54dc-1c5d-4768-83c5-80e7f272f8fe"),
      systolic: Int = faker.number.between(0, 299),
      diastolic: Int = faker.number.between(50, 60),
      userUuid: UUID = UUID.fromString("4e3442df-ffa4-4a66-9d5f-672d3135c460"),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      recordedAt: Instant = Instant.now()
  ): BloodPressureMeasurementPayload {
    return BloodPressureMeasurementPayload(
        uuid = uuid,
        patientUuid = patientUuid,
        systolic = systolic,
        diastolic = diastolic,
        facilityUuid = facilityUuid,
        userUuid = userUuid,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        recordedAt = recordedAt)
  }

  fun bloodSugarPayload(
      uuid: UUID = UUID.randomUUID(),
      bloodSugarType: BloodSugarMeasurementType = BloodSugarMeasurementType.random(),
      bloodSugarValue: Float = faker.number.between(0, 300).toFloat(),
      patientUuid: UUID = UUID.randomUUID(),
      facilityUuid: UUID = UUID.fromString("faec54dc-1c5d-4768-83c5-80e7f272f8fe"),
      userUuid: UUID = UUID.fromString("4e3442df-ffa4-4a66-9d5f-672d3135c460"),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      recordedAt: Instant = Instant.now()
  ) = BloodSugarMeasurementPayload(
      uuid = uuid,
      bloodSugarType = bloodSugarType,
      bloodSugarValue = bloodSugarValue,
      patientUuid = patientUuid,
      facilityUuid = facilityUuid,
      userUuid = userUuid,
      createdAt = createdAt,
      updatedAt = updatedAt,
      deletedAt = deletedAt,
      recordedAt = recordedAt
  )

  fun prescription(
      uuid: UUID = UUID.randomUUID(),
      name: String = faker.food.dish(),
      dosage: String? = "${faker.number.positive(10, 50)}mg",
      rxNormCode: String = "rx-norm-code",
      isDeleted: Boolean = false,
      isProtocolDrug: Boolean = false,
      patientUuid: UUID = UUID.randomUUID(),
      facilityUuid: UUID = UUID.randomUUID(),
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ): PrescribedDrug {
    return PrescribedDrug(
        uuid = uuid,
        name = name,
        dosage = dosage,
        rxNormCode = rxNormCode,
        isDeleted = isDeleted,
        isProtocolDrug = isProtocolDrug,
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt)
  }

  fun prescriptionPayload(
      uuid: UUID = UUID.randomUUID(),
      name: String = faker.food.dish(),
      dosage: String = "${faker.number.positive(10, 50)}mg",
      rxNormCode: String = faker.food.metricMeasurement(),
      isDeleted: Boolean = false,
      isProtocolDrug: Boolean = false,
      patientUuid: UUID = UUID.randomUUID(),
      facilityUuid: UUID = UUID.randomUUID(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ): PrescribedDrugPayload {
    return PrescribedDrugPayload(
        uuid = uuid,
        name = name,
        dosage = dosage,
        rxNormCode = rxNormCode,
        isDeleted = isDeleted,
        isProtocolDrug = isProtocolDrug,
        patientId = patientUuid,
        facilityId = facilityUuid,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt)
  }

  fun appointment(
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class),
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      facilityUuid: UUID = UUID.fromString("faec54dc-1c5d-4768-83c5-80e7f272f8fe"),
      scheduledDate: LocalDate = LocalDate.now(UTC).plusDays(30),
      status: Appointment.Status = Appointment.Status.random(),
      cancelReason: AppointmentCancelReason? = AppointmentCancelReason.random(),
      remindOn: LocalDate? = null,
      agreedToVisit: Boolean? = null,
      appointmentType: Appointment.AppointmentType = Appointment.AppointmentType.random(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      creationFacilityUuid: UUID? = UUID.fromString("faec54dc-1c5d-4768-83c5-80e7f272f8fe")
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

  fun appointmentPayload(
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      date: LocalDate = LocalDate.now(UTC).plusDays(30),
      facilityUuid: UUID = UUID.fromString("faec54dc-1c5d-4768-83c5-80e7f272f8fe"),
      creationFacilityUuid: UUID? = UUID.fromString("faec54dc-1c5d-4768-83c5-80e7f272f8fe"),
      status: Appointment.Status = Appointment.Status.random(),
      cancelReason: AppointmentCancelReason = AppointmentCancelReason.random(),
      remindOn: LocalDate? = null,
      agreedToVisit: Boolean? = null,
      appointmentType: Appointment.AppointmentType = Appointment.AppointmentType.random(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ): AppointmentPayload {
    return AppointmentPayload(
        uuid = uuid,
        patientUuid = patientUuid,
        facilityUuid = facilityUuid,
        creationFacilityUuid = creationFacilityUuid,
        date = date,
        status = status,
        cancelReason = cancelReason,
        remindOn = remindOn,
        agreedToVisit = agreedToVisit,
        appointmentType = appointmentType,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt)
  }

  fun medicalHistory(
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      hasHadHeartAttack: Answer = randomMedicalHistoryAnswer(),
      hasHadStroke: Answer = randomMedicalHistoryAnswer(),
      hasHadKidneyDisease: Answer = randomMedicalHistoryAnswer(),
      diagnosedWithHypertension: Answer = randomMedicalHistoryAnswer(),
      hasDiabetes: Answer = randomMedicalHistoryAnswer(),
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ): MedicalHistory {
    return MedicalHistory(
        uuid = uuid,
        patientUuid = patientUuid,
        diagnosedWithHypertension = diagnosedWithHypertension,
        hasHadHeartAttack = hasHadHeartAttack,
        hasHadStroke = hasHadStroke,
        hasHadKidneyDisease = hasHadKidneyDisease,
        diagnosedWithDiabetes = hasDiabetes,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt)
  }

  fun medicalHistoryPayload(
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      hasHypertension: Answer = randomMedicalHistoryAnswer(),
      diagnosedWithHypertension: Answer = hasHypertension,
      hasHadHeartAttack: Answer = randomMedicalHistoryAnswer(),
      hasHadStroke: Answer = randomMedicalHistoryAnswer(),
      hasHadKidneyDisease: Answer = randomMedicalHistoryAnswer(),
      isOnTreatmentForHypertension: Answer = randomMedicalHistoryAnswer(),
      hasDiabetes: Answer = randomMedicalHistoryAnswer(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null
  ): MedicalHistoryPayload {
    return MedicalHistoryPayload(
        uuid = uuid,
        patientUuid = patientUuid,
        diagnosedWithHypertension = diagnosedWithHypertension,
        isOnTreatmentForHypertension = isOnTreatmentForHypertension,
        hasHadHeartAttack = hasHadHeartAttack,
        hasHadStroke = hasHadStroke,
        hasHadKidneyDisease = hasHadKidneyDisease,
        hasDiabetes = hasDiabetes,
        hasHypertension = diagnosedWithHypertension,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt)
  }

  fun ongoingPatientEntry(
      fullName: String = faker.name.name(),
      dateOfBirth: String? = null,
      age: String? = faker.number.between(MIN_ALLOWED_PATIENT_AGE, MAX_ALLOWED_PATIENT_AGE).toString(),
      gender: Gender = randomGender(),
      colony: String = faker.address.streetName(),
      district: String = faker.address.city(),
      state: String = faker.address.state(),
      streetAddress: String? = faker.address.streetAddress(),
      zone: String? = "Zone-" + faker.address.city(),
      phone: String? = faker.number.number(10),
      identifier: Identifier? = null,
      bangladeshNationalId: Identifier? = null
  ): OngoingNewPatientEntry {
    val ongoingPersonalDetails = OngoingNewPatientEntry.PersonalDetails(fullName, dateOfBirth, age, gender)
    val ongoingAddress = OngoingNewPatientEntry.Address(colony, district, state, streetAddress, zone)
    val ongoingPhoneNumber = phone?.let {
      OngoingNewPatientEntry.PhoneNumber(phone, PatientPhoneNumberType.Mobile, active = true)
    }

    return OngoingNewPatientEntry(
        personalDetails = ongoingPersonalDetails,
        address = ongoingAddress,
        phoneNumber = ongoingPhoneNumber,
        identifier = identifier,
        alternativeId = bangladeshNationalId
    )
  }

  fun bloodPressureMeasurement(
      uuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      facilityUuid: UUID = UUID.fromString("faec54dc-1c5d-4768-83c5-80e7f272f8fe"),
      userUuid: UUID = UUID.fromString("4e3442df-ffa4-4a66-9d5f-672d3135c460"),
      systolic: Int = faker.number.between(0, 299),
      diastolic: Int = faker.number.between(50, 60),
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      recordedAt: Instant = Instant.now()
  ): BloodPressureMeasurement {
    return BloodPressureMeasurement(
        uuid = uuid,
        reading = BloodPressureReading(systolic, diastolic),
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

  fun protocol(
      uuid: UUID = UUID.randomUUID(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      name: String = "Protocol-Punjab",
      followUpDays: Int = 0,
      deletedAt: Instant? = null,
      syncStatus: SyncStatus = randomOfEnum(SyncStatus::class)
  ): Protocol {
    return Protocol(
        uuid = uuid,
        name = name,
        followUpDays = followUpDays,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncStatus = syncStatus
    )
  }

  fun protocolDrug(
      uuid: UUID = UUID.randomUUID(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      protocolUuid: UUID = UUID.randomUUID(),
      rxNormCode: String = "rx-NormCode-1",
      dosage: String = "20mg",
      name: String = "Amlodipine",
      deletedAt: Instant? = null,
      order: Int = 0
  ): ProtocolDrug {
    return ProtocolDrug(
        uuid = uuid,
        rxNormCode = rxNormCode,
        dosage = dosage,
        name = name,
        protocolUuid = protocolUuid,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        order = order
    )
  }

  fun protocolPayload(
      uuid: UUID = UUID.randomUUID(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      name: String = "Punjab Protocol",
      followUpDays: Int = 0,
      protocolDrugs: List<ProtocolDrugPayload> = listOf(protocolDrugPayload(protocolUuid = uuid))
  ) = ProtocolPayload(
      uuid = uuid,
      createdAt = createdAt,
      updatedAt = updatedAt,
      name = name,
      followUpDays = followUpDays,
      protocolDrugs = protocolDrugs,
      deletedAt = deletedAt)

  fun protocolDrugPayload(
      uuid: UUID = UUID.randomUUID(),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      protocolUuid: UUID = UUID.randomUUID(),
      rxNormCode: String = "rx-normcode",
      dosage: String = "5mg",
      name: String = "Amlodipine"
  ) = ProtocolDrugPayload(
      uuid = uuid,
      createdAt = createdAt,
      updatedAt = updatedAt,
      protocolUuid = protocolUuid,
      rxNormCode = rxNormCode,
      dosage = dosage,
      name = name,
      deletedAt = deletedAt)

  fun identifier(
      value: String = "identifier",
      type: Identifier.IdentifierType = Identifier.IdentifierType.random()
  ): Identifier {
    return Identifier(
        value = value,
        type = type
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

  fun bloodSugarMeasurement(
      uuid: UUID = UUID.randomUUID(),
      reading: BloodSugarReading = BloodSugarReading(faker.number.between(30, 1000).toString(), Random),
      patientUuid: UUID = UUID.randomUUID(),
      recordedAt: Instant = Instant.now(),
      userUuid: UUID = UUID.fromString("4e3442df-ffa4-4a66-9d5f-672d3135c460"),
      facilityUuid: UUID = UUID.fromString("faec54dc-1c5d-4768-83c5-80e7f272f8fe"),
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      deletedAt: Instant? = null,
      timestamps: Timestamps = Timestamps(createdAt, updatedAt, deletedAt),
      syncStatus: SyncStatus = SyncStatus.DONE
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

  fun overduePatientAddress(
      streetAddress: String? = faker.address.streetName(),
      colonyOrVillage: String? = faker.address.streetAddress(),
      district: String = faker.address.city(),
      state: String = faker.address.state()
  ) = OverduePatientAddress(
      streetAddress = streetAddress,
      colonyOrVillage = colonyOrVillage,
      district = district,
      state = state
  )

  fun overdueAppointment(
      facilityUuid: UUID = UUID.randomUUID(),
      patientUuid: UUID = UUID.randomUUID(),
      phoneNumberUuid: UUID = UUID.randomUUID(),
      appointmentUuid: UUID = UUID.randomUUID(),
      name: String = "somebody",
      isHighRisk: Boolean = false,
      gender: Gender = Gender.Transgender,
      dateOfBirth: LocalDate? = LocalDate.now(UTC).minusYears(30),
      age: Age? = null,
      phoneNumber: PatientPhoneNumber? = patientPhoneNumber(uuid = phoneNumberUuid, patientUuid = patientUuid),
      appointment: Appointment = appointment(uuid = appointmentUuid, patientUuid = patientUuid, facilityUuid = facilityUuid),
      patientLastSeen: Instant = Instant.parse("2018-01-01T00:00:00Z"),
      diagnosedWithDiabetes: Answer? = null,
      diagnosedWithHypertension: Answer? = null,
      patientAddress: OverduePatientAddress = overduePatientAddress()
  ): OverdueAppointment {
    return OverdueAppointment(
        fullName = name,
        gender = gender,
        dateOfBirth = dateOfBirth,
        age = age,
        appointment = appointment,
        phoneNumber = phoneNumber,
        patientAddress = patientAddress,
        isAtHighRisk = isHighRisk,
        patientLastSeen = patientLastSeen,
        diagnosedWithDiabetes = diagnosedWithDiabetes,
        diagnosedWithHypertension = diagnosedWithHypertension
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
      phoneNumber: String = "3.14159",
      gender: Gender = Gender.Male,
      dateOfBirth: LocalDate? = null,
      age: Age? = Age(45, Instant.now()),
      status: PatientStatus = PatientStatus.Active,
      createdAt: Instant = Instant.now(),
      updatedAt: Instant = Instant.now(),
      address: PatientAddress = patientAddress(),
      syncStatus: SyncStatus = SyncStatus.DONE,
      phoneType: PatientPhoneNumberType = PatientPhoneNumberType.Mobile,
      phoneNumberUuid: UUID = UUID.randomUUID(),
      phoneActive: Boolean = true,
      phoneCreatedAt: Instant = Instant.now(),
      phoneUpdatedAt: Instant = Instant.now(),
      lastSeen: PatientSearchResult.LastSeen = PatientSearchResult.LastSeen(
          lastSeenOn = Instant.now(),
          lastSeenAtFacilityName = "Some Facility",
          lastSeenAtFacilityUuid = UUID.randomUUID()
      )
  ): PatientSearchResult {
    return PatientSearchResult(
        uuid = uuid,
        fullName = fullName,
        gender = gender,
        dateOfBirth = dateOfBirth,
        age = age,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        address = address,
        syncStatus = syncStatus,
        phoneNumber = phoneNumber,
        phoneType = phoneType,
        phoneUuid = phoneNumberUuid,
        phoneActive = phoneActive,
        phoneCreatedAt = phoneCreatedAt,
        phoneUpdatedAt = phoneUpdatedAt,
        lastSeen = lastSeen
    )
  }

  fun recentPatient(
      uuid: UUID = UUID.randomUUID(),
      fullName: String = "fullName",
      gender: Gender = randomGender(),
      dateOfBirth: LocalDate? = null,
      age: Age? = null,
      patientRecordedAt: Instant = Instant.parse("2018-01-01T00:00:00Z"),
      updatedAt: Instant = Instant.parse("2018-01-01T00:00:00Z")
  ) = RecentPatient(
      uuid = uuid,
      fullName = fullName,
      gender = gender,
      dateOfBirth = dateOfBirth,
      age = age,
      patientRecordedAt = patientRecordedAt,
      updatedAt = updatedAt
  )

  fun ongoingLoginEntry(
      uuid: UUID = UUID.fromString("e4d91a34-c475-4038-a066-a866f9ecafec"),
      phoneNumber: String? = "1111111111",
      pin: String? = "1234",
      fullName: String? = "Anish Acharya",
      pinDigest: String? = "digest",
      registrationFacilityUuid: UUID? = UUID.fromString("d91fb2ba-9c87-4de0-b425-eea44457c746"),
      status: UserStatus? = UserStatus.ApprovedForSyncing,
      createdAt: Instant? = Instant.parse("2018-01-01T00:00:00Z"),
      updatedAt: Instant? = Instant.parse("2018-01-01T00:00:00Z")
  ) = OngoingLoginEntry(
      uuid = uuid,
      phoneNumber = phoneNumber,
      pin = pin,
      fullName = fullName,
      pinDigest = pinDigest,
      registrationFacilityUuid = registrationFacilityUuid,
      status = status,
      createdAt = createdAt,
      updatedAt = updatedAt
  )

  fun facilityTeleconsultationsResponse(
      phoneNumber: String? = "+911111111111",
      phoneNumbers: List<TeleconsultPhoneNumber> = listOf(
          teleconsultPhoneNumber(
              phoneNumber = "+911111111111"
          ),
          teleconsultPhoneNumber(
              phoneNumber = "+912222222222"
          ))
  ) = TeleconsultationsResponse(
      teleconsultationPhoneNumber = phoneNumber,
      teleconsultationPhoneNumbers = phoneNumbers
  )


  fun teleconsultPhoneNumber(
      phoneNumber: String = "+911111111111"
  ) = TeleconsultPhoneNumber(
      phoneNumber = phoneNumber
  )
}
