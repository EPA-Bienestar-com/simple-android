package org.simple.clinic.medicalhistory.sync

import io.reactivex.Single
import org.simple.clinic.sync.DataPushResponse
import org.threeten.bp.Instant
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface MedicalHistorySyncApiV1 {

  @POST("v1/medical_histories/sync")
  fun push(
      @Body body: MedicalHistoryPushRequest
  ): Single<DataPushResponse>

  @GET("v1/medical_histories/sync")
  fun pull(
      @Query("limit") recordsToPull: Int,
      @Query("processed_since") lastPullTimestamp: Instant? = null
  ): Single<MedicalHistoryPullResponse>
}
