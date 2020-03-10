package org.simple.clinic.overdue

import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.bloco.faker.Faker
import io.reactivex.Completable
import io.reactivex.Single
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.bloodsugar.BloodSugarMeasurement
import org.simple.clinic.bloodsugar.BloodSugarReading
import org.simple.clinic.bloodsugar.BloodSugarRepository
import org.simple.clinic.bloodsugar.Random
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.facility.Facility
import org.simple.clinic.home.overdue.OverdueAppointment
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.Answer.No
import org.simple.clinic.medicalhistory.Answer.Yes
import org.simple.clinic.medicalhistory.MedicalHistoryRepository
import org.simple.clinic.medicalhistory.OngoingMedicalHistoryEntry
import org.simple.clinic.overdue.Appointment.AppointmentType.Automatic
import org.simple.clinic.overdue.Appointment.AppointmentType.Manual
import org.simple.clinic.overdue.Appointment.Status.Cancelled
import org.simple.clinic.overdue.Appointment.Status.Scheduled
import org.simple.clinic.overdue.Appointment.Status.Visited
import org.simple.clinic.overdue.AppointmentCancelReason.PatientNotResponding
import org.simple.clinic.patient.PatientPhoneNumber
import org.simple.clinic.patient.PatientProfile
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.SyncStatus.DONE
import org.simple.clinic.patient.SyncStatus.PENDING
import org.simple.clinic.rules.LocalAuthenticationRule
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.TestUserClock
import org.simple.clinic.util.TestUtcClock
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class AppointmentRepositoryAndroidTest {

  @Inject
  lateinit var appointmentRepository: AppointmentRepository

  @Inject
  lateinit var patientRepository: PatientRepository

  @Inject
  lateinit var bpRepository: BloodPressureRepository

  @Inject
  lateinit var bloodSugarRepository: BloodSugarRepository

  @Inject
  lateinit var medicalHistoryRepository: MedicalHistoryRepository

  @Inject
  lateinit var database: AppDatabase

  @Inject
  lateinit var userSession: UserSession

  @Inject
  lateinit var testData: TestData

  @Inject
  lateinit var faker: Faker

  @Inject
  lateinit var clock: TestUtcClock

  private val facility: Facility by lazy { testData.qaFacility() }

  private val userUuid: UUID by lazy { testData.qaUserUuid() }

  @get:Rule
  val ruleChain = RuleChain
      .outerRule(LocalAuthenticationRule())
      .around(RxErrorsRule())!!

  private val patientUuid = UUID.fromString("fcf0acd3-0b09-4ecb-bcd4-af40ca6456fc")
  private val appointmentUuid = UUID.fromString("a374e38f-6bc3-4829-899c-0966a4e13b10")

  @Before
  fun setup() {
    TestClinicApp.appComponent().inject(this)
    clock.setDate(LocalDate.parse("2018-01-01"))
  }

  @After
  fun tearDown() {
    database.clearAllTables()
  }

  @Test
  fun when_creating_new_appointment_then_the_appointment_should_be_saved() {
    // given
    val appointmentDate = LocalDate.now(clock)
    val creationFacility = testData.facility(uuid = UUID.fromString("4e32c8c8-cfa0-4665-a52e-7398e47aa8b9"))

    //when
    appointmentRepository.schedule(
        patientUuid = patientUuid,
        appointmentUuid = appointmentUuid,
        appointmentDate = appointmentDate,
        appointmentType = Manual,
        appointmentFacilityUuid = facility.uuid,
        creationFacilityUuid = creationFacility.uuid
    ).blockingGet()

    // then
    val savedAppointment = getAppointmentByUuid(appointmentUuid)
    with(savedAppointment) {
      assertThat(patientUuid).isEqualTo(this@AppointmentRepositoryAndroidTest.patientUuid)
      assertThat(scheduledDate).isEqualTo(appointmentDate)
      assertThat(status).isEqualTo(Scheduled)
      assertThat(remindOn).isNull()
      assertThat(cancelReason).isNull()
      assertThat(agreedToVisit).isNull()
      assertThat(syncStatus).isEqualTo(PENDING)
      assertThat(creationFacilityUuid).isEqualTo(creationFacility.uuid)
    }
  }

  @Test
  fun when_creating_new_appointment_then_all_old_appointments_for_that_patient_should_be_marked_as_visited() {
    // given
    val firstAppointmentUuid = UUID.fromString("0bc9cdb3-bfe9-41e9-88b9-2a072c748c47")
    val scheduledDateOfFirstAppointment = LocalDate.parse("2018-01-01")
    val firstAppointmentScheduledAtTimestamp = Instant.now(clock)
    appointmentRepository.schedule(
        patientUuid = patientUuid,
        appointmentUuid = firstAppointmentUuid,
        appointmentDate = scheduledDateOfFirstAppointment,
        appointmentType = Manual,
        appointmentFacilityUuid = facility.uuid,
        creationFacilityUuid = facility.uuid
    ).blockingGet()
    markAppointmentSyncStatusAsDone(firstAppointmentUuid)

    clock.advanceBy(Duration.ofHours(24))

    val secondAppointmentUuid = UUID.fromString("ed31c3ae-8903-45fe-9ad3-0302dcba7fc6")
    val scheduleDateOfSecondAppointment = LocalDate.parse("2018-02-01")
    val secondAppointmentScheduledAtTimestamp = Instant.now(clock)

    // when
    appointmentRepository.schedule(
        patientUuid = patientUuid,
        appointmentUuid = secondAppointmentUuid,
        appointmentDate = scheduleDateOfSecondAppointment,
        appointmentType = Manual,
        appointmentFacilityUuid = facility.uuid,
        creationFacilityUuid = facility.uuid
    ).blockingGet()

    // then
    val firstAppointment = getAppointmentByUuid(firstAppointmentUuid)
    with(firstAppointment) {
      assertThat(patientUuid).isEqualTo(this@AppointmentRepositoryAndroidTest.patientUuid)
      assertThat(scheduledDate).isEqualTo(scheduledDateOfFirstAppointment)
      assertThat(status).isEqualTo(Visited)
      assertThat(cancelReason).isEqualTo(null)
      assertThat(syncStatus).isEqualTo(PENDING)
      assertThat(createdAt).isEqualTo(firstAppointmentScheduledAtTimestamp)
      assertThat(createdAt).isLessThan(secondAppointmentScheduledAtTimestamp)
      assertThat(updatedAt).isEqualTo(secondAppointmentScheduledAtTimestamp)
    }

    val secondAppointment = getAppointmentByUuid(secondAppointmentUuid)
    with(secondAppointment) {
      assertThat(patientUuid).isEqualTo(this@AppointmentRepositoryAndroidTest.patientUuid)
      assertThat(scheduledDate).isEqualTo(scheduleDateOfSecondAppointment)
      assertThat(status).isEqualTo(Scheduled)
      assertThat(cancelReason).isEqualTo(null)
      assertThat(syncStatus).isEqualTo(PENDING)
      assertThat(createdAt).isEqualTo(secondAppointmentScheduledAtTimestamp)
      assertThat(updatedAt).isEqualTo(secondAppointmentScheduledAtTimestamp)
    }
  }

  @Test
  fun deleted_blood_pressure_measurements_should_not_be_considered_when_fetching_overdue_appointments() {
    fun createBloodPressure(
        bpUuid: UUID,
        patientUuid: UUID,
        recordedAt: Instant,
        deletedAt: Instant? = null
    ): BloodPressureMeasurement {
      return testData.bloodPressureMeasurement(
          uuid = bpUuid,
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          userUuid = userUuid,
          syncStatus = DONE,
          createdAt = Instant.parse("2018-01-01T00:00:00Z"),
          updatedAt = Instant.parse("2018-01-01T00:00:00Z"),
          recordedAt = recordedAt,
          deletedAt = deletedAt
      )
    }

    fun createAppointment(patientUuid: UUID, scheduledDate: LocalDate): Appointment {
      return testData.appointment(
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          status = Scheduled,
          scheduledDate = scheduledDate)
    }

    // given
    val noBpsDeletedPatientUuid = UUID.fromString("d05b8ed2-97ae-4fda-8af9-bc4168af3c4d")
    val latestBpDeletedPatientUuid = UUID.fromString("9e5ec219-f4a5-4bab-9283-0a087c5d7ac2")
    val oldestBpNotDeletedPatientUuid = UUID.fromString("54e7143c-fe64-4cd8-8c92-f379a79a60f9")
    val allBpsDeletedPatientUuid = UUID.fromString("05bd9d55-5742-466f-b97e-07301e25fe7e")

    val patients = listOf(
        testData.patientProfile(
            patientUuid = noBpsDeletedPatientUuid,
            generatePhoneNumber = true,
            patientName = "No BPs are deleted"
        ),
        testData.patientProfile(
            patientUuid = latestBpDeletedPatientUuid,
            generatePhoneNumber = true,
            patientName = "Latest BP is deleted"
        ),
        testData.patientProfile(
            patientUuid = oldestBpNotDeletedPatientUuid,
            generatePhoneNumber = true,
            patientName = "Oldest BP is not deleted"
        ),
        testData.patientProfile(
            patientUuid = allBpsDeletedPatientUuid,
            generatePhoneNumber = true,
            patientName = "All BPs are deleted"
        )
    )

    patientRepository.save(patients).blockingAwait()

    val bpsForPatientWithNoBpsDeleted = listOf(
        createBloodPressure(
            bpUuid = UUID.fromString("189b0842-044e-4f1c-a214-24318052f11d"),
            patientUuid = noBpsDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-01T00:00:00Z")
        ),
        createBloodPressure(
            bpUuid = UUID.fromString("ce5deb11-05ee-4f9e-8734-ec3d99f271a9"),
            patientUuid = noBpsDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-02T00:00:00Z")
        )
    )

    val bpsForPatientWithLatestBpDeleted = listOf(
        createBloodPressure(
            bpUuid = UUID.fromString("55266e25-0c15-4cd3-969d-3c5d5af48c62"),
            patientUuid = latestBpDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-03T00:00:00Z")
        ),
        createBloodPressure(
            bpUuid = UUID.fromString("e4c3461e-8624-4b6e-874b-bb73967e423e"),
            patientUuid = latestBpDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-04T00:00:00Z")
        ),
        createBloodPressure(
            bpUuid = UUID.fromString("e7d19558-36d8-4b5a-a17a-6e3117622b57"),
            patientUuid = latestBpDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-05T00:00:00Z"),
            deletedAt = Instant.parse("2018-01-05T00:00:00Z")
        )
    )

    val bpsForPatientWithOldestBpNotDeleted = listOf(
        createBloodPressure(
            bpUuid = UUID.fromString("1de759ae-9f60-4be5-a1f1-d18143bf8318"),
            patientUuid = oldestBpNotDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-06T00:00:00Z")
        ),
        createBloodPressure(
            bpUuid = UUID.fromString("f135aaa8-e4d6-48c0-acbf-ed0938c44f34"),
            patientUuid = oldestBpNotDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-07T00:00:00Z"),
            deletedAt = Instant.parse("2018-01-07T00:00:00Z")
        ),
        createBloodPressure(
            bpUuid = UUID.fromString("44cff8a9-08c2-4a48-9f4b-5c1ec7d9c10c"),
            patientUuid = oldestBpNotDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-08T00:00:00Z"),
            deletedAt = Instant.parse("2018-01-08T00:00:00Z")
        )
    )

    val bpsForPatientWithAllBpsDeleted = listOf(
        createBloodPressure(
            bpUuid = UUID.fromString("264c4295-c61b-41df-8548-460977510574"),
            patientUuid = allBpsDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-09T00:00:00Z"),
            deletedAt = Instant.parse("2018-01-09T00:00:00Z")
        ),
        createBloodPressure(
            bpUuid = UUID.fromString("ff2a665e-d09a-4110-9791-8e966690370f"),
            patientUuid = allBpsDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-10T00:00:00Z"),
            deletedAt = Instant.parse("2018-01-10T00:00:00Z")
        ),
        createBloodPressure(
            bpUuid = UUID.fromString("4e97bd7e-87ea-4d4c-a826-3784703937ed"),
            patientUuid = allBpsDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-11T00:00:00Z"),
            deletedAt = Instant.parse("2018-01-11T00:00:00Z")
        )
    )

    bpRepository
        .save(bpsForPatientWithNoBpsDeleted + bpsForPatientWithLatestBpDeleted + bpsForPatientWithOldestBpNotDeleted + bpsForPatientWithAllBpsDeleted)
        .blockingAwait()

    val today = LocalDate.now(clock)
    val appointmentsScheduledFor = today.minusDays(1L)

    val appointmentForPatientWithNoBpsDeleted = createAppointment(
        patientUuid = noBpsDeletedPatientUuid,
        scheduledDate = appointmentsScheduledFor
    )

    val appointmentForPatientWithLatestBpDeleted = createAppointment(
        patientUuid = latestBpDeletedPatientUuid,
        scheduledDate = appointmentsScheduledFor
    )

    val appointmentsForPatientWithOldestBpNotDeleted = createAppointment(
        patientUuid = oldestBpNotDeletedPatientUuid,
        scheduledDate = appointmentsScheduledFor
    )

    val appointmentsForPatientWithAllBpsDeleted = createAppointment(
        patientUuid = allBpsDeletedPatientUuid,
        scheduledDate = appointmentsScheduledFor
    )

    appointmentRepository
        .save(listOf(appointmentForPatientWithNoBpsDeleted, appointmentForPatientWithLatestBpDeleted, appointmentsForPatientWithOldestBpNotDeleted, appointmentsForPatientWithAllBpsDeleted))
        .blockingAwait()

    // when
    val overdueAppointments = appointmentRepository.overdueAppointments(since = today, facility = facility).blockingFirst()
        .associateBy { it.appointment.patientUuid }

    // then
    assertThat(overdueAppointments.keys).containsExactly(noBpsDeletedPatientUuid, latestBpDeletedPatientUuid, oldestBpNotDeletedPatientUuid)

    val lastSeenForNoBpsDeletedPatient = overdueAppointments.getValue(noBpsDeletedPatientUuid).patientLastSeen
    val lastSeenForLatestBpDeletedPatient = overdueAppointments.getValue(latestBpDeletedPatientUuid).patientLastSeen
    val lastSeenForOldestBpDeletedPatient = overdueAppointments.getValue(oldestBpNotDeletedPatientUuid).patientLastSeen

    assertThat(lastSeenForNoBpsDeletedPatient).isEqualTo(bpsForPatientWithNoBpsDeleted[1].recordedAt)
    assertThat(lastSeenForLatestBpDeletedPatient).isEqualTo(bpsForPatientWithLatestBpDeleted[1].recordedAt)
    assertThat(lastSeenForOldestBpDeletedPatient).isEqualTo(bpsForPatientWithOldestBpNotDeleted[0].recordedAt)
  }

  @Test
  fun deleted_blood_sugar_measurements_should_not_be_considered_when_fetching_overdue_appointments() {
    fun createBloodSugar(
        bpUuid: UUID,
        patientUuid: UUID,
        recordedAt: Instant,
        deletedAt: Instant? = null
    ): BloodSugarMeasurement {
      return testData.bloodSugarMeasurement(
          uuid = bpUuid,
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          userUuid = userUuid,
          syncStatus = DONE,
          createdAt = Instant.parse("2018-01-01T00:00:00Z"),
          updatedAt = Instant.parse("2018-01-01T00:00:00Z"),
          recordedAt = recordedAt,
          deletedAt = deletedAt
      )
    }

    fun createAppointment(patientUuid: UUID, scheduledDate: LocalDate): Appointment {
      return testData.appointment(
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          status = Scheduled,
          scheduledDate = scheduledDate)
    }

    // given
    val noBloodSugarsDeletedPatientUuid = UUID.fromString("d05b8ed2-97ae-4fda-8af9-bc4168af3c4d")
    val latestBloodSugarDeletedPatientUuid = UUID.fromString("9e5ec219-f4a5-4bab-9283-0a087c5d7ac2")
    val oldestBloodSugarNotDeletedPatientUuid = UUID.fromString("54e7143c-fe64-4cd8-8c92-f379a79a60f9")
    val allBloodSugarsDeletedPatientUuid = UUID.fromString("05bd9d55-5742-466f-b97e-07301e25fe7e")

    val patients = listOf(
        testData.patientProfile(
            patientUuid = noBloodSugarsDeletedPatientUuid,
            generatePhoneNumber = true,
            patientName = "No blood sugars are deleted"
        ),
        testData.patientProfile(
            patientUuid = latestBloodSugarDeletedPatientUuid,
            generatePhoneNumber = true,
            patientName = "Latest blood sugar is deleted"
        ),
        testData.patientProfile(
            patientUuid = oldestBloodSugarNotDeletedPatientUuid,
            generatePhoneNumber = true,
            patientName = "Oldest blood sugar is not deleted"
        ),
        testData.patientProfile(
            patientUuid = allBloodSugarsDeletedPatientUuid,
            generatePhoneNumber = true,
            patientName = "All blood sugars are deleted"
        )
    )

    patientRepository.save(patients).blockingAwait()

    val bloodSugarForPatientWithNoBloodSugarsDeleted = listOf(
        createBloodSugar(
            bpUuid = UUID.fromString("189b0842-044e-4f1c-a214-24318052f11d"),
            patientUuid = noBloodSugarsDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-01T00:00:00Z")
        ),
        createBloodSugar(
            bpUuid = UUID.fromString("ce5deb11-05ee-4f9e-8734-ec3d99f271a9"),
            patientUuid = noBloodSugarsDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-02T00:00:00Z")
        )
    )

    val bloodSugarsForPatientWithLatestBloodSugarDeleted = listOf(
        createBloodSugar(
            bpUuid = UUID.fromString("55266e25-0c15-4cd3-969d-3c5d5af48c62"),
            patientUuid = latestBloodSugarDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-03T00:00:00Z")
        ),
        createBloodSugar(
            bpUuid = UUID.fromString("e4c3461e-8624-4b6e-874b-bb73967e423e"),
            patientUuid = latestBloodSugarDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-04T00:00:00Z")
        ),
        createBloodSugar(
            bpUuid = UUID.fromString("e7d19558-36d8-4b5a-a17a-6e3117622b57"),
            patientUuid = latestBloodSugarDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-05T00:00:00Z"),
            deletedAt = Instant.parse("2018-01-05T00:00:00Z")
        )
    )

    val bloodSugarsForPatientWithOldestBloodSugarNotDeleted = listOf(
        createBloodSugar(
            bpUuid = UUID.fromString("1de759ae-9f60-4be5-a1f1-d18143bf8318"),
            patientUuid = oldestBloodSugarNotDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-06T00:00:00Z")
        ),
        createBloodSugar(
            bpUuid = UUID.fromString("f135aaa8-e4d6-48c0-acbf-ed0938c44f34"),
            patientUuid = oldestBloodSugarNotDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-07T00:00:00Z"),
            deletedAt = Instant.parse("2018-01-07T00:00:00Z")
        ),
        createBloodSugar(
            bpUuid = UUID.fromString("44cff8a9-08c2-4a48-9f4b-5c1ec7d9c10c"),
            patientUuid = oldestBloodSugarNotDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-08T00:00:00Z"),
            deletedAt = Instant.parse("2018-01-08T00:00:00Z")
        )
    )

    val bloodSugarsForPatientWithAllBloodSugarsDeleted = listOf(
        createBloodSugar(
            bpUuid = UUID.fromString("264c4295-c61b-41df-8548-460977510574"),
            patientUuid = allBloodSugarsDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-09T00:00:00Z"),
            deletedAt = Instant.parse("2018-01-09T00:00:00Z")
        ),
        createBloodSugar(
            bpUuid = UUID.fromString("ff2a665e-d09a-4110-9791-8e966690370f"),
            patientUuid = allBloodSugarsDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-10T00:00:00Z"),
            deletedAt = Instant.parse("2018-01-10T00:00:00Z")
        ),
        createBloodSugar(
            bpUuid = UUID.fromString("4e97bd7e-87ea-4d4c-a826-3784703937ed"),
            patientUuid = allBloodSugarsDeletedPatientUuid,
            recordedAt = Instant.parse("2018-01-11T00:00:02Z"),
            deletedAt = Instant.parse("2018-01-11T00:00:00Z")
        )
    )

    bloodSugarRepository
        .save(bloodSugarForPatientWithNoBloodSugarsDeleted + bloodSugarsForPatientWithLatestBloodSugarDeleted + bloodSugarsForPatientWithOldestBloodSugarNotDeleted + bloodSugarsForPatientWithAllBloodSugarsDeleted)
        .blockingAwait()

    val today = LocalDate.now(clock)
    val appointmentsScheduledFor = today.minusDays(1L)

    val appointmentForPatientWithNoBloodSugarDeleted = createAppointment(
        patientUuid = noBloodSugarsDeletedPatientUuid,
        scheduledDate = appointmentsScheduledFor
    )

    val appointmentForPatientWithLatestBloodSugarDeleted = createAppointment(
        patientUuid = latestBloodSugarDeletedPatientUuid,
        scheduledDate = appointmentsScheduledFor
    )

    val appointmentsForPatientWithOldestBloodSugarNotDeleted = createAppointment(
        patientUuid = oldestBloodSugarNotDeletedPatientUuid,
        scheduledDate = appointmentsScheduledFor
    )

    val appointmentsForPatientWithAllBloodSugarsDeleted = createAppointment(
        patientUuid = allBloodSugarsDeletedPatientUuid,
        scheduledDate = appointmentsScheduledFor
    )

    appointmentRepository
        .save(listOf(appointmentForPatientWithNoBloodSugarDeleted, appointmentForPatientWithLatestBloodSugarDeleted, appointmentsForPatientWithOldestBloodSugarNotDeleted, appointmentsForPatientWithAllBloodSugarsDeleted))
        .blockingAwait()

    // when
    val overdueAppointments = appointmentRepository.overdueAppointments(since = today, facility = facility).blockingFirst()
        .associateBy { it.appointment.patientUuid }

    // then
    assertThat(overdueAppointments.keys).containsExactly(noBloodSugarsDeletedPatientUuid, latestBloodSugarDeletedPatientUuid, oldestBloodSugarNotDeletedPatientUuid)

    val appointmentBpUuidOfNoBpsDeletedPatient = overdueAppointments.getValue(noBloodSugarsDeletedPatientUuid).patientLastSeen
    val appointmentBpUuidOfLatestBpDeletedPatient = overdueAppointments.getValue(latestBloodSugarDeletedPatientUuid).patientLastSeen
    val appointmentBpUuidOfOldestBpDeletedPatient = overdueAppointments.getValue(oldestBloodSugarNotDeletedPatientUuid).patientLastSeen

    assertThat(appointmentBpUuidOfNoBpsDeletedPatient).isEqualTo(bloodSugarForPatientWithNoBloodSugarsDeleted[1].recordedAt)
    assertThat(appointmentBpUuidOfLatestBpDeletedPatient).isEqualTo(bloodSugarsForPatientWithLatestBloodSugarDeleted[1].recordedAt)
    assertThat(appointmentBpUuidOfOldestBpDeletedPatient).isEqualTo(bloodSugarsForPatientWithOldestBloodSugarNotDeleted[0].recordedAt)
  }

  @Test
  fun when_setting_appointment_reminder_then_reminder_with_correct_date_should_be_set() {
    // given
    val appointmentDate = LocalDate.parse("2018-01-01")
    val appointmentScheduledAtTimestamp = Instant.now(clock)
    appointmentRepository.schedule(
        patientUuid = patientUuid,
        appointmentUuid = appointmentUuid,
        appointmentDate = appointmentDate,
        appointmentType = Manual,
        appointmentFacilityUuid = facility.uuid,
        creationFacilityUuid = facility.uuid
    ).blockingGet()
    markAppointmentSyncStatusAsDone(appointmentUuid)

    clock.advanceBy(Duration.ofHours(24))

    val reminderDate = LocalDate.parse("2018-02-01")

    // when
    appointmentRepository.createReminder(appointmentUuid, reminderDate).blockingGet()

    // then
    val appointmentUpdatedAtTimestamp = Instant.now(clock)
    val updatedAppointment = getAppointmentByUuid(appointmentUuid)
    with(updatedAppointment) {
      assertThat(remindOn).isEqualTo(reminderDate)
      assertThat(agreedToVisit).isNull()
      assertThat(syncStatus).isEqualTo(PENDING)
      assertThat(createdAt).isEqualTo(appointmentScheduledAtTimestamp)
      assertThat(createdAt).isLessThan(appointmentUpdatedAtTimestamp)
      assertThat(updatedAt).isEqualTo(appointmentUpdatedAtTimestamp)
    }
  }

  @Test
  fun when_marking_appointment_as_agreed_to_visit_reminder_for_a_month_should_be_set() {
    // given
    val appointmentScheduleDate = LocalDate.parse("2018-01-01")
    val appointmentScheduledAtTimestamp = Instant.now(clock)
    appointmentRepository.schedule(
        patientUuid = patientUuid,
        appointmentUuid = appointmentUuid,
        appointmentDate = appointmentScheduleDate,
        appointmentType = Manual,
        appointmentFacilityUuid = facility.uuid,
        creationFacilityUuid = facility.uuid
    ).blockingGet()
    markAppointmentSyncStatusAsDone(appointmentUuid)

    clock.advanceBy(Duration.ofSeconds(1))
    val userClock = TestUserClock(LocalDate.parse("2018-01-31"))

    // when
    appointmentRepository.markAsAgreedToVisit(appointmentUuid, userClock).blockingAwait()

    // then
    val appointmentUpdatedAtTimestamp = Instant.parse("2018-01-01T00:00:01Z")
    with(getAppointmentByUuid(appointmentUuid)) {
      assertThat(remindOn).isEqualTo(LocalDate.parse("2018-02-28"))
      assertThat(agreedToVisit).isTrue()
      assertThat(syncStatus).isEqualTo(PENDING)
      assertThat(createdAt).isEqualTo(appointmentScheduledAtTimestamp)
      assertThat(createdAt).isLessThan(appointmentUpdatedAtTimestamp)
      assertThat(updatedAt).isEqualTo(appointmentUpdatedAtTimestamp)
    }
  }

  @Test
  fun when_removing_appointment_from_list_then_appointment_status_and_cancel_reason_should_be_updated() {
    // given
    val appointmentScheduleDate = LocalDate.parse("2018-01-01")
    val appointmentScheduledTimestamp = Instant.now(clock)
    appointmentRepository.schedule(
        patientUuid = patientUuid,
        appointmentUuid = appointmentUuid,
        appointmentDate = appointmentScheduleDate,
        appointmentType = Manual,
        appointmentFacilityUuid = facility.uuid,
        creationFacilityUuid = facility.uuid
    ).blockingGet()
    markAppointmentSyncStatusAsDone(appointmentUuid)

    clock.advanceBy(Duration.ofDays(1))

    // when
    appointmentRepository.cancelWithReason(appointmentUuid, PatientNotResponding).blockingGet()

    // then
    val updatedAppointment = getAppointmentByUuid(appointmentUuid)
    val appointmentUpdatedAtTimestamp = Instant.now(clock)
    with(updatedAppointment) {
      assertThat(cancelReason).isEqualTo(PatientNotResponding)
      assertThat(status).isEqualTo(Cancelled)
      assertThat(syncStatus).isEqualTo(PENDING)
      assertThat(createdAt).isEqualTo(appointmentScheduledTimestamp)
      assertThat(createdAt).isLessThan(appointmentUpdatedAtTimestamp)
      assertThat(updatedAt).isEqualTo(appointmentUpdatedAtTimestamp)
    }
  }

  @Test
  fun when_removing_appointment_with_reason_as_patient_already_visited_then_appointment_should_be_marked_as_visited() {
    // given
    val appointmentScheduleDate = LocalDate.parse("2018-01-01")
    val appointmentScheduledTimestamp = Instant.now(clock)
    appointmentRepository.schedule(
        patientUuid = patientUuid,
        appointmentUuid = appointmentUuid,
        appointmentDate = appointmentScheduleDate,
        appointmentType = Manual,
        appointmentFacilityUuid = facility.uuid,
        creationFacilityUuid = facility.uuid
    ).blockingGet()
    markAppointmentSyncStatusAsDone(appointmentUuid)

    clock.advanceBy(Duration.ofDays(1))

    // when
    appointmentRepository.markAsAlreadyVisited(appointmentUuid).blockingAwait()

    // then
    val appointmentUpdatedAtTimestamp = Instant.now(clock)
    with(getAppointmentByUuid(appointmentUuid)) {
      assertThat(cancelReason).isNull()
      assertThat(status).isEqualTo(Visited)
      assertThat(createdAt).isEqualTo(appointmentScheduledTimestamp)
      assertThat(createdAt).isLessThan(appointmentUpdatedAtTimestamp)
      assertThat(updatedAt).isEqualTo(appointmentUpdatedAtTimestamp)
    }
  }

  @Test
  fun high_risk_patients_should_be_present_at_the_top_when_loading_overdue_appointments() {
    data class BP(val systolic: Int, val diastolic: Int)

    fun savePatientAndAppointment(
        patientUuid: UUID,
        appointmentUuid: UUID = UUID.randomUUID(),
        fullName: String,
        bps: List<BP>,
        hasHadHeartAttack: Answer = No,
        hasHadStroke: Answer = No,
        hasDiabetes: Answer = No,
        hasHadKidneyDisease: Answer = No,
        appointmentHasBeenOverdueFor: Duration
    ) {
      val patientProfile = testData.patientProfile(
          patientUuid = patientUuid,
          patientName = fullName,
          generatePhoneNumber = true,
          generateBusinessId = false
      )
      patientRepository.save(listOf(patientProfile)).blockingAwait()

      val scheduledDate = (LocalDateTime.now(clock) - appointmentHasBeenOverdueFor).toLocalDate()
      appointmentRepository.schedule(
          patientUuid = patientUuid,
          appointmentUuid = appointmentUuid,
          appointmentDate = scheduledDate,
          appointmentType = Manual,
          appointmentFacilityUuid = facility.uuid,
          creationFacilityUuid = facility.uuid
      ).blockingGet()

      val bloodPressureMeasurements = bps.mapIndexed { index, (systolic, diastolic) ->

        val bpTimestamp = Instant.now(clock).plusSeconds(index.toLong() + 1)

        testData.bloodPressureMeasurement(
            patientUuid = patientUuid,
            systolic = systolic,
            diastolic = diastolic,
            userUuid = userUuid,
            facilityUuid = facility.uuid,
            recordedAt = bpTimestamp,
            createdAt = bpTimestamp,
            updatedAt = bpTimestamp
        )
      }
      bpRepository.save(bloodPressureMeasurements).blockingAwait()

      medicalHistoryRepository.save(patientUuid, OngoingMedicalHistoryEntry(
          hasHadHeartAttack = hasHadHeartAttack,
          hasHadStroke = hasHadStroke,
          hasHadKidneyDisease = hasHadKidneyDisease,
          hasDiabetes = hasDiabetes
      )).blockingAwait()
      clock.advanceBy(Duration.ofSeconds(bps.size.toLong() + 1))
    }

    // given
    val thirtyDays = Duration.ofDays(30)
    val threeFiftyDays = Duration.ofDays(350)

    savePatientAndAppointment(
        patientUuid = UUID.fromString("0620c310-0248-4d05-b7c4-8134bd7335e8"),
        fullName = "Has had a heart attack, sBP < 140 & dBP < 110, overdue == 30 days",
        bps = listOf(BP(systolic = 100, diastolic = 90)),
        hasHadHeartAttack = Yes,
        appointmentHasBeenOverdueFor = thirtyDays
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("fb4b1804-335b-41ba-b14d-0263ca9cfe6b"),
        fullName = "Has had a heart attack, sBP > 140, overdue == 30 days",
        bps = listOf(BP(systolic = 145, diastolic = 90)),
        hasHadHeartAttack = Yes,
        appointmentHasBeenOverdueFor = thirtyDays
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("c99e9290-c456-4944-9ea3-7f68d1da17df"),
        fullName = "Has had a heart attack, dBP > 110, overdue == 30 days",
        bps = listOf(BP(systolic = 130, diastolic = 120)),
        hasHadHeartAttack = Yes,
        appointmentHasBeenOverdueFor = thirtyDays
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("c4c9b12e-05f2-4343-9a1d-319049df4ff7"),
        fullName = "Has had a stroke, sBP < 140 & dBP < 110, overdue == 20 days",
        bps = listOf(BP(systolic = 100, diastolic = 90)),
        hasHadStroke = Yes,
        appointmentHasBeenOverdueFor = Duration.ofDays(20)
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("2c24c3a5-c385-4e5e-8643-b48ab28107c8"),
        fullName = "Has had a kidney disease, overdue == 30 days",
        bps = listOf(BP(systolic = 100, diastolic = 90)),
        hasHadKidneyDisease = Yes,
        appointmentHasBeenOverdueFor = thirtyDays
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("8b497bb5-f809-434c-b1a4-4efdf810f044"),
        fullName = "Has diabetes, overdue == 30 days",
        bps = listOf(BP(systolic = 100, diastolic = 90)),
        hasDiabetes = Yes,
        appointmentHasBeenOverdueFor = thirtyDays
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("cd78a254-a028-4b5d-bdcd-5ff367ad4143"),
        fullName = "Has had a heart attack, stroke, kidney disease and has diabetes, sBP > 140, overdue == 30 days",
        bps = listOf(BP(systolic = 140, diastolic = 90)),
        hasHadStroke = Yes,
        hasHadHeartAttack = Yes,
        hasHadKidneyDisease = Yes,
        hasDiabetes = Yes,
        appointmentHasBeenOverdueFor = thirtyDays
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("9f51709f-b356-4d9c-b6b7-1466eca35b78"),
        fullName = "Systolic > 180, overdue == 4 days",
        bps = listOf(BP(systolic = 9000, diastolic = 100)),
        appointmentHasBeenOverdueFor = Duration.ofDays(4)
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("a4ed131e-c02e-469d-87d1-8aa63a9da780"),
        fullName = "Diastolic > 110, overdue == 3 days",
        bps = listOf(BP(systolic = 100, diastolic = 9000)),
        appointmentHasBeenOverdueFor = Duration.ofDays(3)
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("b2a3fbd1-27eb-4ef4-b78d-f66cdbb164b4"),
        fullName = "Systolic == 180, overdue == 30 days",
        bps = listOf(BP(systolic = 180, diastolic = 90)),
        appointmentHasBeenOverdueFor = thirtyDays
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("488ca972-9937-4bce-8ed6-2a926963432a"),
        fullName = "Systolic == 170, overdue == 30 days",
        bps = listOf(BP(systolic = 170, diastolic = 90)),
        appointmentHasBeenOverdueFor = thirtyDays
    )


    savePatientAndAppointment(
        patientUuid = UUID.fromString("a6ca12c1-6f00-4ea9-82f7-b949be415471"),
        fullName = "Diastolic == 110, overdue == 30 days",
        bps = listOf(BP(systolic = 101, diastolic = 110)),
        appointmentHasBeenOverdueFor = thirtyDays
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("b261dc0d-c6e1-4a3c-9c48-5335928ddd63"),
        fullName = "Diastolic == 100, overdue == 30 days",
        bps = listOf(BP(systolic = 101, diastolic = 100)),
        appointmentHasBeenOverdueFor = thirtyDays
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("234cbcb8-c3b1-4b7c-be34-7ac3691c1df7"),
        fullName = "BP == 141/91, overdue == 350 days",
        bps = listOf(BP(systolic = 141, diastolic = 91)),
        appointmentHasBeenOverdueFor = threeFiftyDays
    )

    savePatientAndAppointment(
        patientUuid = UUID.fromString("96c30123-9b08-4e11-b058-e80a62030a31"),
        fullName = "BP == 110/80, overdue between 30 days and 1 year",
        bps = listOf(BP(systolic = 110, diastolic = 80)),
        appointmentHasBeenOverdueFor = Duration.ofDays(80)
    )

    // when
    val appointments = appointmentRepository.overdueAppointments(since = LocalDate.now(clock), facility = facility).blockingFirst()

    // then
    assertThat(appointments.map { it.fullName to it.isAtHighRisk }).isEqualTo(listOf(
        "Diastolic > 110, overdue == 3 days" to true,
        "Systolic > 180, overdue == 4 days" to true,
        "Has had a heart attack, sBP > 140, overdue == 30 days" to true,
        "Has had a heart attack, dBP > 110, overdue == 30 days" to true,
        "Has had a heart attack, stroke, kidney disease and has diabetes, sBP > 140, overdue == 30 days" to true,
        "Systolic == 180, overdue == 30 days" to true,
        "Diastolic == 110, overdue == 30 days" to true,
        "Has had a stroke, sBP < 140 & dBP < 110, overdue == 20 days" to false,
        "Has had a heart attack, sBP < 140 & dBP < 110, overdue == 30 days" to false,
        "Has had a kidney disease, overdue == 30 days" to false,
        "Has diabetes, overdue == 30 days" to false,
        "Systolic == 170, overdue == 30 days" to false,
        "Diastolic == 100, overdue == 30 days" to false,
        "BP == 110/80, overdue between 30 days and 1 year" to false,
        "BP == 141/91, overdue == 350 days" to false
    ))
  }

  @Test
  fun when_fetching_overdue_appointments_it_should_exclude_appointments_more_than_a_year_overdue() {
    fun createOverdueAppointment(
        patientUuid: UUID,
        scheduledDate: LocalDate,
        facilityUuid: UUID
    ) {
      val patientProfile = testData.patientProfile(
          patientUuid = patientUuid,
          generatePhoneNumber = true
      )
      patientRepository.save(listOf(patientProfile)).blockingAwait()

      val bp = testData.bloodPressureMeasurement(
          patientUuid = patientUuid,
          facilityUuid = facilityUuid
      )
      bpRepository.save(listOf(bp)).blockingAwait()

      val appointment = testData.appointment(
          patientUuid = patientUuid,
          facilityUuid = facilityUuid,
          scheduledDate = scheduledDate,
          status = Scheduled,
          cancelReason = null
      )
      appointmentRepository.save(listOf(appointment)).blockingAwait()
    }

    //given
    val patientWithOneDayOverdue = UUID.fromString("9b794e72-6ebb-48c3-a8d7-69751ffeecc2")
    val patientWithTenDaysOverdue = UUID.fromString("0fc57e45-7018-4c03-9218-f90f6fc0f268")
    val patientWithOverAnYearDaysOverdue = UUID.fromString("51467803-f588-4a65-8def-7a15f41bdd13")

    val now = LocalDate.now(clock)
    val facilityUuid = UUID.fromString("ccc66ec1-5029-455b-bf92-caa6d90a9a79")

    createOverdueAppointment(patientWithOneDayOverdue, now.minusDays(1), facilityUuid)
    createOverdueAppointment(patientWithTenDaysOverdue, now.minusDays(10), facilityUuid)
    createOverdueAppointment(patientWithOverAnYearDaysOverdue, now.minusDays(370), facilityUuid)

    //when
    val overduePatientUuids = appointmentRepository.overdueAppointments(since = now, facility = testData.facility(uuid = facilityUuid)).blockingFirst().map { it.appointment.patientUuid }

    //then
    assertThat(overduePatientUuids).containsExactly(patientWithOneDayOverdue, patientWithTenDaysOverdue)
    assertThat(overduePatientUuids).doesNotContain(patientWithOverAnYearDaysOverdue)
  }

  @Test
  fun when_fetching_appointment_for_patient_it_should_return_the_last_created_appointment() {
    // given
    val scheduledDateForFirstAppointment = LocalDate.parse("2018-02-01")
    appointmentRepository.schedule(
        patientUuid = patientUuid,
        appointmentUuid = UUID.fromString("faa8cd6c-4aca-41c9-983a-1a10b6704466"),
        appointmentDate = scheduledDateForFirstAppointment,
        appointmentType = Manual,
        appointmentFacilityUuid = facility.uuid,
        creationFacilityUuid = facility.uuid
    ).blockingGet()

    clock.advanceBy(Duration.ofDays(1))

    val scheduledDateForSecondAppointment = LocalDate.parse("2018-02-08")
    val secondAppointment = appointmentRepository.schedule(
        patientUuid = patientUuid,
        appointmentUuid = UUID.fromString("634b4807-d3a8-42a9-8411-7c921ed57f49"),
        appointmentDate = scheduledDateForSecondAppointment,
        appointmentType = Manual,
        appointmentFacilityUuid = facility.uuid,
        creationFacilityUuid = facility.uuid
    ).blockingGet()

    // when
    val appointment = appointmentRepository.lastCreatedAppointmentForPatient(patientUuid).toNullable()!!

    // then
    assertThat(appointment).isEqualTo(secondAppointment)
  }

  @Test
  fun marking_appointment_older_than_current_date_as_visited_should_work_correctly() {
    // given
    val firstAppointmentUuid = UUID.fromString("96b21ba5-e12d-41ec-bfc9-f09bac6ed435")
    val secondAppointmentUuid = UUID.fromString("2fbbf320-3b78-4f26-a8d6-5a90d2800711")

    val appointmentScheduleDate = LocalDate.parse("2018-02-01")

    clock.advanceBy(Duration.ofHours(1))
    database
        .appointmentDao()
        .save(listOf(testData.appointment(
            uuid = firstAppointmentUuid,
            patientUuid = patientUuid,
            status = Scheduled,
            syncStatus = DONE,
            scheduledDate = appointmentScheduleDate,
            createdAt = Instant.now(clock),
            updatedAt = Instant.now(clock)
        )))
    val firstAppointmentBeforeMarkingAsCreatedOnCurrentDay = getAppointmentByUuid(firstAppointmentUuid)

    // then
    clock.advanceBy(Duration.ofHours(1))
    appointmentRepository.markAppointmentsCreatedBeforeTodayAsVisited(patientUuid).blockingAwait()
    assertThat(getAppointmentByUuid(firstAppointmentUuid)).isEqualTo(firstAppointmentBeforeMarkingAsCreatedOnCurrentDay)

    // then
    clock.advanceBy(Duration.ofDays(1))
    database
        .appointmentDao()
        .save(listOf(testData.appointment(
            uuid = secondAppointmentUuid,
            patientUuid = patientUuid,
            scheduledDate = appointmentScheduleDate,
            status = Scheduled,
            syncStatus = PENDING,
            createdAt = Instant.now(clock),
            updatedAt = Instant.now(clock)
        )))

    val secondAppointmentBeforeMarkingAsCreatedOnNextDay = getAppointmentByUuid(secondAppointmentUuid)
    appointmentRepository.markAppointmentsCreatedBeforeTodayAsVisited(patientUuid).blockingAwait()

    val firstAppointmentAfterMarkingAsCreatedOnNextDay = getAppointmentByUuid(firstAppointmentUuid)

    with(firstAppointmentAfterMarkingAsCreatedOnNextDay) {
      assertThat(status).isEqualTo(Visited)
      assertThat(syncStatus).isEqualTo(PENDING)
      assertThat(createdAt).isEqualTo(firstAppointmentBeforeMarkingAsCreatedOnCurrentDay.createdAt)
      assertThat(createdAt).isLessThan(updatedAt)
      assertThat(updatedAt).isEqualTo(Instant.now(clock))
    }
    assertThat(getAppointmentByUuid(secondAppointmentUuid))
        .isEqualTo(secondAppointmentBeforeMarkingAsCreatedOnNextDay)
  }

  @Test
  fun when_scheduling_appointment_for_defaulter_patient_then_the_appointment_should_be_saved_as_defaulter() {
    // given
    val appointmentScheduleDate = LocalDate.parse("2018-01-01")

    // when
    appointmentRepository.schedule(
        patientUuid = patientUuid,
        appointmentUuid = appointmentUuid,
        appointmentDate = appointmentScheduleDate,
        appointmentType = Automatic,
        appointmentFacilityUuid = facility.uuid,
        creationFacilityUuid = facility.uuid
    ).blockingGet()

    // then
    val savedAppointment = getAppointmentByUuid(appointmentUuid)
    with(savedAppointment) {
      assertThat(patientUuid).isEqualTo(this@AppointmentRepositoryAndroidTest.patientUuid)
      assertThat(scheduledDate).isEqualTo(appointmentScheduleDate)
      assertThat(status).isEqualTo(Scheduled)
      assertThat(syncStatus).isEqualTo(PENDING)
      assertThat(appointmentType).isEqualTo(Automatic)
    }
  }

  @Test
  fun when_picking_overdue_appointment_then_the_latest_recorded_bp_should_be_considered() {
    fun createBloodPressure(patientProfile: PatientProfile, recordedAt: Instant): BloodPressureMeasurement {
      return testData.bloodPressureMeasurement(
          patientUuid = patientProfile.patient.uuid,
          recordedAt = recordedAt
      )
    }

    fun scheduleAppointment(
        appointmentUuid: UUID,
        patientProfile: PatientProfile
    ): Single<Appointment> {
      return appointmentRepository.schedule(
          patientUuid = patientProfile.patient.uuid,
          appointmentUuid = appointmentUuid,
          appointmentDate = LocalDate.parse("2017-12-30"),
          appointmentType = Manual,
          appointmentFacilityUuid = facility.uuid,
          creationFacilityUuid = facility.uuid
      )
    }

    // given
    val firstPatient = testData.patientProfile(
        patientUuid = UUID.fromString("e1943cfb-faf0-42c4-b5b6-14b5153295b2"),
        generatePhoneNumber = true
    )
    val secondPatient = testData.patientProfile(
        patientUuid = UUID.fromString("08c7acbf-61f1-439e-93a8-43ba4e990428"),
        generatePhoneNumber = true
    )

    patientRepository.save(listOf(firstPatient, secondPatient)).blockingAwait()

    val earlierRecordedBpForFirstPatient = createBloodPressure(
        patientProfile = firstPatient,
        recordedAt = Instant.parse("2017-12-31T23:59:59Z")
    )
    val laterRecordedBpForFirstPatient = createBloodPressure(
        patientProfile = firstPatient,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z")
    )

    val earlierRecordedBpForSecondPatient = createBloodPressure(
        patientProfile = secondPatient,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z")
    )
    val laterRecordedBpForSecondPatient = createBloodPressure(
        patientProfile = secondPatient,
        recordedAt = Instant.parse("2018-01-01T00:00:01Z")
    )

    bpRepository.save(listOf(laterRecordedBpForFirstPatient, earlierRecordedBpForFirstPatient, earlierRecordedBpForSecondPatient, laterRecordedBpForSecondPatient)).blockingAwait()

    val appointmentUuidForFirstPatient = UUID.fromString("d9fd734d-13b8-43e3-a2d7-b40341699050")
    val appointmentUuidForSecondPatient = UUID.fromString("979e4a13-ae73-4dcf-a1e0-31465dff5512")

    scheduleAppointment(appointmentUuidForFirstPatient, firstPatient).blockingGet()
    scheduleAppointment(appointmentUuidForSecondPatient, secondPatient).blockingGet()

    // when
    val bloodPressuresByAppointmentUuid = appointmentRepository
        .overdueAppointments(since = LocalDate.now(clock), facility = facility)
        .blockingFirst()
        .associateBy({ it.appointment.uuid }, { it.patientLastSeen })

    // then
    val expected = mapOf(
        appointmentUuidForFirstPatient to laterRecordedBpForFirstPatient.recordedAt,
        appointmentUuidForSecondPatient to laterRecordedBpForSecondPatient.recordedAt
    )
    assertThat(bloodPressuresByAppointmentUuid).isEqualTo(expected)
  }

  @Test
  fun when_picking_overdue_appointment_then_the_latest_recorded_blood_sugar_should_be_considered() {
    fun createBloodSugar(patientProfile: PatientProfile, recordedAt: Instant): BloodSugarMeasurement {
      return testData.bloodSugarMeasurement(
          patientUuid = patientProfile.patient.uuid,
          recordedAt = recordedAt
      )
    }

    fun scheduleAppointment(
        appointmentUuid: UUID,
        patientProfile: PatientProfile
    ): Single<Appointment> {
      return appointmentRepository.schedule(
          patientUuid = patientProfile.patient.uuid,
          appointmentUuid = appointmentUuid,
          appointmentDate = LocalDate.parse("2017-12-30"),
          appointmentType = Manual,
          appointmentFacilityUuid = facility.uuid,
          creationFacilityUuid = facility.uuid
      )
    }

    // given
    val firstPatient = testData.patientProfile(
        patientUuid = UUID.fromString("e1943cfb-faf0-42c4-b5b6-14b5153295b2"),
        generatePhoneNumber = true
    )
    val secondPatient = testData.patientProfile(
        patientUuid = UUID.fromString("08c7acbf-61f1-439e-93a8-43ba4e990428"),
        generatePhoneNumber = true
    )

    patientRepository.save(listOf(firstPatient, secondPatient)).blockingAwait()

    val earlierRecordedBloodSugarForFirstPatient = createBloodSugar(
        patientProfile = firstPatient,
        recordedAt = Instant.parse("2017-12-31T23:59:59Z")
    )
    val laterRecordedBloodSugarForFirstPatient = createBloodSugar(
        patientProfile = firstPatient,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z")
    )

    val earlierRecordedBloodSugarForSecondPatient = createBloodSugar(
        patientProfile = secondPatient,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z")
    )
    val laterRecordedBloodSugarForSecondPatient = createBloodSugar(
        patientProfile = secondPatient,
        recordedAt = Instant.parse("2018-01-01T00:00:01Z")
    )

    bloodSugarRepository.save(listOf(laterRecordedBloodSugarForFirstPatient, earlierRecordedBloodSugarForFirstPatient, earlierRecordedBloodSugarForSecondPatient, laterRecordedBloodSugarForSecondPatient)).blockingAwait()

    val appointmentUuidForFirstPatient = UUID.fromString("d9fd734d-13b8-43e3-a2d7-b40341699050")
    val appointmentUuidForSecondPatient = UUID.fromString("979e4a13-ae73-4dcf-a1e0-31465dff5512")

    scheduleAppointment(appointmentUuidForFirstPatient, firstPatient).blockingGet()
    scheduleAppointment(appointmentUuidForSecondPatient, secondPatient).blockingGet()

    // when
    val bloodSugarByAppointmentUuid = appointmentRepository
        .overdueAppointments(since = LocalDate.now(clock), facility = facility)
        .blockingFirst()
        .associateBy({ it.appointment.uuid }, { it.patientLastSeen })

    // then
    val expected = mapOf(
        appointmentUuidForFirstPatient to laterRecordedBloodSugarForFirstPatient.recordedAt,
        appointmentUuidForSecondPatient to laterRecordedBloodSugarForSecondPatient.recordedAt
    )
    assertThat(bloodSugarByAppointmentUuid).isEqualTo(expected)
  }

  @Test
  fun when_picking_overdue_appointment_and_blood_sugar_is_latest_compared_to_blood_pressure_then_blood_sugar_should_be_considered() {
    fun createBloodSugar(patientProfile: PatientProfile, recordedAt: Instant): BloodSugarMeasurement {
      return testData.bloodSugarMeasurement(
          patientUuid = patientProfile.patient.uuid,
          recordedAt = recordedAt
      )
    }

    fun createBloodPressure(patientProfile: PatientProfile, recordedAt: Instant): BloodPressureMeasurement {
      return testData.bloodPressureMeasurement(
          patientUuid = patientProfile.patient.uuid,
          recordedAt = recordedAt
      )
    }

    fun scheduleAppointment(
        appointmentUuid: UUID,
        patientProfile: PatientProfile
    ): Single<Appointment> {
      return appointmentRepository.schedule(
          patientUuid = patientProfile.patient.uuid,
          appointmentUuid = appointmentUuid,
          appointmentDate = LocalDate.parse("2017-12-30"),
          appointmentType = Manual,
          appointmentFacilityUuid = facility.uuid,
          creationFacilityUuid = facility.uuid
      )
    }

    // given
    val patient = testData.patientProfile(
        patientUuid = UUID.fromString("e1943cfb-faf0-42c4-b5b6-14b5153295b2"),
        generatePhoneNumber = true
    )

    patientRepository.save(listOf(patient)).blockingAwait()

    val earlierRecordedBPForPatient = createBloodPressure(
        patientProfile = patient,
        recordedAt = Instant.parse("2017-12-31T23:59:59Z")
    )
    val laterRecordedBloodSugarForPatient = createBloodSugar(
        patientProfile = patient,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z")
    )

    bpRepository.save(listOf(earlierRecordedBPForPatient))
    bloodSugarRepository.save(listOf(laterRecordedBloodSugarForPatient)).blockingAwait()

    val appointmentUuidForFirstPatient = UUID.fromString("d9fd734d-13b8-43e3-a2d7-b40341699050")

    scheduleAppointment(appointmentUuidForFirstPatient, patient).blockingGet()
    scheduleAppointment(appointmentUuidForFirstPatient, patient).blockingGet()

    // when
    val bloodSugarByAppointmentUuid = appointmentRepository
        .overdueAppointments(since = LocalDate.now(clock), facility = facility)
        .blockingFirst()
        .associateBy({ it.appointment.uuid }, { it.patientLastSeen })

    // then
    val expected = mapOf(
        appointmentUuidForFirstPatient to laterRecordedBloodSugarForPatient.recordedAt
    )
    assertThat(bloodSugarByAppointmentUuid).isEqualTo(expected)
  }

  @Test
  fun when_picking_overdue_appointment_and_blood_pressure_is_latest_compared_to_blood_sugar_then_blood_pressure_should_be_considered() {
    fun createBloodSugar(patientProfile: PatientProfile, recordedAt: Instant): BloodSugarMeasurement {
      return testData.bloodSugarMeasurement(
          patientUuid = patientProfile.patient.uuid,
          recordedAt = recordedAt
      )
    }

    fun createBloodPressure(patientProfile: PatientProfile, recordedAt: Instant): BloodPressureMeasurement {
      return testData.bloodPressureMeasurement(
          patientUuid = patientProfile.patient.uuid,
          recordedAt = recordedAt
      )
    }

    fun scheduleAppointment(
        appointmentUuid: UUID,
        patientProfile: PatientProfile
    ): Single<Appointment> {
      return appointmentRepository.schedule(
          patientUuid = patientProfile.patient.uuid,
          appointmentUuid = appointmentUuid,
          appointmentDate = LocalDate.parse("2017-12-30"),
          appointmentType = Manual,
          appointmentFacilityUuid = facility.uuid,
          creationFacilityUuid = facility.uuid
      )
    }

    // given
    val patient = testData.patientProfile(
        patientUuid = UUID.fromString("e1943cfb-faf0-42c4-b5b6-14b5153295b2"),
        generatePhoneNumber = true
    )

    patientRepository.save(listOf(patient)).blockingAwait()

    val earlierRecordedBloodSugarForPatient = createBloodSugar(
        patientProfile = patient,
        recordedAt = Instant.parse("2017-12-31T23:59:59Z")
    )
    val laterRecordedBPForPatient = createBloodPressure(
        patientProfile = patient,
        recordedAt = Instant.parse("2018-01-01T00:00:00Z")
    )

    bloodSugarRepository.save(listOf(earlierRecordedBloodSugarForPatient))
    bpRepository.save(listOf(laterRecordedBPForPatient)).blockingAwait()

    val appointmentUuidForFirstPatient = UUID.fromString("d9fd734d-13b8-43e3-a2d7-b40341699050")

    scheduleAppointment(appointmentUuidForFirstPatient, patient).blockingGet()
    scheduleAppointment(appointmentUuidForFirstPatient, patient).blockingGet()

    // when
    val bloodSugarByAppointmentUuid = appointmentRepository
        .overdueAppointments(since = LocalDate.now(clock), facility = facility)
        .blockingFirst()
        .associateBy({ it.appointment.uuid }, { it.patientLastSeen })

    // then
    val expected = mapOf(
        appointmentUuidForFirstPatient to laterRecordedBPForPatient.recordedAt
    )
    assertThat(bloodSugarByAppointmentUuid).isEqualTo(expected)
  }

  @Test
  fun deleted_patients_must_be_excluded_when_loading_overdue_appointments() {
    fun createOverdueAppointment(
        patientUuid: UUID,
        facilityUuid: UUID,
        isPatientDeleted: Boolean
    ) {
      val patientProfile = testData.patientProfile(
          patientUuid = patientUuid,
          generatePhoneNumber = true,
          patientDeletedAt = if (isPatientDeleted) Instant.now() else null
      )
      patientRepository.save(listOf(patientProfile)).blockingAwait()

      val bp = testData.bloodPressureMeasurement(
          patientUuid = patientUuid,
          facilityUuid = facilityUuid
      )
      bpRepository.save(listOf(bp)).blockingAwait()

      val appointment = testData.appointment(
          patientUuid = patientUuid,
          facilityUuid = facilityUuid,
          scheduledDate = LocalDate.now(clock).minusDays(1),
          status = Scheduled,
          cancelReason = null
      )
      appointmentRepository.save(listOf(appointment)).blockingAwait()
    }

    //given
    val deletedPatientId = UUID.fromString("97d05796-614c-46de-a10a-e12cf595f4ff")
    createOverdueAppointment(
        patientUuid = deletedPatientId,
        facilityUuid = facility.uuid,
        isPatientDeleted = true
    )
    val notDeletedPatientId = UUID.fromString("4e642ef2-1991-42ae-ba61-a10809c78f5d")
    createOverdueAppointment(
        patientUuid = notDeletedPatientId,
        facilityUuid = facility.uuid,
        isPatientDeleted = false
    )

    // when
    val overdueAppointments = appointmentRepository.overdueAppointments(since = LocalDate.now(clock), facility = facility).blockingFirst()

    //then
    assertThat(overdueAppointments).hasSize(1)
    assertThat(overdueAppointments.first().appointment.patientUuid).isEqualTo(notDeletedPatientId)
  }

  @Test
  fun deleted_appointments_must_be_excluded_when_loading_overdue_appointments() {
    fun createOverdueAppointment(
        patientUuid: UUID,
        facilityUuid: UUID,
        isAppointmentDeleted: Boolean
    ) {
      val patientProfile = testData.patientProfile(
          patientUuid = patientUuid,
          generatePhoneNumber = true
      )
      patientRepository.save(listOf(patientProfile)).blockingAwait()

      val bp = testData.bloodPressureMeasurement(
          patientUuid = patientUuid,
          facilityUuid = facilityUuid
      )
      bpRepository.save(listOf(bp)).blockingAwait()

      val appointment = testData.appointment(
          patientUuid = patientUuid,
          facilityUuid = facilityUuid,
          scheduledDate = LocalDate.now(clock).minusDays(1),
          status = Scheduled,
          cancelReason = null,
          deletedAt = if (isAppointmentDeleted) Instant.now() else null
      )
      appointmentRepository.save(listOf(appointment)).blockingAwait()
    }

    //given
    val patientIdWithDeletedAppointment = UUID.fromString("97d05796-614c-46de-a10a-e12cf595f4ff")
    createOverdueAppointment(
        patientUuid = patientIdWithDeletedAppointment,
        facilityUuid = facility.uuid,
        isAppointmentDeleted = true
    )
    val patientIdWithoutDeletedAppointment = UUID.fromString("4e642ef2-1991-42ae-ba61-a10809c78f5d")
    createOverdueAppointment(
        patientUuid = patientIdWithoutDeletedAppointment,
        facilityUuid = facility.uuid,
        isAppointmentDeleted = false
    )

    // when
    val overdueAppointments = appointmentRepository.overdueAppointments(since = LocalDate.now(clock), facility = facility).blockingFirst()

    //then
    assertThat(overdueAppointments).hasSize(1)
    assertThat(overdueAppointments.first().appointment.patientUuid).isEqualTo(patientIdWithoutDeletedAppointment)
  }

  @Test
  fun appointments_that_are_still_scheduled_after_the_schedule_date_should_be_fetched_as_overdue_appointments() {

    fun createAppointmentRecord(
        patientUuid: UUID,
        bpUuid: UUID,
        appointmentUuid: UUID,
        scheduleAppointmentOn: LocalDate
    ): RecordAppointment {
      val patientProfile = testData.patientProfile(
          patientUuid = patientUuid,
          generatePhoneNumber = true
      )

      val bloodPressureMeasurement = testData.bloodPressureMeasurement(
          uuid = bpUuid,
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          userUuid = userUuid,
          systolic = 120,
          diastolic = 80,
          recordedAt = Instant.parse("2018-01-01T00:00:00Z"),
          deletedAt = null
      )

      val appointment = testData.appointment(
          uuid = appointmentUuid,
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          scheduledDate = scheduleAppointmentOn,
          status = Scheduled,
          cancelReason = null,
          remindOn = null,
          agreedToVisit = null
      )

      return RecordAppointment(patientProfile, bloodPressureMeasurement, null, appointment)
    }

    // given
    val currentDate = LocalDate.parse("2018-01-05")

    val oneWeekBeforeCurrentDate = createAppointmentRecord(
        patientUuid = UUID.fromString("c5bb0ab7-516d-4c61-ac36-b806ba9bcca5"),
        bpUuid = UUID.fromString("85765883-b964-4322-a7e3-c922612b078d"),
        appointmentUuid = UUID.fromString("064a0417-d485-40f0-9659-dbb7a4efbfb7"),
        scheduleAppointmentOn = currentDate.minusDays(7)
    )

    val oneDayBeforeCurrentDate = createAppointmentRecord(
        patientUuid = UUID.fromString("7dd6a3c6-2977-45d9-bf22-f2b8929d227e"),
        bpUuid = UUID.fromString("e27345b6-0463-410d-b433-ada8adf8f6f7"),
        appointmentUuid = UUID.fromString("899a7269-01b0-4d59-9e3b-5a6cc82985d2"),
        scheduleAppointmentOn = currentDate.minusDays(1)
    )

    val onCurrentDate = createAppointmentRecord(
        patientUuid = UUID.fromString("c83a03f3-61b4-4af6-bcbf-3094ed4044a1"),
        bpUuid = UUID.fromString("2f439af6-bff9-4d85-9179-9697171863fb"),
        appointmentUuid = UUID.fromString("6419fb68-6e1d-4928-8435-777da07c54d9"),
        scheduleAppointmentOn = currentDate
    )

    val afterCurrentDate = createAppointmentRecord(
        patientUuid = UUID.fromString("fae12ba1-e958-4aaf-9802-0b4b09535469"),
        bpUuid = UUID.fromString("4b25b6bb-4279-4816-81a6-f5f325c832d4"),
        appointmentUuid = UUID.fromString("b422ca39-2090-4c24-9a4c-5a8904403a57"),
        scheduleAppointmentOn = currentDate.plusDays(1)
    )

    listOf(oneWeekBeforeCurrentDate, oneDayBeforeCurrentDate, onCurrentDate, afterCurrentDate)
        .forEach { it.save(patientRepository, bpRepository, bloodSugarRepository, appointmentRepository) }

    // when
    val overdueAppointments = appointmentRepository
        .overdueAppointments(since = currentDate, facility = facility)
        .blockingFirst()

    // then
    val expectedAppointments = listOf(oneWeekBeforeCurrentDate, oneDayBeforeCurrentDate).map(RecordAppointment::toOverdueAppointment)

    assertThat(overdueAppointments).containsExactlyElementsIn(expectedAppointments)
  }

  @Test
  fun appointments_with_reminder_dates_before_the_current_date_should_be_shown_when_fetching_overdue_appointments() {

    val currentDate = LocalDate.parse("2018-01-05")

    fun createAppointmentRecord(
        patientUuid: UUID,
        bpUuid: UUID,
        appointmentUuid: UUID,
        appointmentReminderOn: LocalDate
    ): RecordAppointment {
      val patientProfile = testData.patientProfile(
          patientUuid = patientUuid,
          generatePhoneNumber = true
      )

      val bloodPressureMeasurement = testData.bloodPressureMeasurement(
          uuid = bpUuid,
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          userUuid = userUuid,
          systolic = 120,
          diastolic = 80,
          recordedAt = Instant.parse("2018-01-01T00:00:00Z"),
          deletedAt = null
      )

      val appointment = testData.appointment(
          uuid = appointmentUuid,
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          scheduledDate = currentDate.minusWeeks(2),
          status = Scheduled,
          cancelReason = null,
          remindOn = appointmentReminderOn,
          agreedToVisit = null
      )

      return RecordAppointment(patientProfile, bloodPressureMeasurement, null, appointment)
    }

    // given
    val remindOneWeekBeforeCurrentDate = createAppointmentRecord(
        patientUuid = UUID.fromString("417c19d3-68a0-4936-bc4f-5b7c2a73ccc7"),
        bpUuid = UUID.fromString("3414fd9a-8b30-4850-9f8f-3de9305dcb6c"),
        appointmentUuid = UUID.fromString("053f2f73-b693-420c-a9c6-d8aae1c77395"),
        appointmentReminderOn = currentDate.minusWeeks(1)
    )

    val remindOneDayBeforeCurrentDate = createAppointmentRecord(
        patientUuid = UUID.fromString("0af5c909-551b-448d-988e-b00b3304f738"),
        bpUuid = UUID.fromString("6b5aed42-9e78-486a-bee2-392455993dfe"),
        appointmentUuid = UUID.fromString("e58cfd76-aaeb-42a8-8bf1-4c71614c6288"),
        appointmentReminderOn = currentDate.minusDays(1)
    )

    val remindOnCurrentDate = createAppointmentRecord(
        patientUuid = UUID.fromString("6fc5a658-4afc-473b-a062-c57849f4ade9"),
        bpUuid = UUID.fromString("ff440058-6dbc-4283-b0c3-882ee069ed6c"),
        appointmentUuid = UUID.fromString("27625873-bda2-47ff-ab2c-a664224a8d7e"),
        appointmentReminderOn = currentDate
    )

    val remindAfterCurrentDate = createAppointmentRecord(
        patientUuid = UUID.fromString("f1ddc613-a7ca-4bb4-a1a0-233672a4eb1d"),
        bpUuid = UUID.fromString("6847f8ed-8868-42a1-b962-f5b4258f224c"),
        appointmentUuid = UUID.fromString("2000fdda-8e42-4067-b7d3-38cb9e74f88b"),
        appointmentReminderOn = currentDate.plusDays(1)
    )

    listOf(remindOneWeekBeforeCurrentDate, remindOneDayBeforeCurrentDate, remindOnCurrentDate, remindAfterCurrentDate)
        .forEach { it.save(patientRepository, bpRepository, bloodSugarRepository, appointmentRepository) }

    // when
    val overdueAppointments = appointmentRepository
        .overdueAppointments(since = currentDate, facility = facility)
        .blockingFirst()

    // then
    val expectedAppointments = listOf(remindOneWeekBeforeCurrentDate, remindOneDayBeforeCurrentDate).map(RecordAppointment::toOverdueAppointment)

    assertThat(overdueAppointments).containsExactlyElementsIn(expectedAppointments)
  }

  @Test
  fun patients_without_phone_number_should_not_be_shown_when_fetching_overdue_appointments() {

    val currentDate = LocalDate.parse("2018-01-05")

    fun createAppointmentRecord(
        patientUuid: UUID,
        bpUuid: UUID,
        appointmentUuid: UUID,
        patientPhoneNumber: PatientPhoneNumber?
    ): RecordAppointment {
      val patientProfile = with(testData.patientProfile(patientUuid = patientUuid, generatePhoneNumber = false)) {
        val phoneNumbers = if (patientPhoneNumber == null) emptyList() else listOf(patientPhoneNumber.withPatientUuid(patientUuid))

        this.copy(phoneNumbers = phoneNumbers)
      }

      val bloodPressureMeasurement = testData.bloodPressureMeasurement(
          uuid = bpUuid,
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          userUuid = userUuid,
          systolic = 120,
          diastolic = 80,
          recordedAt = Instant.parse("2018-01-01T00:00:00Z"),
          deletedAt = null
      )

      val appointment = testData.appointment(
          uuid = appointmentUuid,
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          scheduledDate = LocalDate.parse("2018-01-04"),
          status = Scheduled,
          cancelReason = null,
          remindOn = null,
          agreedToVisit = null
      )

      return RecordAppointment(patientProfile, bloodPressureMeasurement, null, appointment)
    }

    // given
    val withPhoneNumber = createAppointmentRecord(
        patientUuid = UUID.fromString("417c19d3-68a0-4936-bc4f-5b7c2a73ccc7"),
        bpUuid = UUID.fromString("3414fd9a-8b30-4850-9f8f-3de9305dcb6c"),
        appointmentUuid = UUID.fromString("053f2f73-b693-420c-a9c6-d8aae1c77395"),
        patientPhoneNumber = testData.patientPhoneNumber()
    )

    val withDeletedPhoneNumber = createAppointmentRecord(
        patientUuid = UUID.fromString("0af5c909-551b-448d-988e-b00b3304f738"),
        bpUuid = UUID.fromString("6b5aed42-9e78-486a-bee2-392455993dfe"),
        appointmentUuid = UUID.fromString("e58cfd76-aaeb-42a8-8bf1-4c71614c6288"),
        patientPhoneNumber = testData.patientPhoneNumber(deletedAt = Instant.parse("2018-01-01T00:00:00Z"))
    )

    val withoutPhoneNumber = createAppointmentRecord(
        patientUuid = UUID.fromString("f1ddc613-a7ca-4bb4-a1a0-233672a4eb1d"),
        bpUuid = UUID.fromString("6847f8ed-8868-42a1-b962-f5b4258f224c"),
        appointmentUuid = UUID.fromString("2000fdda-8e42-4067-b7d3-38cb9e74f88b"),
        patientPhoneNumber = null
    )

    listOf(withPhoneNumber, withDeletedPhoneNumber, withoutPhoneNumber)
        .forEach { it.save(patientRepository, bpRepository, bloodSugarRepository, appointmentRepository) }

    // when
    val overdueAppointments = appointmentRepository
        .overdueAppointments(since = currentDate, facility = facility)
        .blockingFirst()

    // then
    val expectedAppointments = listOf(withPhoneNumber).map { it.toOverdueAppointment() }

    assertThat(overdueAppointments).containsExactlyElementsIn(expectedAppointments)
  }

  @Test
  fun patients_without_blood_pressure_should_not_be_shown_when_fetching_overdue_appointments() {

    val currentDate = LocalDate.parse("2018-01-05")

    fun createAppointmentRecord(
        patientUuid: UUID,
        bpUuid: UUID?,
        appointmentUuid: UUID
    ): RecordAppointment {
      val patientProfile = testData.patientProfile(patientUuid = patientUuid, generatePhoneNumber = true)

      val bloodPressureMeasurement = if (bpUuid != null) {
        testData.bloodPressureMeasurement(
            uuid = bpUuid,
            patientUuid = patientUuid,
            facilityUuid = facility.uuid,
            userUuid = userUuid,
            systolic = 120,
            diastolic = 80,
            recordedAt = Instant.parse("2018-01-01T00:00:00Z"),
            deletedAt = null
        )
      } else null

      val appointment = testData.appointment(
          uuid = appointmentUuid,
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          scheduledDate = LocalDate.parse("2018-01-04"),
          status = Scheduled,
          cancelReason = null,
          remindOn = null,
          agreedToVisit = null
      )

      return RecordAppointment(patientProfile, bloodPressureMeasurement, null, appointment)
    }

    // given
    val withBloodPressure = createAppointmentRecord(
        patientUuid = UUID.fromString("417c19d3-68a0-4936-bc4f-5b7c2a73ccc7"),
        bpUuid = UUID.fromString("3414fd9a-8b30-4850-9f8f-3de9305dcb6c"),
        appointmentUuid = UUID.fromString("053f2f73-b693-420c-a9c6-d8aae1c77395")
    )

    val withoutBloodPressure = createAppointmentRecord(
        patientUuid = UUID.fromString("0af5c909-551b-448d-988e-b00b3304f738"),
        bpUuid = null,
        appointmentUuid = UUID.fromString("e58cfd76-aaeb-42a8-8bf1-4c71614c6288")
    )

    listOf(withBloodPressure, withoutBloodPressure)
        .forEach { it.save(patientRepository, bpRepository, bloodSugarRepository, appointmentRepository) }

    // when
    val overdueAppointments = appointmentRepository
        .overdueAppointments(since = currentDate, facility = facility)
        .blockingFirst()

    // then
    val expectedAppointments = listOf(withBloodPressure).map(RecordAppointment::toOverdueAppointment)

    assertThat(overdueAppointments).containsExactlyElementsIn(expectedAppointments)
  }

  @Test
  fun patients_without_blood_sugars_should_not_be_shown_when_fetching_overdue_appointments() {

    val currentDate = LocalDate.parse("2018-01-05")

    fun createAppointmentRecord(
        patientUuid: UUID,
        bloodSugarUuid: UUID?,
        appointmentUuid: UUID
    ): RecordAppointment {
      val patientProfile = testData.patientProfile(patientUuid = patientUuid, generatePhoneNumber = true)

      val bloodSugarMeasurement = if (bloodSugarUuid != null) {
        testData.bloodSugarMeasurement(
            uuid = bloodSugarUuid,
            patientUuid = patientUuid,
            facilityUuid = facility.uuid,
            userUuid = userUuid,
            reading = BloodSugarReading("256", Random),
            recordedAt = Instant.parse("2018-01-01T00:00:00Z"),
            deletedAt = null
        )
      } else null

      val appointment = testData.appointment(
          uuid = appointmentUuid,
          patientUuid = patientUuid,
          facilityUuid = facility.uuid,
          scheduledDate = LocalDate.parse("2018-01-04"),
          status = Scheduled,
          cancelReason = null,
          remindOn = null,
          agreedToVisit = null
      )

      return RecordAppointment(patientProfile, null, bloodSugarMeasurement, appointment)
    }

    // given
    val withBloodSugar = createAppointmentRecord(
        patientUuid = UUID.fromString("417c19d3-68a0-4936-bc4f-5b7c2a73ccc7"),
        bloodSugarUuid = UUID.fromString("3414fd9a-8b30-4850-9f8f-3de9305dcb6c"),
        appointmentUuid = UUID.fromString("053f2f73-b693-420c-a9c6-d8aae1c77395")
    )

    val withoutBloodSugar = createAppointmentRecord(
        patientUuid = UUID.fromString("0af5c909-551b-448d-988e-b00b3304f738"),
        bloodSugarUuid = null,
        appointmentUuid = UUID.fromString("e58cfd76-aaeb-42a8-8bf1-4c71614c6288")
    )

    listOf(withBloodSugar, withoutBloodSugar)
        .forEach { it.save(patientRepository, bpRepository, bloodSugarRepository, appointmentRepository) }

    // when
    val overdueAppointments = appointmentRepository
        .overdueAppointments(since = currentDate, facility = facility)
        .blockingFirst()

    // then
    val expectedAppointments = listOf(withBloodSugar).map(RecordAppointment::toOverdueAppointment)

    assertThat(overdueAppointments).containsExactlyElementsIn(expectedAppointments)
  }

  private fun markAppointmentSyncStatusAsDone(vararg appointmentUuids: UUID) {
    appointmentRepository.setSyncStatus(appointmentUuids.toList(), DONE).blockingAwait()
  }

  private fun getAppointmentByUuid(appointmentUuid: UUID): Appointment {
    return database.appointmentDao().getOne(appointmentUuid)!!
  }

  data class RecordAppointment(
      val patientProfile: PatientProfile,
      val bloodPressureMeasurement: BloodPressureMeasurement?,
      val bloodSugarMeasurement: BloodSugarMeasurement?,
      val appointment: Appointment
  ) {
    fun save(
        patientRepository: PatientRepository,
        bloodPressureRepository: BloodPressureRepository,
        bloodSugarRepository: BloodSugarRepository,
        appointmentRepository: AppointmentRepository
    ) {
      val saveBp = if (bloodPressureMeasurement != null) {
        bloodPressureRepository.save(listOf(bloodPressureMeasurement))
      } else Completable.complete()

      val saveBloodSugar = if (bloodSugarMeasurement != null) {
        bloodSugarRepository.save(listOf(bloodSugarMeasurement))
      } else Completable.complete()

      patientRepository.save(listOf(patientProfile))
          .andThen(saveBp)
          .andThen(saveBloodSugar)
          .andThen(appointmentRepository.save(listOf(appointment)))
          .blockingAwait()
    }

    fun toOverdueAppointment(): OverdueAppointment {
      if (bloodPressureMeasurement == null && bloodSugarMeasurement == null) {
        throw AssertionError("Need a Blood Pressure Measurement or Blood Sugar Measurement to create an Overdue Appointment")
      } else {
        val patientLastSeen = when {
          bloodPressureMeasurement == null -> bloodSugarMeasurement!!.recordedAt
          bloodSugarMeasurement == null -> bloodPressureMeasurement.recordedAt
          else -> maxOf(bloodPressureMeasurement.recordedAt, bloodSugarMeasurement.recordedAt)
        }
        return OverdueAppointment(
            fullName = patientProfile.patient.fullName,
            gender = patientProfile.patient.gender,
            dateOfBirth = patientProfile.patient.dateOfBirth,
            age = patientProfile.patient.age,
            appointment = appointment,
            phoneNumber = patientProfile.phoneNumbers.first(),
            isAtHighRisk = false,
            patientLastSeen = patientLastSeen
        )
      }
    }
  }
}

private fun PatientPhoneNumber.withPatientUuid(uuid: UUID): PatientPhoneNumber {
  return this.copy(patientUuid = uuid)
}
