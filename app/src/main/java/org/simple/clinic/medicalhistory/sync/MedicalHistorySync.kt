package org.simple.clinic.medicalhistory.sync

import com.f2prateek.rx.preferences2.Preference
import io.reactivex.Completable
import io.reactivex.Single
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.MedicalHistory
import org.simple.clinic.medicalhistory.MedicalHistoryRepository
import org.simple.clinic.sync.ModelSync
import org.simple.clinic.sync.SyncConfig
import org.simple.clinic.sync.SyncCoordinator
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.Optional
import javax.inject.Inject
import javax.inject.Named

class MedicalHistorySync @Inject constructor(
    private val syncCoordinator: SyncCoordinator,
    private val repository: MedicalHistoryRepository,
    private val api: MedicalHistorySyncApi,
    private val userSession: UserSession,
    @Named("last_medicalhistory_pull_token") private val lastPullToken: Preference<Optional<String>>,
    @Named("sync_config_frequent") private val configProvider: Single<SyncConfig>
) : ModelSync {

  private fun canSyncData() = userSession.canSyncData().firstOrError()

  override fun sync(): Completable =
      canSyncData()
          .flatMapCompletable { canSync ->
            if (canSync) {
              Completable.mergeArrayDelayError(push(), pull())

            } else {
              Completable.complete()
            }
          }

  override fun push(): Completable {
    return syncCoordinator.push(repository, pushNetworkCall = { api.push(toRequest(it)) })
  }

  override fun pull(): Completable {
    return configProvider
        .map { it.batchSize }
        .flatMapCompletable { batchSize ->
          syncCoordinator.pull(repository, lastPullToken, batchSize) { api.pull(batchSize.numberOfRecords, it) }
        }
  }

  override fun syncConfig() = configProvider

  private fun toRequest(histories: List<MedicalHistory>): MedicalHistoryPushRequest {
    val payloads = histories
        .map {
          it.run {
            MedicalHistoryPayload(
                uuid = uuid,
                patientUuid = patientUuid,
                diagnosedWithHypertension = diagnosedWithHypertension,
                isOnTreatmentForHypertension = Answer.Unanswered,
                hasHadHeartAttack = hasHadHeartAttack,
                hasHadStroke = hasHadStroke,
                hasHadKidneyDisease = hasHadKidneyDisease,
                hasDiabetes = diagnosedWithDiabetes,
                hasHypertension = diagnosedWithHypertension,
                createdAt = createdAt,
                updatedAt = updatedAt,
                deletedAt = deletedAt)
          }
        }
    return MedicalHistoryPushRequest(payloads)
  }
}
