package org.simple.clinic.scanid

import org.simple.clinic.patient.businessid.Identifier
import java.util.UUID

interface ScanSimpleIdUi : ScanSimpleIdUiActions {
  fun openPatientSummary(patientUuid: UUID)
  fun openAddIdToPatientScreen(identifier: Identifier)
  fun showShortCodeValidationError(failure: ShortCodeValidationResult)
  fun hideShortCodeValidationError()
  fun openPatientShortCodeSearch(validShortCode: String)
  fun hideQrCodeScannerView()
  fun showQrCodeScannerView()
}
