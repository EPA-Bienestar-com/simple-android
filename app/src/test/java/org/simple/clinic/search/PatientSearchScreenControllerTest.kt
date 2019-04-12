package org.simple.clinic.search

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.widgets.UiEvent

class PatientSearchScreenControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val screen: PatientSearchScreen = mock()

  private lateinit var controller: PatientSearchScreenController
  private val uiEvents = PublishSubject.create<UiEvent>()

  @Before
  fun setUp() {
    controller = PatientSearchScreenController()

    uiEvents.compose(controller).subscribe { uiChange -> uiChange(screen) }
  }

  @Test
  fun `search button should remain enabled only when name is present`() {
    uiEvents.onNext(SearchQueryNameChanged(""))
    uiEvents.onNext(SearchQueryNameChanged("foo"))
    uiEvents.onNext(SearchQueryNameChanged(" "))
    uiEvents.onNext(SearchQueryNameChanged("bar"))

    verify(screen, times(2)).showSearchButtonAsDisabled()
    verify(screen, times(2)).showSearchButtonAsEnabled()
  }

  @Test
  fun `when search is clicked with empty name then a validation error should be shown`() {
    uiEvents.onNext(SearchQueryNameChanged(""))
    uiEvents.onNext(SearchClicked())

    verify(screen, times(1)).setEmptyFullNameErrorVisible(true)
  }

  @Test
  fun `when name changes then any validation error on name should be removed`() {
    uiEvents.onNext(SearchQueryNameChanged("Anish"))
    uiEvents.onNext(SearchQueryNameChanged("Anish Acharya"))

    verify(screen, times(2)).setEmptyFullNameErrorVisible(false)
  }

  @Test
  fun `when search is clicked with empty name then patients shouldn't be searched`() {
    uiEvents.onNext(SearchQueryNameChanged(""))
    uiEvents.onNext(SearchClicked())

    verify(screen, never()).openPatientSearchResultsScreen(any())
  }

  @Test
  fun `when full name is present, and search is clicked, search results screen should open`() {
    val fullName = "bar"

    uiEvents.onNext(SearchQueryNameChanged(fullName))
    uiEvents.onNext(SearchClicked())

    verify(screen).openPatientSearchResultsScreen(fullName)
  }
}
