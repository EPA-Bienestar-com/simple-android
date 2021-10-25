package org.simple.clinic.storage.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.assertValues
import org.simple.clinic.patient.PatientProfile
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.businessid.BusinessId
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.string
import java.util.UUID
import javax.inject.Inject

class Migration57AndroidTest : BaseDatabaseMigrationTest(56, 57) {

  @Inject
  lateinit var testData: TestData

  @Before
  override fun setUp() {
    super.setUp()
    TestClinicApp.appComponent().inject(this)
  }

  @Suppress("IllegalIdentifier")
  @Test
  fun migrating_to_57_should_mark_all_patients_with_blank_identifiers_as_sync_pending() {
    // given
    val uuid_of_synced_patient_without_bangladesh_ID = UUID.fromString("1c36ac74-a6ae-4ed2-afb1-42175e3625f9")
    val synced_patient_without_bangladesh_ID = testData
        .patientProfile(
            patientUuid = uuid_of_synced_patient_without_bangladesh_ID,
            generatePhoneNumber = false,
            businessId = testData.businessId(
                uuid = UUID.fromString("3fff1513-6906-40ff-bbf7-5ab901c5eaa4"),
                patientUuid = uuid_of_synced_patient_without_bangladesh_ID,
                meta = ""
            )
        )
        .withSyncStatus(SyncStatus.DONE)

    val uuid_of_sync_pending_patient_without_bangladesh_ID = UUID.fromString("0713baa6-0db1-4d38-a502-03dc4ee97771")
    val sync_pending_patient_without_bangladesh_ID = testData
        .patientProfile(
            patientUuid = uuid_of_sync_pending_patient_without_bangladesh_ID,
            generatePhoneNumber = false,
            businessId = testData.businessId(
                uuid = UUID.fromString("4b6daae6-2fe1-4a40-bd94-621cf3da9822"),
                patientUuid = uuid_of_sync_pending_patient_without_bangladesh_ID,
                meta = ""
            )
        )
        .withSyncStatus(SyncStatus.PENDING)


    val uuid_of_first_sync_failed_patient_with_blank_bangladesh_ID = UUID.fromString("40427b8e-e6ba-424d-a91c-c282fcc5061e")
    val first_sync_failed_patient_with_blank_bangladesh_ID = testData
        .patientProfile(
            patientUuid = uuid_of_first_sync_failed_patient_with_blank_bangladesh_ID,
            generatePhoneNumber = false,
            businessId = testData.businessId(
                uuid = UUID.fromString("8455663a-7ddc-44fc-bacd-a801d85c3632"),
                patientUuid = uuid_of_first_sync_failed_patient_with_blank_bangladesh_ID,
                meta = ""
            )
        )
        .withBangladeshNationalId(UUID.fromString("5f84e1e1-7c34-4005-91b1-1fddc9ad0cd0"), id = "")
        .withSyncStatus(SyncStatus.INVALID)

    val uuid_of_sync_pending_patient_with_blank_bangladesh_ID = UUID.fromString("168f9b6b-e831-49de-80ae-65b3f7d6d688")
    val sync_pending_patient_with_blank_bangladesh_ID = testData
        .patientProfile(
            patientUuid = uuid_of_sync_pending_patient_with_blank_bangladesh_ID,
            generatePhoneNumber = false,
            businessId = testData.businessId(
                uuid = UUID.fromString("0caa9b1a-c85f-4437-b535-08e727760ce0"),
                patientUuid = uuid_of_sync_pending_patient_with_blank_bangladesh_ID,
                meta = ""
            )
        )
        .withBangladeshNationalId(UUID.fromString("46824088-0b57-41d2-a6c3-86677d6940ec"), id = "")
        .withSyncStatus(SyncStatus.PENDING)

    val uuid_of_sync_failed_patient_without_bangladesh_ID = UUID.fromString("822007ff-a98c-4f45-8847-c8ff92693ba3")
    val sync_failed_patient_without_bangladesh_ID = testData
        .patientProfile(
            patientUuid = uuid_of_sync_failed_patient_without_bangladesh_ID,
            generatePhoneNumber = false,
            businessId = testData.businessId(
                uuid = UUID.fromString("faaa15e9-59c6-477f-a595-c0332930e401"),
                patientUuid = uuid_of_sync_failed_patient_without_bangladesh_ID,
                meta = ""
            )
        )
        .withSyncStatus(SyncStatus.INVALID)

    val uuid_of_second_sync_failed_patient_with_blank_bangladesh_ID = UUID.fromString("f01d777b-93ad-4f2b-ada9-89736ed86a2e")
    val second_sync_failed_patient_with_blank_bangladesh_ID = testData
        .patientProfile(
            patientUuid = uuid_of_second_sync_failed_patient_with_blank_bangladesh_ID,
            generatePhoneNumber = false,
            businessId = testData.businessId(
                uuid = UUID.fromString("80ea8ba3-57d3-4b9b-af34-8fa3a5c73f95"),
                patientUuid = uuid_of_second_sync_failed_patient_with_blank_bangladesh_ID,
                meta = ""
            )
        )
        .withBangladeshNationalId(UUID.fromString("db67eb0a-a712-4935-85f7-0e7c59afe117"), id = "")
        .withSyncStatus(SyncStatus.INVALID)

    val uuid_of_synced_patient_with_non_blank_bangladesh_ID = UUID.fromString("fb7bb116-4057-4970-a0b5-bec437655cc6")
    val synced_patient_with_non_blank_bangladesh_ID = testData
        .patientProfile(
            patientUuid = uuid_of_synced_patient_with_non_blank_bangladesh_ID,
            generatePhoneNumber = false,
            businessId = testData.businessId(
                uuid = UUID.fromString("4107567f-7c6f-4e83-bcc9-66f1238823e6"),
                patientUuid = uuid_of_synced_patient_with_non_blank_bangladesh_ID,
                meta = ""
            )
        )
        .withBangladeshNationalId(UUID.fromString("4362679e-2950-46e9-9e19-79db50ab6dd7"), id = "123456abcd")
        .withSyncStatus(SyncStatus.DONE)

    before.savePatientProfile(synced_patient_without_bangladesh_ID)
    before.savePatientProfile(sync_pending_patient_without_bangladesh_ID)
    before.savePatientProfile(first_sync_failed_patient_with_blank_bangladesh_ID)
    before.savePatientProfile(sync_pending_patient_with_blank_bangladesh_ID)
    before.savePatientProfile(sync_failed_patient_without_bangladesh_ID)
    before.savePatientProfile(second_sync_failed_patient_with_blank_bangladesh_ID)
    before.savePatientProfile(synced_patient_with_non_blank_bangladesh_ID)

    // then
    after.assertPatient(synced_patient_without_bangladesh_ID.patient)
    after.assertPatient(sync_pending_patient_without_bangladesh_ID.patient)
    after.assertPatient(first_sync_failed_patient_with_blank_bangladesh_ID.withSyncStatus(SyncStatus.PENDING).patient)
    after.assertPatient(sync_pending_patient_with_blank_bangladesh_ID.patient)
    after.assertPatient(sync_failed_patient_without_bangladesh_ID.patient)
    after.assertPatient(second_sync_failed_patient_with_blank_bangladesh_ID.withSyncStatus(SyncStatus.PENDING).patient)
    after.assertPatient(synced_patient_with_non_blank_bangladesh_ID.patient)
  }

