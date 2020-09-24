package org.simple.clinic.teleconsultlog.prescription

import android.graphics.Bitmap
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.mobius.EffectHandlerTestCase
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.signature.SignatureRepository
import org.simple.clinic.util.scheduler.TestSchedulersProvider
import java.util.UUID

class TeleconsultPrescriptionEffectHandlerTest {

  private val uiActions = mock<TeleconsultPrescriptionUiActions>()
  private val patientRepository = mock<PatientRepository>()
  private val signatureRepository = mock<SignatureRepository>()
  private val effectHandler = TeleconsultPrescriptionEffectHandler(
      patientRepository = patientRepository,
      signatureRepository = signatureRepository,
      schedulersProvider = TestSchedulersProvider.trampoline(),
      uiActions = uiActions
  )
  private val effectHandlerTestCase = EffectHandlerTestCase(effectHandler.build())

  @After
  fun tearDown() {
    effectHandlerTestCase.dispose()
  }

  @Test
  fun `when load patient details effect is received, then load patient details`() {
    // given
    val patientUuid = UUID.fromString("6e1898a7-e3c0-4497-bf26-5cabbb1cb6c8")
    val patient = TestData.patient(
        uuid = UUID.fromString("9d87d557-e092-48da-ac53-429a7f957598")
    )

    whenever(patientRepository.patientImmediate(patientUuid)) doReturn patient

    // when
    effectHandlerTestCase.dispatch(LoadPatientDetails(patientUuid))

    // then
    effectHandlerTestCase.assertOutgoingEvents(PatientDetailsLoaded(patient))

    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when go back effect is received, then go back to previous screen`() {
    // when
    effectHandlerTestCase.dispatch(GoBack)

    // then
    effectHandlerTestCase.assertNoOutgoingEvents()

    verify(uiActions).goBackToPreviousScreen()
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when show signature effect is received, then show signature error`() {
    // when
    effectHandlerTestCase.dispatch(ShowSignatureRequiredError)

    // then
    effectHandlerTestCase.assertNoOutgoingEvents()

    verify(uiActions).showSignatureRequiredError()
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when load data for next clicked is received, then load data`() {
    // given
    val teleconsultRecordId = UUID.fromString("b38e1672-c75e-4cbc-a723-f1952687703d")
    val medicalRegistrationId = "ABC123456"
    val instructions = "This is a medical instruction"

    val bitmap = mock<Bitmap>()

    whenever(signatureRepository.getSignatureBitmap()) doReturn bitmap

    // when
    effectHandlerTestCase.dispatch(LoadDataForNextClick(teleconsultRecordId, instructions, medicalRegistrationId))

    // then
    effectHandlerTestCase.assertOutgoingEvents(DataForNextClickLoaded(
        medicalInstructions = instructions,
        medicalRegistrationId = medicalRegistrationId,
        signatureBitmap = bitmap
    ))

    verifyZeroInteractions(uiActions)
  }
}
