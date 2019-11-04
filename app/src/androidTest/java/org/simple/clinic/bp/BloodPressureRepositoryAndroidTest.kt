package org.simple.clinic.bp

import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.encounter.EncounterRepository
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.rules.LocalAuthenticationRule
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.TestUtcClock
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.Month
import org.threeten.bp.temporal.ChronoUnit.DAYS
import java.util.UUID
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class BloodPressureRepositoryAndroidTest {

  @Inject
  lateinit var clock: TestUtcClock

  @Inject
  lateinit var appDatabase: AppDatabase

  @Inject
  lateinit var repository: BloodPressureRepository

  @Inject
  lateinit var encounterRepository: EncounterRepository

  @Inject
  lateinit var testData: TestData

  private val authenticationRule = LocalAuthenticationRule()

  private val rxErrorsRule = RxErrorsRule()

  @get:Rule
  val ruleChain = RuleChain
      .outerRule(authenticationRule)
      .around(rxErrorsRule)!!

  @Before
  fun setUp() {
    TestClinicApp.appComponent().inject(this)
    clock.setDate(LocalDate.of(2000, Month.JANUARY, 1))
  }

  @Test
  fun saving_a_blood_pressure_with_an_older_recorded_time_should_set_the_updated_time_to_the_current_time() {
    val now = Instant.now(clock)
    val oneWeek = Duration.ofDays(7L)
    clock.advanceBy(oneWeek)

    val savedBloodPressure = repository
        .saveMeasurement(
            patientUuid = UUID.randomUUID(),
            systolic = 120,
            diastolic = 80,
            loggedInUser = testData.qaUser(),
            currentFacility = testData.qaFacility(),
            recordedAt = now)
        .blockingGet()

    assertThat(savedBloodPressure.recordedAt).isEqualTo(now)
    assertThat(savedBloodPressure.updatedAt).isEqualTo(now.plus(oneWeek))
  }

  @Test
  fun updating_a_blood_pressure_should_update_it_correctly() {
    val bloodPressure = testData.bloodPressureMeasurement(
        systolic = 120,
        diastolic = 80,
        createdAt = Instant.now(clock),
        updatedAt = Instant.now(clock),
        recordedAt = Instant.now(clock),
        syncStatus = SyncStatus.DONE)

    encounterRepository.saveBloodPressureMeasurement(bloodPressure).blockingAwait()

    val durationToAdvanceBy = Duration.ofMinutes(15L)
    clock.advanceBy(durationToAdvanceBy)

    repository.updateMeasurement(bloodPressure.copy(systolic = 130, diastolic = 90)).blockingAwait()

    val expected = bloodPressure.copy(
        systolic = 130,
        diastolic = 90,
        updatedAt = bloodPressure.updatedAt.plus(durationToAdvanceBy),
        syncStatus = SyncStatus.PENDING
    )

    assertThat(appDatabase.bloodPressureDao().getOne(bloodPressure.uuid)!!).isEqualTo(expected)
  }

  @Test
  fun when_fetching_newest_blood_pressure_the_list_should_be_ordered_by_recorded_at() {
    val patientUuid = UUID.randomUUID()
    val bloodPressure1 = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        recordedAt = Instant.now(clock))

    val bloodPressure2 = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        recordedAt = Instant.now(clock).plus(1, DAYS))

    val bloodPressure3 = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        recordedAt = Instant.now(clock).minus(1, DAYS))

    val bloodPressure4 = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        recordedAt = Instant.now(clock).plusMillis(1000))

    val bloodPressure5 = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        recordedAt = Instant.now(clock).minus(10, DAYS))

    appDatabase.bloodPressureDao().save(listOf(bloodPressure1, bloodPressure2, bloodPressure3, bloodPressure4, bloodPressure5))

    val bpMeasurements = repository.newestMeasurementsForPatient(patientUuid, 4).blockingFirst()

    assertThat(bpMeasurements).isEqualTo(listOf(bloodPressure2, bloodPressure4, bloodPressure1, bloodPressure3))
  }

  @Test
  fun deleted_blood_pressures_should_not_be_included_when_fetching_newest_blood_pressures() {
    val patientUuid = UUID.randomUUID()
    val bloodPressure1 = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        recordedAt = Instant.now(clock))

    val bloodPressure2 = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        recordedAt = Instant.now(clock).plus(1, DAYS),
        deletedAt = Instant.now(clock).plus(3, DAYS))

    val bloodPressure3 = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        recordedAt = Instant.now(clock).minus(1, DAYS))

    val bloodPressure4 = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        recordedAt = Instant.now(clock).plusMillis(1000),
        deletedAt = Instant.now(clock))

    val bloodPressure5 = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        recordedAt = Instant.now(clock).minus(10, DAYS))

    appDatabase.bloodPressureDao().save(listOf(bloodPressure1, bloodPressure2, bloodPressure3, bloodPressure4, bloodPressure5))

    val bpMeasurements = repository.newestMeasurementsForPatient(patientUuid, 4).blockingFirst()

    assertThat(bpMeasurements).isEqualTo(listOf(bloodPressure1, bloodPressure3, bloodPressure5))
  }

  @Test
  fun marking_a_blood_pressure_as_deleted_should_work_correctly() {
    val now = Instant.now(clock)
    val bloodPressure = testData.bloodPressureMeasurement(
        recordedAt = now,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
        syncStatus = SyncStatus.DONE)

    encounterRepository.saveBloodPressureMeasurement(bloodPressure).blockingAwait()

    val durationToAdvanceBy = Duration.ofMinutes(15L)
    clock.advanceBy(durationToAdvanceBy)

    repository.markBloodPressureAsDeleted(bloodPressure).blockingAwait()

    val timeAtWhichBpWasDeleted = now.plus(durationToAdvanceBy)
    val expected = bloodPressure.copy(
        updatedAt = timeAtWhichBpWasDeleted,
        deletedAt = timeAtWhichBpWasDeleted,
        syncStatus = SyncStatus.PENDING)

    val savedBloodPressure = appDatabase.bloodPressureDao().getOne(expected.uuid)!!
    val encounter = appDatabase.encountersDao().getOne(expected.encounterUuid)!!

    assertThat(savedBloodPressure).isEqualTo(expected)
    assertThat(encounter.deletedAt).isEqualTo(timeAtWhichBpWasDeleted)
  }

  @Test
  fun getting_the_blood_pressure_count_for_a_patient_should_work_correctly() {
    val patientUuidWithOnlyDeletedBloodPressures = UUID.randomUUID()
    val patientUuidWithBloodPressures = UUID.randomUUID()

    val now = Instant.now(clock)
    val bpsForPatientWithOnlyDeletedBloodPressures = listOf(
        testData.bloodPressureMeasurement(patientUuid = patientUuidWithOnlyDeletedBloodPressures, deletedAt = now),
        testData.bloodPressureMeasurement(patientUuid = patientUuidWithOnlyDeletedBloodPressures, deletedAt = now),
        testData.bloodPressureMeasurement(patientUuid = patientUuidWithOnlyDeletedBloodPressures, deletedAt = now)
    )

    val bpsForPatientWithBloodPressures = listOf(
        testData.bloodPressureMeasurement(patientUuid = patientUuidWithBloodPressures, deletedAt = now),
        testData.bloodPressureMeasurement(patientUuid = patientUuidWithBloodPressures),
        testData.bloodPressureMeasurement(patientUuid = patientUuidWithBloodPressures, deletedAt = now)
    )

    appDatabase.bloodPressureDao().save(bpsForPatientWithOnlyDeletedBloodPressures + bpsForPatientWithBloodPressures)
    assertThat(appDatabase.bloodPressureDao().count().blockingFirst()).isEqualTo(6)

    assertThat(repository.bloodPressureCount(patientUuidWithOnlyDeletedBloodPressures).blockingFirst()).isEqualTo(0)
    assertThat(repository.bloodPressureCount(patientUuidWithBloodPressures).blockingFirst()).isEqualTo(1)
  }

  @Test
  fun querying_whether_blood_pressures_for_patient_have_change_should_work_as_expected() {
    fun haveBpsForPatientChangedSince(patientUuid: UUID, since: Instant): Boolean {
      return repository.haveBpsForPatientChangedSince(patientUuid, since).blockingFirst()
    }

    fun setBpSyncStatusToDone(bpUuid: UUID) {
      repository.setSyncStatus(listOf(bpUuid), SyncStatus.DONE).blockingAwait()
    }

    val patientUuid = UUID.randomUUID()
    val now = Instant.now(clock)
    val oneSecondEarlier = now.minus(Duration.ofSeconds(1))
    val fiftyNineSecondsLater = now.plus(Duration.ofSeconds(59))
    val oneMinuteLater = now.plus(Duration.ofMinutes(1))

    val bp1ForPatient = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        syncStatus = SyncStatus.PENDING,
        updatedAt = now
    )
    val bp2ForPatient = testData.bloodPressureMeasurement(
        patientUuid = patientUuid,
        syncStatus = SyncStatus.PENDING,
        updatedAt = oneMinuteLater
    )
    val bpForSomeOtherPatient = testData.bloodPressureMeasurement(
        patientUuid = UUID.randomUUID(),
        syncStatus = SyncStatus.PENDING,
        updatedAt = now
    )

    repository.save(listOf(bp1ForPatient, bp2ForPatient, bpForSomeOtherPatient)).blockingAwait()
    assertThat(haveBpsForPatientChangedSince(patientUuid, oneSecondEarlier)).isTrue()
    assertThat(haveBpsForPatientChangedSince(patientUuid, now)).isTrue()
    assertThat(haveBpsForPatientChangedSince(patientUuid, fiftyNineSecondsLater)).isTrue()
    assertThat(haveBpsForPatientChangedSince(patientUuid, oneMinuteLater)).isFalse()

    setBpSyncStatusToDone(bp2ForPatient.uuid)
    assertThat(haveBpsForPatientChangedSince(patientUuid, fiftyNineSecondsLater)).isFalse()
    assertThat(haveBpsForPatientChangedSince(patientUuid, oneSecondEarlier)).isTrue()

    setBpSyncStatusToDone(bp1ForPatient.uuid)
    assertThat(haveBpsForPatientChangedSince(patientUuid, oneSecondEarlier)).isFalse()
    assertThat(haveBpsForPatientChangedSince(bpForSomeOtherPatient.patientUuid, oneSecondEarlier)).isTrue()

    setBpSyncStatusToDone(bpForSomeOtherPatient.uuid)
    assertThat(haveBpsForPatientChangedSince(bpForSomeOtherPatient.patientUuid, oneSecondEarlier)).isFalse()
  }
}
