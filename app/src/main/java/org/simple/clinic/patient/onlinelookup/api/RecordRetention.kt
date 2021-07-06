package org.simple.clinic.patient.onlinelookup.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.simple.clinic.patient.onlinelookup.api.RetentionType.Permanent
import org.simple.clinic.patient.onlinelookup.api.RetentionType.Temporary
import org.simple.clinic.patient.onlinelookup.api.RetentionType.Unknown
import java.time.Duration
import java.time.Instant

@JsonClass(generateAdapter = true)
data class RecordRetention(
    @Json(name = "type")
    val type: RetentionType,

    @Json(name = "duration_seconds")
    @SecondsDuration
    val retainFor: Duration?
) {

  fun computeRetainUntilTimestamp(instant: Instant, fallbackRetentionDuration: Duration): Instant? {
    return when (type) {
      Temporary -> instant + retainFor!!
      Permanent -> null
      Unknown -> instant + fallbackRetentionDuration
    }
  }
}
