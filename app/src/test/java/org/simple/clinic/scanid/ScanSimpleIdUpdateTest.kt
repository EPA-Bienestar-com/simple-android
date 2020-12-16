package org.simple.clinic.scanid

import com.spotify.mobius.test.NextMatchers.hasEffects
import com.spotify.mobius.test.NextMatchers.hasModel
import com.spotify.mobius.test.NextMatchers.hasNoModel
import com.spotify.mobius.test.NextMatchers.hasNothing
import com.spotify.mobius.test.UpdateSpec
import com.spotify.mobius.test.UpdateSpec.assertThatNext
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.patient.businessid.Identifier.IdentifierType.BpPassport
import org.simple.clinic.platform.crash.NoOpCrashReporter
import org.simple.clinic.util.Optional
import java.util.UUID

class ScanSimpleIdUpdateTest {

  private val defaultModel = ScanSimpleIdModel.create()

  private val spec = UpdateSpec(ScanSimpleIdUpdate(
      crashReporter = NoOpCrashReporter()
  ))

  @Test
  fun `when a valid QR code is scanned, then search for the patient`() {
    val scannedId = "9f154761-ee2f-4ee3-acd1-0038328f75ca"
    val identifier = Identifier(scannedId, BpPassport)

    spec
        .given(defaultModel)
        .whenEvent(ScanSimpleIdScreenQrCodeScanned(scannedId))
        .then(assertThatNext(
            hasModel(defaultModel.searching()),
            hasEffects(SearchPatientByIdentifier(identifier))
        ))
  }

  @Test
  fun `when the entered short code is valid, send the entered short code to the parent screen`() {
    val shortCode = "1234567"
    val model = defaultModel.shortCodeChanged(ShortCodeInput(shortCode))

    val expectedScanResult = EnteredShortCode(shortCode)

    spec
        .given(model)
        .whenEvent(ShortCodeValidated(ShortCodeValidationResult.Success))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(SendScannedIdentifierResult(expectedScanResult))
        ))
  }

  @Test
  fun `when identifier is scanned and patient is found, then send the patient id to the parent screen`() {
    val patientId = UUID.fromString("60822507-9151-4836-944b-9cbbd1530c0b")
    val patient = Optional.of(
        TestData.patient(
            uuid = patientId
        )
    )
    val identifier = Identifier("123456", BpPassport)

    val expectedScanResult = PatientFound(patientId)

    spec
        .given(defaultModel)
        .whenEvent(PatientSearchByIdentifierCompleted(patient, identifier))
        .then(assertThatNext(
            hasModel(defaultModel.notSearching()),
            hasEffects(SendScannedIdentifierResult(expectedScanResult))
        ))
  }

  @Test
  fun `when identifier is scanned and patient is not found, then send the identifier to parent screen`() {
    val patient = Optional.empty<Patient>()
    val identifier = Identifier("123456", BpPassport)

    val expectedScanResult = PatientNotFound(identifier)

    spec
        .given(defaultModel)
        .whenEvent(PatientSearchByIdentifierCompleted(patient, identifier))
        .then(assertThatNext(
            hasModel(defaultModel.notSearching()),
            hasEffects(SendScannedIdentifierResult(expectedScanResult))
        ))
  }

  @Test
  fun `when searching for patient, then ignore newly scanned identifiers`() {
    val scannedId = "9f154761-ee2f-4ee3-acd1-0038328f75ca"

    val searchingModel = defaultModel
        .searching()

    spec
        .given(searchingModel)
        .whenEvent(ScanSimpleIdScreenQrCodeScanned(scannedId))
        .then(assertThatNext(
            hasNothing()
        ))
  }
}
