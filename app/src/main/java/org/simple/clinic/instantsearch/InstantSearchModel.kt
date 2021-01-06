package org.simple.clinic.instantsearch

import org.simple.clinic.facility.Facility
import org.simple.clinic.patient.businessid.Identifier

data class InstantSearchModel(
    val facility: Facility?,
    val searchQuery: String?,
    val additionalIdentifier: Identifier?
) {

  val hasFacility: Boolean
    get() = facility != null

  val hasSearchQuery: Boolean
    get() = !searchQuery.isNullOrBlank()

  val hasAdditionalIdentifier: Boolean
    get() = additionalIdentifier != null

  companion object {
    fun create(additionalIdentifier: Identifier?) = InstantSearchModel(
        facility = null,
        searchQuery = null,
        additionalIdentifier = additionalIdentifier
    )
  }

  fun facilityLoaded(facility: Facility): InstantSearchModel {
    return copy(facility = facility)
  }

  fun searchQueryChanged(searchQuery: String): InstantSearchModel {
    return copy(searchQuery = searchQuery)
  }
}
