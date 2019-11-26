package org.simple.clinic.patient.businessid

import com.squareup.moshi.Json
import java.util.UUID

sealed class BusinessIdMetaData {

  data class BpPassportMetaDataV1(

      @Json(name = "assigning_user_id")
      val assigningUserUuid: UUID,

      @Json(name = "assigning_facility_id")
      val assigningFacilityUuid: UUID
  ) : BusinessIdMetaData()

  data class BangladeshNationalIdMetaDataV1(

      @Json(name = "assigning_user_id")
      val assigningUserUuid: UUID,

      @Json(name = "assigning_facility_id")
      val assigningFacilityUuid: UUID
  ) : BusinessIdMetaData()
}
