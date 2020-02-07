package org.simple.clinic.summary

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import org.junit.Test
import org.simple.clinic.facility.FacilityConfig
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.patient.businessid.Identifier
import java.util.UUID

class PatientSummaryViewRendererTest {

  private val facilityWithDiabetesManagementEnabled = PatientMocker.facility(
      uuid = UUID.fromString("9eb182ee-1ec8-4d19-89b8-abe66ed993d9"),
      facilityConfig = FacilityConfig(diabetesManagementEnabled = true)
  )

  private val facilityWithDiabetesManagementDisabled = PatientMocker.facility(
      uuid = UUID.fromString("9eb182ee-1ec8-4d19-89b8-abe66ed993d9"),
      facilityConfig = FacilityConfig(diabetesManagementEnabled = false)
  )

  private val defaultModel = PatientSummaryModel.from(UUID.fromString("6fdf088e-f6aa-40e9-9cc2-22e197b83470"))
  private val ui = mock<PatientSummaryScreenUi>()

  private val uiRenderer = PatientSummaryViewRenderer(ui)

  @Test
  fun `when the facility supports diabetes management, the diabetes widget must be shown`() {
    // given
    val model = defaultModel.currentFacilityLoaded(facilityWithDiabetesManagementEnabled)

    // when
    uiRenderer.render(model)

    // then
    verify(ui).showDiabetesView()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the facility does not support diabetes management, the diabetes widget must be hidden`() {
    // given
    val model = defaultModel.currentFacilityLoaded(facilityWithDiabetesManagementDisabled)

    // when
    uiRenderer.render(model)

    // then
    verify(ui).hideDiabetesView()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when profile summary is loaded, then populate patient profile and show edit button`() {
    // given
    val patientUuid = UUID.fromString("873e001f-fdc7-4e27-a734-5c9f15b22cdc")
    val patient = PatientMocker.patient(patientUuid)
    val patientAddress = PatientMocker.address(patient.addressUuid)
    val phoneNumber = PatientMocker.phoneNumber(patientUuid = patientUuid)
    val bpPassport = PatientMocker.businessId(patientUuid = patientUuid, identifier = Identifier("526 780", Identifier.IdentifierType.BpPassport))
    val bangladeshNationalId = PatientMocker.businessId(patientUuid = patientUuid, identifier = Identifier("123456789012", Identifier.IdentifierType.BangladeshNationalId))

    val patientSummaryProfile = PatientSummaryProfile(
        patient = patient,
        address = patientAddress,
        phoneNumber = phoneNumber,
        bpPassport = bpPassport,
        bangladeshNationalId = bangladeshNationalId
    )


    val model = defaultModel.patientSummaryProfileLoaded(patientSummaryProfile)

    // when
    uiRenderer.render(model)

    // then
    verify(ui).populatePatientProfile(patientSummaryProfile)
    verify(ui).showEditButton()
    verifyNoMoreInteractions(ui)
  }
}
