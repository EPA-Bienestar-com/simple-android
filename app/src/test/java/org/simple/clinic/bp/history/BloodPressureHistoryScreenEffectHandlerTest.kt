package org.simple.clinic.bp.history

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Observable
import org.junit.After
import org.junit.Test
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.mobius.EffectHandlerTestCase
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.util.Just
import org.simple.clinic.util.Optional
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import java.util.UUID

class BloodPressureHistoryScreenEffectHandlerTest {

  private val patientRepository = mock<PatientRepository>()
  private val bloodPressureRepository = mock<BloodPressureRepository>()
  private val patientUuid = UUID.fromString("433d058f-daef-47a7-8c61-95f1a220cbcb")
  private val uiActions = mock<BloodPressureHistoryScreenUiActions>()
  private val effectHandler = BloodPressureHistoryScreenEffectHandler(
      bloodPressureRepository,
      patientRepository,
      TrampolineSchedulersProvider(),
      uiActions
  ).build()
  private val testCase = EffectHandlerTestCase(effectHandler)

  @After
  fun tearDown() {
    testCase.dispose()
  }

  @Test
  fun `when load patient effect is received, then load patient`() {
    // given
    val patientUuid = UUID.fromString("46044a13-012e-439b-81c9-8bbb15307629")
    val patient = PatientMocker.patient(uuid = patientUuid)
    whenever(patientRepository.patient(patientUuid)) doReturn Observable.just<Optional<Patient>>(Just(patient))

    // when
    testCase.dispatch(LoadPatient(patientUuid))

    // then
    testCase.assertOutgoingEvents(PatientLoaded(patient))
    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when load blood pressure history effect is received, then load all blood pressures`() {
    // given
    val bloodPressureMeasurement = PatientMocker.bloodPressureMeasurement(
        UUID.fromString("51ac042d-2f70-495c-a3e3-2599d8990da2"),
        patientUuid
    )
    val bloodPressures = listOf(bloodPressureMeasurement)
    whenever(bloodPressureRepository.allBloodPressures(patientUuid)) doReturn Observable.just(bloodPressures)

    // when
    testCase.dispatch(LoadBloodPressureHistory(patientUuid))

    // then
    testCase.assertOutgoingEvents(BloodPressureHistoryLoaded(bloodPressures))
    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when open blood pressure entry sheet effect is received, then open blood pressure entry sheet`() {
    // given
    val patientUuid = UUID.fromString("5adeb648-00a6-4073-b509-ac74cbd5f08b")

    // when
    testCase.dispatch(OpenBloodPressureEntrySheet(patientUuid))

    // then
    testCase.assertNoOutgoingEvents()
    verify(uiActions).openBloodPressureEntrySheet(patientUuid)
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when open blood pressure update sheet effect is received, then open blood pressure update sheet`() {
    // given
    val bloodPressureMeasurement = PatientMocker.bloodPressureMeasurement(
        UUID.fromString("3c6fb840-86b9-4b85-aa75-b24c4bb9fbfd"),
        patientUuid
    )

    // when
    testCase.dispatch(OpenBloodPressureUpdateSheet(bloodPressureMeasurement))

    // then
    testCase.assertNoOutgoingEvents()
    verify(uiActions).openBloodPressureUpdateSheet(bloodPressureMeasurement.uuid)
    verifyNoMoreInteractions(uiActions)
  }
}
