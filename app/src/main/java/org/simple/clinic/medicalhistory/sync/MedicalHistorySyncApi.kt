package org.simple.clinic.medicalhistory.sync

import io.reactivex.Single
import org.simple.clinic.sync.DataPushResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface MedicalHistorySyncApi {

  @POST("medical_histories/sync")
  fun push(
      @Body body: MedicalHistoryPushRequest
  ): Single<DataPushResponse>

  @Headers(value = ["X-RESYNC-TOKEN: 2"])
  @GET("medical_histories/sync")
  fun pull(
      @Query("limit") recordsToPull: Int,
      @Query("process_token") lastPullToken: String? = null
  ): Single<MedicalHistoryPullResponse>
}
