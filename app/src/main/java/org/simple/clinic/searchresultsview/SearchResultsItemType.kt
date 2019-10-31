package org.simple.clinic.searchresultsview

import android.widget.TextView
import com.xwray.groupie.ViewHolder
import io.reactivex.subjects.Subject
import org.simple.clinic.R
import org.simple.clinic.facility.Facility
import org.simple.clinic.patient.PatientSearchResult
import org.simple.clinic.summary.GroupieItemWithUiEvents
import org.simple.clinic.widgets.PatientSearchResultItemView
import org.simple.clinic.widgets.PatientSearchResultItemView.PatientSearchResultViewModel
import org.simple.clinic.widgets.UiEvent
import java.util.UUID

sealed class SearchResultsItemType<T : ViewHolder>(adapterId: Long) : GroupieItemWithUiEvents<T>(adapterId) {

  companion object {
    private const val HEADER_NOT_IN_CURRENT_FACILITY = 0L
    private const val HEADER_NO_PATIENTS_IN_CURRENT_FACILITY = 1L

    fun from(
        results: PatientSearchResults
    ): List<SearchResultsItemType<out ViewHolder>> {
      return when {
        results.hasNoResults -> emptyList()
        else -> generateSearchResultListItems(results)
      }
    }

    private fun generateSearchResultListItems(
        results: PatientSearchResults
    ): List<SearchResultsItemType<ViewHolder>> {
      val currentFacility = results.currentFacility!!
      val itemsInCurrentFacility = SearchResultRow
          .forSearchResults(results.visitedCurrentFacility, currentFacility)
          .let { searchResultRowsInCurrentFacility ->
            listItemsForCurrentFacility(
                currentFacility = currentFacility,
                searchResultRows = searchResultRowsInCurrentFacility
            )
          }

      val itemsInOtherFacility = SearchResultRow
          .forSearchResults(results.notVisitedCurrentFacility, currentFacility)
          .let(::listItemsForOtherFacilities)

      return itemsInCurrentFacility + itemsInOtherFacility
    }

    private fun listItemsForCurrentFacility(
        currentFacility: Facility,
        searchResultRows: List<SearchResultRow>
    ): List<SearchResultsItemType<ViewHolder>> {

      val currentFacilityHeader = InCurrentFacilityHeader(currentFacility.name)

      return if (searchResultRows.isNotEmpty()) {
        listOf(
            currentFacilityHeader,
            *searchResultRows.toTypedArray()
        )
      } else {
        listOf(
            currentFacilityHeader,
            NoPatientsInCurrentFacility
        )
      }
    }

    private fun listItemsForOtherFacilities(
        searchResultRows: List<SearchResultRow>
    ): List<SearchResultsItemType<ViewHolder>> {

      return if (searchResultRows.isNotEmpty()) {
        listOf(
            NotInCurrentFacilityHeader,
            *searchResultRows.toTypedArray()
        )
      } else {
        emptyList()
      }
    }
  }

  override lateinit var uiEvents: Subject<UiEvent>

  data class InCurrentFacilityHeader(
      private val facilityName: String
  ) : SearchResultsItemType<ViewHolder>(facilityName.hashCode().toLong()) {

    override fun getLayout(): Int = R.layout.list_patient_search_header

    override fun bind(viewHolder: ViewHolder, position: Int) {
      val header = viewHolder.itemView.findViewById<TextView>(R.id.patientsearch_header)

      header.text = viewHolder.itemView.context.getString(R.string.patientsearchresults_current_facility_header, facilityName)
    }
  }

  object NotInCurrentFacilityHeader : SearchResultsItemType<ViewHolder>(HEADER_NOT_IN_CURRENT_FACILITY) {

    override fun getLayout(): Int = R.layout.list_patient_search_header

    override fun bind(viewHolder: ViewHolder, position: Int) {
      val header = viewHolder.itemView.findViewById<TextView>(R.id.patientsearch_header)

      header.text = viewHolder.itemView.context.getString(R.string.patientsearchresults_other_results_header)
    }
  }

  object NoPatientsInCurrentFacility : SearchResultsItemType<ViewHolder>(HEADER_NO_PATIENTS_IN_CURRENT_FACILITY) {
    override fun getLayout(): Int = R.layout.list_patient_search_no_patients

    override fun bind(viewHolder: ViewHolder, position: Int) {
    }
  }

  data class SearchResultRow(
      private val searchResultViewModel: PatientSearchResultViewModel,
      private val currentFacilityUuid: UUID
  ) : SearchResultsItemType<ViewHolder>(searchResultViewModel.hashCode().toLong()) {

    companion object {
      fun forSearchResults(
          searchResults: List<PatientSearchResult>,
          currentFacility: Facility
      ): List<SearchResultRow> {
        return searchResults
            .map(::mapPatientSearchResultToViewModel)
            .map { searchResultViewModel -> SearchResultRow(searchResultViewModel, currentFacility.uuid) }
      }

      private fun mapPatientSearchResultToViewModel(searchResult: PatientSearchResult): PatientSearchResultViewModel {
        return PatientSearchResultViewModel(
            uuid = searchResult.uuid,
            fullName = searchResult.fullName,
            gender = searchResult.gender,
            age = searchResult.age,
            dateOfBirth = searchResult.dateOfBirth,
            address = searchResult.address,
            phoneNumber = searchResult.phoneNumber,
            lastBp = searchResult.lastBp
        )
      }
    }

    override fun getLayout(): Int = R.layout.list_patient_search

    override fun bind(viewHolder: ViewHolder, position: Int) {
      val patientSearchResultView = viewHolder.itemView.findViewById<PatientSearchResultItemView>(R.id.patientSearchResultView)

      patientSearchResultView.setOnClickListener {
        uiEvents.onNext(SearchResultClicked(searchResultViewModel.uuid))
      }

      patientSearchResultView.render(searchResultViewModel, currentFacilityUuid)
    }
  }
}
