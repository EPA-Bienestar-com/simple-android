package org.simple.clinic.scanid

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import com.squareup.moshi.Moshi
import org.junit.After
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.mobius.EffectHandlerTestCase
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.businessid.Identifier.IdentifierType.BpPassport
import org.simple.clinic.util.scheduler.TestSchedulersProvider
import java.util.UUID

class ScanSimpleIdEffectHandlerTest {

  private val patientRepository = mock<PatientRepository>()
  private val uiActions = mock<ScanSimpleIdUiActions>()
  private val moshi = Moshi.Builder().build()
  private val testCase = EffectHandlerTestCase(ScanSimpleIdEffectHandler(
      schedulersProvider = TestSchedulersProvider.trampoline(),
      patientRepository = patientRepository,
      uiActions = uiActions,
      moshi = moshi
  ).build())

  @After
  fun tearDown() {
    testCase.dispose()
  }

  @Test
  fun `when search for patient by identifier effect is received, then search for patient`() {
    // given
    val patient = TestData.patient(
        uuid = UUID.fromString("4db4e9af-56a4-4995-958b-aeb33d80cfa5")
    )

    val identifier = TestData.identifier(
        value = "123 456",
        type = BpPassport
    )

    whenever(patientRepository.findPatientsWithBusinessId(identifier.value)) doReturn listOf(patient)

    // when
    testCase.dispatch(SearchPatientByIdentifier(identifier))

    // then
    testCase.assertOutgoingEvents(PatientSearchByIdentifierCompleted(
        patients = listOf(patient),
        identifier = identifier
    ))
    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when parse json into patient prefill info object effect is received, then parse the json`() {
    // given
    val expectedJson = """
    {
    "hidn":"1234123456785678",
    "hid":"Mohit",
    "name":"Mohit Ahuja",
    "gender":"M",
    "statelgd":"Maharashtra",
    "distlgd":"Thane",
    "dob":"12/12/1997",
    "address":"Obvious HQ"
     }
     """
    val indiaNHIDInfoPayload = TestData.indiaNHIDInfoPayload()
    val indiaNHIDInfo = indiaNHIDInfoPayload.fromPayload()

    // when
    testCase.dispatch(ParseScannedJson(expectedJson))

    // then
    testCase.assertOutgoingEvents(ScannedQRCodeJsonParsed(indiaNHIDInfo))
    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when open patient summary effect is received, then open patient summary`() {
    // given
    val patientId = UUID.fromString("9730e9a0-e62e-4556-b84e-03d593f6fe4c")

    // when
    testCase.dispatch(OpenPatientSummary(patientId))

    // then
    testCase.assertNoOutgoingEvents()

    verify(uiActions).openPatientSummary(patientId)
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when open short code search effect is received, then open short code search`() {
    // given
    val shortCode = "1234567"

    // when
    testCase.dispatch(OpenShortCodeSearch(shortCode))

    // then
    testCase.assertNoOutgoingEvents()

    verify(uiActions).openShortCodeSearch(shortCode)
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when open patient search effect is received, then open patient search`() {
    // given
    val identifier = TestData.identifier(
        value = "a765a30e-6bd9-4f12-99da-acba91b6a479",
        type = BpPassport
    )

    // when
    testCase.dispatch(OpenPatientSearch(identifier))

    // then
    testCase.assertNoOutgoingEvents()

    verify(uiActions).openPatientSearch(identifier)
    verifyNoMoreInteractions(uiActions)
  }
}
