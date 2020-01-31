package org.simple.clinic.bloodsugar.sync

import io.reactivex.Single
import org.simple.clinic.sync.DataPushResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface BloodSugarSyncApi {

  @POST("blood_sugars/sync")
  fun push(
      @Body body: BloodSugarPushRequest
  ): Single<DataPushResponse>

  @Headers(value = ["X-RESYNC-TOKEN: 1"])
  @GET("blood_sugars/sync")
  fun pull(
      @Query("limit") recordsToPull: Int,
      @Query("process_token") lastPullToken: String? = null
  ): Single<BloodSugarPullResponse>
}