  @Suppress("IllegalIdentifier")
  @Test
  fun migrating_to_57_should_delete_all_blank_identifiers() {
    // given
    val uuid_of_patient_without_bangladesh_ID = UUID.fromString("1c36ac74-a6ae-4ed2-afb1-42175e3625f9")
    val patient_without_bangladesh_ID = testData
        .patientProfile(
            patientUuid = uuid_of_patient_without_bangladesh_ID,
            generatePhoneNumber = false,
            businessId = testData.businessId(
                uuid = UUID.fromString("3fff1513-6906-40ff-bbf7-5ab901c5eaa4"),
                patientUuid = uuid_of_patient_without_bangladesh_ID,
                meta = ""
            )
        )

    val uuid_of_patient_with_blank_bangladesh_ID = UUID.fromString("ed8df359-3965-4de1-9a25-d00533c1d292")
    val patient_with_blank_bangladesh_ID = testData
        .patientProfile(
            patientUuid = uuid_of_patient_with_blank_bangladesh_ID,
            generatePhoneNumber = false,
            businessId = testData.businessId(
                uuid = UUID.fromString("c5345a7a-fdcc-45e9-87f9-525856123382"),
                patientUuid = uuid_of_patient_with_blank_bangladesh_ID,
                meta = ""
            )
        )
        .withBangladeshNationalId(UUID.fromString("46824088-0b57-41d2-a6c3-86677d6940ec"), id = "")
    val expected_business_IDs_after_migration_for_patient_with_blank_bangladesh_ID = listOf(patient_with_blank_bangladesh_ID.businessIds[0])

    val uuid_of_patient_with_multiple_bangladesh_IDs = UUID.fromString("0c4f3c02-c17f-45f1-822b-6e79c5a21ccf")
    val patient_with_multiple_bangladesh_IDs = testData
        .patientProfile(
            patientUuid = uuid_of_patient_with_multiple_bangladesh_IDs,
            generatePhoneNumber = false,
            businessId = testData.businessId(
                uuid = UUID.fromString("8da804d9-6624-46ca-b007-dc94bce5d59c"),
                patientUuid = uuid_of_patient_with_multiple_bangladesh_IDs,
                meta = ""
            )
        )
        .withBangladeshNationalId(UUID.fromString("9a674529-4ccd-4eb0-a290-d49056721587"), id = "123456abcd")
        .withBangladeshNationalId(UUID.fromString("c2572fd7-e42a-497e-9c19-c2272924476f"), id = "")
    val expected_business_IDs_after_migration_for_patient_with_multiple_bangladesh_IDs = listOf(
        patient_with_multiple_bangladesh_IDs.businessIds[0],
        patient_with_multiple_bangladesh_IDs.businessIds[1]
    )

    before.savePatientProfile(patient_without_bangladesh_ID)
    before.savePatientProfile(patient_with_blank_bangladesh_ID)
    before.savePatientProfile(patient_with_multiple_bangladesh_IDs)

    // then
    after.assertBusinessIdsForPatient(
        patientUuid = uuid_of_patient_without_bangladesh_ID,
        expectedBusinessIds = patient_without_bangladesh_ID.businessIds
    )
    after.assertBusinessIdsForPatient(
        patientUuid = uuid_of_patient_with_blank_bangladesh_ID,
        expectedBusinessIds = expected_business_IDs_after_migration_for_patient_with_blank_bangladesh_ID
    )
    after.assertBusinessIdsForPatient(
        patientUuid = uuid_of_patient_with_multiple_bangladesh_IDs,
        expectedBusinessIds = expected_business_IDs_after_migration_for_patient_with_multiple_bangladesh_IDs
    )
  }

