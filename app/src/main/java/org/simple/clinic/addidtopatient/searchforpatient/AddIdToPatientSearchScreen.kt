package org.simple.clinic.addidtopatient.searchforpatient

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import kotlinx.android.synthetic.main.screen_addidtopatientsearch.view.*
import org.simple.clinic.R
import org.simple.clinic.addidtopatient.searchresults.AddIdToPatientSearchResultsScreenKey
import org.simple.clinic.allpatientsinfacility.AllPatientsInFacilityListScrolled
import org.simple.clinic.allpatientsinfacility.AllPatientsInFacilitySearchResultClicked
import org.simple.clinic.allpatientsinfacility.AllPatientsInFacilityView
import org.simple.clinic.bindUiToController
import org.simple.clinic.main.TheActivity
import org.simple.clinic.patient.PatientSearchCriteria
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.summary.OpenIntention
import org.simple.clinic.summary.PatientSummaryScreenKey
import org.simple.clinic.util.UtcClock
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.hideKeyboard
import org.simple.clinic.widgets.showKeyboard
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class AddIdToPatientSearchScreen(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs) {

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var activity: AppCompatActivity

  @Inject
  lateinit var controller: AddIdToPatientSearchScreenController

  @Inject
  lateinit var utcClock: UtcClock

  private val screenKey by unsafeLazy {
    screenRouter.key<AddIdToPatientSearchScreenKey>(this)
  }

  private val allPatientsInFacility by unsafeLazy {
    allPatientsInFacilityView as AllPatientsInFacilityView
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    TheActivity.component.inject(this)

    toolBar.setNavigationOnClickListener {
      screenRouter.pop()
    }
    searchQueryEditText.showKeyboard()

    val screenDestroys = RxView.detaches(this).map { ScreenDestroyed() }
    hideKeyboardWhenAllPatientsListIsScrolled(screenDestroys)

    bindUiToController(
        ui = this,
        events = Observable.merge(
            searchQueryChanges(),
            searchClicks(),
            patientClickEvents()
        ),
        controller = controller,
        screenDestroys = screenDestroys
    )
  }

  private fun searchQueryChanges(): Observable<UiEvent> {
    return RxTextView
        .textChanges(searchQueryEditText)
        .map(CharSequence::toString)
        .map(::SearchQueryTextChanged)
  }

  private fun searchClicks(): Observable<SearchClicked> {
    val imeSearchClicks = RxTextView
        .editorActionEvents(searchQueryEditText)
        .filter { it.actionId() == EditorInfo.IME_ACTION_SEARCH }

    return RxView
        .clicks(searchButtonFrame.button)
        .mergeWith(imeSearchClicks)
        .map { SearchClicked }
  }

  private fun patientClickEvents(): Observable<UiEvent> {
    return allPatientsInFacility
        .uiEvents
        .ofType<AllPatientsInFacilitySearchResultClicked>()
        .map { it.patientUuid }
        .map(::PatientItemClicked)
  }

  @Suppress("CheckResult")
  private fun hideKeyboardWhenAllPatientsListIsScrolled(screenDestroys: Observable<ScreenDestroyed>) {
    allPatientsInFacility
        .uiEvents
        .ofType<AllPatientsInFacilityListScrolled>()
        .takeUntil(screenDestroys)
        .subscribe { enterPatientNameInputContainer.hideKeyboard() }
  }

  fun openAddIdToPatientSearchResultsScreen(criteria: PatientSearchCriteria) {
    screenRouter.push(AddIdToPatientSearchResultsScreenKey(
        searchCriteria = criteria,
        identifier = screenKey.identifier
    ))
  }

  fun setEmptySearchQueryErrorVisible(visible: Boolean) {
    searchQueryEditText.error = if (visible) {
      resources.getString(R.string.addidtopatientsearch_error_empty_fullname)
    } else null
  }

  fun openPatientSummary(patientUuid: UUID) {
    screenRouter.push(PatientSummaryScreenKey(
        patientUuid = patientUuid,
        intention = OpenIntention.LinkIdWithPatient(screenKey.identifier),
        screenCreatedTimestamp = Instant.now(utcClock)
    ))
  }

  fun showAllPatientsInFacility() {
    allPatientsInFacilityView.visibility = View.VISIBLE
  }

  fun hideAllPatientsInFacility() {
    allPatientsInFacilityView.visibility = View.GONE
  }

  fun showSearchButton() {
    searchButtonFrame.visibility = View.VISIBLE
  }

  fun hideSearchButton() {
    searchButtonFrame.visibility = View.GONE
  }
}
