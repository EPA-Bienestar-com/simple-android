package org.simple.clinic.home

import com.spotify.mobius.test.NextMatchers.hasEffects
import com.spotify.mobius.test.NextMatchers.hasNoModel
import com.spotify.mobius.test.UpdateSpec
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.util.Optional
import java.util.UUID

class HomeScreenUpdateTest {

  private val defaultModel = HomeScreenModel.create()
  private val updateSpec = UpdateSpec(HomeScreenUpdate())

  @Test
  fun `when the patient short code is entered, the short code search screen must be opened`() {
    val shortCode = "1234567"

    updateSpec
        .given(defaultModel)
        .whenEvent(BusinessIdScanned.ByShortCode(shortCode))
        .then(UpdateSpec.assertThatNext(
            hasNoModel(),
            hasEffects(OpenShortCodeSearchScreen(shortCode))
        ))
  }

  @Test
  fun `when the patient identifier is scanned, a patient with the given identifier must be searched for`() {
    val identifier = TestData.identifier("88d12415-b10d-4ebb-bf48-482ece022139", Identifier.IdentifierType.BpPassport)

    updateSpec
        .given(defaultModel)
        .whenEvent(BusinessIdScanned.ByIdentifier(identifier))
        .then(UpdateSpec.assertThatNext(
            hasNoModel(),
            hasEffects(SearchPatientByIdentifier(identifier))
        ))
  }

  @Test
  fun `when the scanned identifier has a corresponding patient, the patient summary screen must be opened`() {
    val identifier = TestData.identifier("88d12415-b10d-4ebb-bf48-482ece022139", Identifier.IdentifierType.BpPassport)
    val patient = TestData.patient(uuid = UUID.fromString("614a3a62-92be-4551-92d0-beca649cfd7c"))

    val event = PatientSearchByIdentifierCompleted(
        patient = Optional.of(patient),
        identifier = identifier
    )

    updateSpec
        .given(defaultModel)
        .whenEvent(event)
        .then(UpdateSpec.assertThatNext(
            hasNoModel(),
            hasEffects(OpenPatientSummary(patient.uuid))
        ))
  }

  @Test
  fun `when the scanned identifier does not have a corresponding patient, the patient search screen must be opened`() {
    val identifier = TestData.identifier("88d12415-b10d-4ebb-bf48-482ece022139", Identifier.IdentifierType.BpPassport)

    val event = PatientSearchByIdentifierCompleted(
        patient = Optional.empty(),
        identifier = identifier
    )

    updateSpec
        .given(defaultModel)
        .whenEvent(event)
        .then(UpdateSpec.assertThatNext(
            hasNoModel(),
            hasEffects(OpenPatientSearchScreen(identifier))
        ))
  }
}
