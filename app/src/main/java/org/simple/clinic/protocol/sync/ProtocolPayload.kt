package org.simple.clinic.protocol.sync

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.protocol.Protocol
import org.simple.clinic.protocol.ProtocolDrug
import org.threeten.bp.Instant
import java.util.UUID

@JsonClass(generateAdapter = true)
data class ProtocolPayload(

    @Json(name = "id")
    val uuid: UUID,

    @Json(name = "created_at")
    val createdAt: Instant,

    @Json(name = "updated_at")
    val updatedAt: Instant,

    @Json(name = "name")
    val name: String,

    @Json(name = "follow_up_days")
    val followUpDays: Int,

    // TODO: Backend is supposed to make this non-null.
    @Json(name = "protocol_drugs")
    val protocolDrugs: List<ProtocolDrugPayload>?
) {

  fun toDatabaseModel(newStatus: SyncStatus): Protocol {
    return Protocol(
        uuid = uuid,
        createdAt = createdAt,
        updatedAt = updatedAt,
        name = name,
        followUpDays = followUpDays,
        syncStatus = newStatus,
        deletedAt = null
    )
  }
}

@JsonClass(generateAdapter = true)
data class ProtocolDrugPayload(

    @Json(name = "id")
    val uuid: UUID,

    @Json(name = "created_at")
    val createdAt: Instant,

    @Json(name = "updated_at")
    val updatedAt: Instant,

    @Json(name = "protocol_id")
    val protocolUuid: UUID,

    @Json(name = "rxnorm_code")
    val rxNormCode: String?,

    @Json(name = "dosage")
    val dosage: String,

    @Json(name = "name")
    val name: String
) {

  fun toDatabaseModel(): ProtocolDrug {
    return ProtocolDrug(
        uuid = uuid,
        createdAt = createdAt,
        updatedAt = updatedAt,
        protocolUuid = protocolUuid,
        rxNormCode = rxNormCode,
        dosage = dosage,
        name = name,
        deletedAt = null,
        order = 0
    )
  }
}
