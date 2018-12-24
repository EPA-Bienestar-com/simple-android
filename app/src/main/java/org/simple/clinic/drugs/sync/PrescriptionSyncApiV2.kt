package org.simple.clinic.drugs.sync

import io.reactivex.Single
import org.simple.clinic.sync.DataPushResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface PrescriptionSyncApiV2 {

  companion object {
    const val version = "v2"
  }

  @POST("$version/prescription_drugs/sync")
  fun push(
      @Body body: PrescriptionPushRequest
  ): Single<DataPushResponse>

  @GET("$version/prescription_drugs/sync")
  fun pull(
      @Query("limit") recordsToPull: Int,
      @Query("process_token") lastPullToken: String? = null
  ): Single<PrescriptionPullResponse>
}
