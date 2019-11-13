package org.simple.clinic.newentry

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Observable
import io.reactivex.Single
import org.junit.Test
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.mobius.EffectHandlerTestCase
import org.simple.clinic.newentry.Field.PhoneNumber
import org.simple.clinic.patient.OngoingNewPatientEntry
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider

class PatientEntryEffectHandlerTest {
  @Test
  fun `it hides phone length errors when hide phone validation errors is dispatched`() {
    // given
    val userSession = mock<UserSession>()
    val facilityRepository = mock<FacilityRepository>()
    val patientRepository = mock<PatientRepository>()
    val validationActions = mock<PatientEntryValidationActions>()
    whenever(facilityRepository.currentFacility(any<UserSession>())).doReturn(Observable.never())
    whenever(patientRepository.ongoingEntry()).doReturn(Single.never<OngoingNewPatientEntry>())

    val testCase = EffectHandlerTestCase(PatientEntryEffectHandler.create(
        userSession = userSession,
        facilityRepository = facilityRepository,
        patientRepository = patientRepository,
        patientRegisteredCount = mock(),
        ui = mock(),
        validationActions = validationActions,
        schedulersProvider = TrampolineSchedulersProvider()
    ))

    // when
    testCase.dispatch(HideValidationError(PhoneNumber))

    // then
    testCase.assertNoOutgoingEvents()
    verify(validationActions).showLengthTooShortPhoneNumberError(false)
    verify(validationActions).showLengthTooLongPhoneNumberError(false)
    verifyNoMoreInteractions(validationActions)
  }
}
