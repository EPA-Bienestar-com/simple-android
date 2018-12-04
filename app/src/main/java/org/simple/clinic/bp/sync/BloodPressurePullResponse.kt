package org.simple.clinic.bp.sync

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.simple.clinic.sync.DataPullResponse

@JsonClass(generateAdapter = true)
data class BloodPressurePullResponse(

    @Json(name = "blood_pressures")
    override val payloads: List<BloodPressureMeasurementPayload>,

    @Json(name = "processed_since")
    override val processedSinceTimestamp: String

) : DataPullResponse<BloodPressureMeasurementPayload>
