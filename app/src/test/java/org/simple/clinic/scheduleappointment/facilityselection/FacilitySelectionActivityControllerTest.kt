package org.simple.clinic.scheduleappointment.facilityselection

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.widgets.UiEvent

class FacilitySelectionActivityControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val uiEvents = PublishSubject.create<UiEvent>()!!
  private val screen = mock<FacilitySelectionActivity>()

  private lateinit var controller: FacilitySelectionActivityController

  @Before
  fun setUp() {
    controller = FacilitySelectionActivityController()

    uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(screen) }
  }

  @Test
  fun `when facility is selected then it should be passed back as result`() {
    val newFacility = TestData.facility()

    uiEvents.onNext(FacilitySelected(newFacility))

    verify(screen).sendSelectedFacility(newFacility)
  }
}
