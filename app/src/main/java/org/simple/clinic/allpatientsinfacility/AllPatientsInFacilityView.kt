package org.simple.clinic.allpatientsinfacility

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.rxbinding2.support.v7.widget.RxRecyclerView
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.view_allpatientsinfacility.view.*
import org.simple.clinic.R
import org.simple.clinic.ViewControllerBinding
import org.simple.clinic.activity.TheActivity
import org.simple.clinic.allpatientsinfacility.AllPatientsInFacilityListItem.AllPatientsInFacilityListItemCallback
import org.simple.clinic.allpatientsinfacility.AllPatientsInFacilityListItem.Event.SearchResultClicked
import org.simple.clinic.patient.PatientSearchResult
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.UiEvent
import java.util.Locale
import javax.inject.Inject

class AllPatientsInFacilityView(
    context: Context,
    attributeSet: AttributeSet
) : FrameLayout(context, attributeSet), AllPatientsInFacilityUi {

  companion object {
    private val FEATURE_STATE_KEY = AllPatientsInFacilityView::class.java.name
    private val VIEW_STATE_KEY = FEATURE_STATE_KEY + "ViewState"
  }

  private val searchResultsAdapter by unsafeLazy {
    AllPatientsInFacilityListAdapter(AllPatientsInFacilityListItemCallback(), locale)
  }

  @Inject
  lateinit var uiStateProducer: AllPatientsInFacilityUiStateProducer

  @Inject
  lateinit var uiChangeProducer: AllPatientsInFacilityUiChangeProducer

  @Inject
  lateinit var locale: Locale

  private val downstreamUiEvents = PublishSubject.create<UiEvent>()
  private lateinit var binding: ViewControllerBinding<UiEvent, AllPatientsInFacilityUiState, AllPatientsInFacilityUi>

  val uiEvents: Observable<UiEvent> = downstreamUiEvents.hide()

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }

    TheActivity.component.inject(this)

    setupAllPatientsList()
    setupInitialViewVisibility()
    forwardListItemEventsToDownstream()
    forwardListScrolledEventsToDownstream()

    binding = ViewControllerBinding.bindToView(
        view = this,
        uiStateProducer = uiStateProducer,
        uiChangeProducer = uiChangeProducer
    )
  }

  @SuppressLint("CheckResult")
  private fun forwardListItemEventsToDownstream() {
    searchResultsAdapter
        .itemEvents
        .ofType<SearchResultClicked>()
        .map { it.patientSearchResult.uuid }
        .map(::AllPatientsInFacilitySearchResultClicked)
        .takeUntil(RxView.detaches(this))
        .subscribe(downstreamUiEvents::onNext)
  }

  @SuppressLint("CheckResult")
  private fun forwardListScrolledEventsToDownstream() {
    RxRecyclerView
        .scrollStateChanges(patientsList)
        .filter { it == RecyclerView.SCROLL_STATE_DRAGGING }
        .map { AllPatientsInFacilityListScrolled }
        .takeUntil(RxView.detaches(this))
        .subscribe { downstreamUiEvents.onNext(it) }
  }

  private fun setupInitialViewVisibility() {
    patientsList.visibility = View.GONE
    noPatientsContainer.visibility = View.GONE
  }

  private fun setupAllPatientsList() {
    patientsList.apply {
      setHasFixedSize(true)
      layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
      adapter = searchResultsAdapter
    }
  }

  override fun showNoPatientsFound(facilityName: String) {
    patientsList.visibility = View.GONE
    noPatientsContainer.visibility = View.VISIBLE
    noPatientsLabel.text = resources.getString(R.string.allpatientsinfacility_nopatients_title, facilityName)
  }

  override fun showPatients(facilityUiState: FacilityUiState, patientSearchResults: List<PatientSearchResult>) {
    patientsList.visibility = View.VISIBLE
    noPatientsContainer.visibility = View.GONE
    val listItems = AllPatientsInFacilityListItem.mapSearchResultsToListItems(facilityUiState, patientSearchResults)
    searchResultsAdapter.submitList(listItems)
  }

  override fun onSaveInstanceState(): Parcelable? {
    return Bundle().apply {
      val viewState = super.onSaveInstanceState()
      putParcelable(VIEW_STATE_KEY, viewState)
      putParcelable(FEATURE_STATE_KEY, binding.latestState())
    }
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    val bundle = state as Bundle
    val viewState = bundle[VIEW_STATE_KEY]
    val controllerState = bundle[FEATURE_STATE_KEY]

    (controllerState as AllPatientsInFacilityUiState?)?.let {
      binding.restoreSavedState(it)
    }

    super.onRestoreInstanceState(viewState as Parcelable)
  }
}
