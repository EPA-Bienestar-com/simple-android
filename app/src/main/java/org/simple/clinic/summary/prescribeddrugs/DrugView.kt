package org.simple.clinic.summary.prescribeddrugs

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import org.simple.clinic.R
import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.text.style.TextAppearanceWithLetterSpacingSpan
import org.simple.clinic.util.Truss

class DrugView(
    context: Context,
    attributeSet: AttributeSet
) : AppCompatTextView(context, attributeSet) {

  companion object {
    fun create(parent: ViewGroup, drug: PrescribedDrug): DrugView {
      val inflater = LayoutInflater.from(parent.context)

      val view =  inflater.inflate(R.layout.list_patientsummary_prescription_drug, parent, false) as DrugView

      view.bind(drug)

      return view
    }
  }

  private fun bind(drug: PrescribedDrug) {
    val summaryBuilder = Truss()
    summaryBuilder.append(drug.name)
    if (drug.dosage.isNullOrBlank().not()) {
      val dosageTextAppearance = TextAppearanceWithLetterSpacingSpan(context, R.style.Clinic_V2_TextAppearance_Body1Left_Grey1)
      summaryBuilder
          .pushSpan(dosageTextAppearance)
          .append("  ${drug.dosage}")
          .popSpan()
    }
    text = summaryBuilder.build()
  }
}
