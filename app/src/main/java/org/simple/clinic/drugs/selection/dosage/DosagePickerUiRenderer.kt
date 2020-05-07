package org.simple.clinic.drugs.selection.dosage

import org.simple.clinic.mobius.ViewRenderer

class DosagePickerUiRenderer(
    private val ui: DosagePickerUi
) : ViewRenderer<DosagePickerModel> {

  override fun render(model: DosagePickerModel) {
    if (model.hasLoadedProtocolDrugs) {
      renderDosages(model)
    }
  }

  private fun renderDosages(model: DosagePickerModel) {
    val dosageListItems = model
        .protocolDrugs!!
        .map { drug -> DosageListItem(DosageOption.Dosage(drug)) }
        .plus(DosageListItem(DosageOption.None))

    ui.populateDosageList(dosageListItems)
  }
}