  private fun PatientProfile.withBangladeshNationalId(uuid: UUID, id: String): PatientProfile {
    val bangladeshNationalId = testData.businessId(
        uuid = uuid,
        patientUuid = this.patientUuid,
        identifier = Identifier(id, Identifier.IdentifierType.BangladeshNationalId),
        meta = "meta"
    )
    return this.copy(businessIds = this.businessIds + bangladeshNationalId)
  }

  private fun SupportSQLiteDatabase.assertBusinessIdsForPatient(
      patientUuid: UUID,
      expectedBusinessIds: List<BusinessId>
  ) {
    val businessIdMap = expectedBusinessIds.associateBy { it.uuid }

    query(""" SELECT * FROM "BusinessId" WHERE "patientUuid" = '$patientUuid' """).use { cursor ->
      assertThat(cursor.count).isEqualTo(expectedBusinessIds.size)

      generateSequence { cursor.takeIf { it.moveToNext() } }
          .iterator()
          .forEach {
            val businessIdUuid = UUID.fromString(it.string("uuid"))
            val businessId = businessIdMap.getValue(businessIdUuid)

            with(businessId) {
              it.assertValues(mapOf(
                  "uuid" to uuid,
                  "patientUuid" to patientUuid,
                  "identifier" to identifier.value,
                  "identifierType" to Identifier.IdentifierType.TypeAdapter.knownMappings[identifier.type],
                  "metaVersion" to BusinessId.MetaDataVersion.TypeAdapter.knownMappings[metaDataVersion],
                  "meta" to metaData,
                  "createdAt" to createdAt,
                  "updatedAt" to updatedAt,
                  "deletedAt" to deletedAt
              ))
            }
          }
    }
  }
}
