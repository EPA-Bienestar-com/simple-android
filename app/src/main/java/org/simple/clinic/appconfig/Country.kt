package org.simple.clinic.appconfig

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.android.parcel.Parcelize
import java.net.URI

@JsonClass(generateAdapter = true)
@Parcelize
data class Country(

    @Json(name = "country_code")
    val isoCountryCode: String,

    @Json(name = "endpoint")
    val endpoint: URI,

    @Json(name = "display_name")
    val displayName: String,

    @Json(name = "isd_code")
    val isdCode: String
) : Parcelable {

    companion object {
        const val INDIA = "IN"
        const val BANGLADESH = "BD"
        const val ETHIOPIA = "ET"
    }
}
