package org.simple.clinic.drugs

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
import org.simple.clinic.facility.Facility
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.rules.LocalAuthenticationRule
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.Rules
import org.simple.clinic.util.TestUtcClock
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.Month
import java.util.UUID
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class PrescriptionRepositoryAndroidTest {

  @Inject
  lateinit var clock: TestUtcClock

  @Inject
  lateinit var database: AppDatabase

  @Inject
  lateinit var repository: PrescriptionRepository

  @Inject
  lateinit var userSession: UserSession

  @Inject
  lateinit var testData: TestData

  @Inject
  lateinit var facility: Facility

  @get:Rule
  val rules: RuleChain = Rules
      .global()
      .around(LocalAuthenticationRule())

  @Before
  fun setUp() {
    TestClinicApp.appComponent().inject(this)
    clock.setDate(LocalDate.of(2000, Month.JANUARY, 1))
  }

  @Test
  fun prescriptions_for_a_patient_should_exclude_soft_deleted_prescriptions() {
    database.facilityDao().save(listOf(testData.facility(uuid = facility.uuid, syncStatus = SyncStatus.DONE)))

    val addressUuid = UUID.randomUUID()
    database.addressDao().save(testData.patientAddress(uuid = addressUuid))

    val patientUuid = UUID.randomUUID()
    database.patientDao().save(testData.patient(uuid = patientUuid, addressUuid = addressUuid))

    val protocolUuid = UUID.randomUUID()
    val amlodipine5mg = testData.protocolDrug(name = "Amlodipine", dosage = "5mg", protocolUuid = protocolUuid)
    val amlodipine10mg = testData.protocolDrug(name = "Amlodipine", dosage = "10mg", protocolUuid = protocolUuid)

    repository.savePrescription(patientUuid, amlodipine5mg, facility).blockingAwait()

    val savedPrescriptions = repository.newestPrescriptionsForPatient(patientUuid).blockingFirst()
    assertThat(savedPrescriptions).hasSize(1)

    repository.savePrescription(patientUuid, amlodipine10mg, facility)
        .andThen(repository.softDeletePrescription(savedPrescriptions.first().uuid))
        .blockingAwait()

    val savedPrescriptionsAfterDelete = repository.newestPrescriptionsForPatient(patientUuid).blockingFirst()
    assertThat(savedPrescriptionsAfterDelete).hasSize(1)
  }

  @Test
  fun soft_delete_prescription_should_update_timestamp_and_sync_status() {
    val patientUUID = UUID.randomUUID()

    val amlodipine5mg = testData.protocolDrug(name = "Amlodipine", dosage = "5mg")
    repository.savePrescription(patientUUID, amlodipine5mg, facility).blockingAwait()

    val savedPrescriptions = repository.newestPrescriptionsForPatient(patientUUID).blockingFirst()
    assertThat(savedPrescriptions).hasSize(1)

    val prescription = savedPrescriptions[0]
    repository.softDeletePrescription(prescription.uuid).blockingAwait()

    val softDeletedPrescription = database.prescriptionDao().getOne(prescription.uuid)!!

    assertThat(softDeletedPrescription.updatedAt).isGreaterThan(prescription.updatedAt)
    assertThat(softDeletedPrescription.createdAt).isEqualTo(prescription.createdAt)
    assertThat(softDeletedPrescription.syncStatus).isEqualTo(SyncStatus.PENDING)
  }

  @Test
  fun prescriptions_should_be_overridable() {
    val prescription = testData.prescription(name = "Churro")
    database.prescriptionDao().save(listOf(prescription))

    val correctedPrescription = prescription.copy(name = "Amlodipine")
    database.prescriptionDao().save(listOf(correctedPrescription))

    val storedPrescription = database.prescriptionDao().getOne(correctedPrescription.uuid)!!
    assertThat(storedPrescription.name).isEqualTo(correctedPrescription.name)
  }

  @Test
  fun updating_a_prescription_should_update_it_correctly() {
    val prescription = testData.prescription(
        name = "Atenolol",
        createdAt = Instant.now(clock),
        updatedAt = Instant.now(clock),
        syncStatus = SyncStatus.DONE)
    database.prescriptionDao().save(listOf(prescription))

    val correctedPrescription = prescription.copy(name = "Amlodipine")

    val durationToAdvanceBy = Duration.ofMinutes(15L)
    clock.advanceBy(durationToAdvanceBy)

    repository.updatePrescription(correctedPrescription).blockingAwait()

    val expected = prescription.copy(
        name = "Amlodipine",
        updatedAt = prescription.updatedAt.plus(durationToAdvanceBy),
        syncStatus = SyncStatus.PENDING
    )

    val storedPrescription = database.prescriptionDao().getOne(correctedPrescription.uuid)!!
    assertThat(storedPrescription).isEqualTo(expected)
  }
}
