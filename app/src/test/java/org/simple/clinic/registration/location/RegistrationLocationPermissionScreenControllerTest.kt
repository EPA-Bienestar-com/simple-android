package org.simple.clinic.registration.location

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import org.simple.clinic.util.RuntimePermissionResult
import org.simple.clinic.widgets.UiEvent

class RegistrationLocationPermissionScreenControllerTest {

  val uiEvents = PublishSubject.create<UiEvent>()!!
  val screen = mock<RegistrationLocationPermissionScreen>()

  private lateinit var controller: RegistrationLocationPermissionScreenController

  @Before
  fun setUp() {
    controller = RegistrationLocationPermissionScreenController()

    uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(screen) }
  }

  @Test
  fun `when location permission is received then facility selection screen should be opened`() {
    uiEvents.onNext(LocationPermissionChanged(RuntimePermissionResult.GRANTED))

    verify(screen).openFacilitySelectionScreen()
  }
}
