package org.simple.clinic.patient

import android.support.test.runner.AndroidJUnit4
import com.f2prateek.rx.preferences2.Preference
import org.junit.Before
import org.junit.runner.RunWith
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.patient.sync.PatientPayload
import org.simple.clinic.patient.sync.PatientPushRequest
import org.simple.clinic.patient.sync.PatientSync
import org.simple.clinic.patient.sync.PatientSyncApiV1
import org.simple.clinic.sync.BaseSyncCoordinatorAndroidTest
import org.simple.clinic.util.Optional
import org.threeten.bp.Instant
import javax.inject.Inject
import javax.inject.Named

@RunWith(AndroidJUnit4::class)
class PatientSyncAndroidTest : BaseSyncCoordinatorAndroidTest<PatientProfile, PatientPayload>() {

  @Inject
  lateinit var repository: PatientRepository

  @Inject
  @field:Named("last_patient_pull_timestamp")
  lateinit var lastPullTimestamp: Preference<Optional<Instant>>

  @Inject
  lateinit var sync: PatientSync

  @Inject
  lateinit var syncApi: PatientSyncApiV1

  @Inject
  lateinit var testData: TestData

  @Before
  fun setUp() {
    TestClinicApp.appComponent().inject(this)
  }

  override fun push() = sync.push()

  override fun pull() = sync.pull()

  override fun repository() = repository

  override fun generateRecord(syncStatus: SyncStatus) = testData.patientProfile(syncStatus = syncStatus)

  override fun generatePayload() = testData.patientPayload()

  override fun lastPullTimestamp() = lastPullTimestamp

  override fun pushNetworkCall(payloads: List<PatientPayload>) = syncApi.push(PatientPushRequest(payloads))
}
