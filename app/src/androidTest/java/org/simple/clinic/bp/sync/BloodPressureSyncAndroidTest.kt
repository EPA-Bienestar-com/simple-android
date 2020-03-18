package org.simple.clinic.bp.sync

import androidx.test.runner.AndroidJUnit4
import com.f2prateek.rx.preferences2.Preference
import io.reactivex.Single
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.simple.clinic.rules.ServerAuthenticationRule
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.facility.Facility
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.sync.BaseSyncCoordinatorAndroidTest
import org.simple.clinic.sync.BatchSize
import org.simple.clinic.sync.SyncConfig
import org.simple.clinic.sync.SyncGroup
import org.simple.clinic.sync.SyncInterval
import org.simple.clinic.user.User
import org.simple.clinic.util.Optional
import org.simple.clinic.util.RxErrorsRule
import javax.inject.Inject
import javax.inject.Named

@RunWith(AndroidJUnit4::class)
class BloodPressureSyncAndroidTest : BaseSyncCoordinatorAndroidTest<BloodPressureMeasurement, BloodPressureMeasurementPayload>() {

  @Inject
  lateinit var repository: BloodPressureRepository

  @Inject
  @field:Named("last_bp_pull_token")
  lateinit var lastPullToken: Preference<Optional<String>>

  @Inject
  lateinit var syncApi: BloodPressureSyncApi

  @Inject
  lateinit var bpSync: BloodPressureSync

  @Inject
  lateinit var testData: TestData

  @Inject
  lateinit var user: User

  @Inject
  lateinit var facility: Facility

  private val configProvider = Single.just(SyncConfig(
      syncInterval = SyncInterval.FREQUENT,
      batchSize = BatchSize.VERY_SMALL,
      syncGroup = SyncGroup.FREQUENT))

  private val authenticationRule = ServerAuthenticationRule()

  private val rxErrorsRule = RxErrorsRule()

  @get:Rule
  val ruleChain = RuleChain
      .outerRule(authenticationRule)
      .around(rxErrorsRule)!!

  @Before
  fun setUp() {
    TestClinicApp.appComponent().inject(this)
  }

  override fun push() = bpSync.push()

  override fun pull() = bpSync.pull()

  override fun repository() = repository

  override fun generateRecord(syncStatus: SyncStatus) = testData.bloodPressureMeasurement(
      syncStatus = syncStatus,
      userUuid = user.uuid,
      facilityUuid = facility.uuid
  )

  override fun generatePayload() = testData.bpPayload(
      userUuid = user.uuid,
      facilityUuid = facility.uuid
  )

  override fun lastPullToken(): Preference<Optional<String>> = lastPullToken

  override fun pushNetworkCall(payloads: List<BloodPressureMeasurementPayload>) = syncApi.push(BloodPressurePushRequest(payloads))

  override fun batchSize(): BatchSize = configProvider.blockingGet().batchSize
}
