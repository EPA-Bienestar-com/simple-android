package org.simple.clinic.drugs.sync

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.simple.clinic.sync.DataPullResponse

@JsonClass(generateAdapter = true)
data class PrescriptionPullResponse(

    @Json(name = "prescription_drugs")
    override val payloads: List<PrescribedDrugPayload>,

    @Json(name = "processed_since")
    override val processedSinceTimestamp: String

) : DataPullResponse<PrescribedDrugPayload>
