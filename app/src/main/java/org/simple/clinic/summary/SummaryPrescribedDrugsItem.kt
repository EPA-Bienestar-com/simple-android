package org.simple.clinic.summary

import android.text.style.TypefaceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.xwray.groupie.ViewHolder
import io.reactivex.subjects.Subject
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.util.Truss
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.setTopMargin
import timber.log.Timber

data class SummaryPrescribedDrugsItem(
    val prescriptions: List<PrescribedDrug>
) : GroupieItemWithUiEvents<SummaryPrescribedDrugsItem.DrugsSummaryViewHolder>(adapterId = 0) {

  override lateinit var uiEvents: Subject<UiEvent>

  override fun getLayout() = R.layout.list_patientsummary_prescriptions

  override fun createViewHolder(itemView: View): DrugsSummaryViewHolder {
    val holder = DrugsSummaryViewHolder(itemView)
    holder.updateButton.setOnClickListener {
      uiEvents.onNext(PatientSummaryUpdateDrugsClicked())
    }
    return holder
  }

  override fun bind(holder: DrugsSummaryViewHolder, position: Int) {
    holder.summaryViewGroup.visibility = when {
      prescriptions.isEmpty() -> View.GONE
      else -> View.VISIBLE
    }

    holder.setButtonText(prescriptions)

    holder.removeAllDrugViews()

    if (prescriptions.isNotEmpty()) {
      prescriptions.forEach { drug ->
        val drugViewHolder = holder.inflateRowForDrug()
        drugViewHolder.bind(drug)
      }

      val lastUpdatedPrescription = prescriptions
          .sortedByDescending { it.updatedAt.toEpochMilli() }
          .first()

      Timber.i("Last updated prescription: ${lastUpdatedPrescription.name}: ${lastUpdatedPrescription.updatedAt}")

      val lastUpdatedTimestamp = RelativeTimestampGenerator().generate(lastUpdatedPrescription.updatedAt)
      holder.lastUpdatedTimestampTextView.text = lastUpdatedTimestamp.displayText(holder.itemView.context)
    }

    holder.updateButton.setTopMargin(when {
      prescriptions.isEmpty() -> R.dimen.summary_prescriptions_update_when_empty
      else -> R.dimen.summary_prescriptions_update_when_non_empty
    })
  }

  class DrugsSummaryViewHolder(rootView: View) : ViewHolder(rootView) {
    val summaryViewGroup by bindView<ViewGroup>(R.id.patientsummary_prescriptions_summary_container)
    val drugsSummaryContainer by bindView<ViewGroup>(R.id.patientsummary_prescriptions_container)
    val lastUpdatedTimestampTextView by bindView<TextView>(R.id.patientsummary_prescriptions_last_updated_timestamp)
    val updateButton by bindView<Button>(R.id.patientsummary_prescriptions_update)

    fun inflateRowForDrug(): DrugViewHolder {
      val drugViewHolder = DrugViewHolder.create(drugsSummaryContainer)
      drugsSummaryContainer.addView(drugViewHolder.itemView, drugsSummaryContainer.childCount - 1)
      return drugViewHolder
    }

    fun removeAllDrugViews() {
      drugsSummaryContainer.removeAllViews()
    }

    fun setButtonText(prescriptions: List<PrescribedDrug>) {
      updateButton.text =
        if ( prescriptions.isEmpty() ) {
          itemView.context.getString(R.string.patientsummary_prescriptions_add)
        }
        else {
          itemView.context.getString(R.string.patientsummary_prescriptions_update)
        }
    }
  }

  class DrugViewHolder(val itemView: View) {
    private val drugTextView = itemView as TextView

    fun bind(drug: PrescribedDrug) {
      val summaryBuilder = Truss()
      if (drug.dosage.isNullOrBlank().not()) {
        summaryBuilder
            .pushSpan(TypefaceSpan("sans-serif-medium"))
            .append(drug.dosage!!)
            .popSpan()
      }
      summaryBuilder.append("   ${drug.name}")
      drugTextView.text = summaryBuilder.build()
    }

    companion object {
      fun create(parent: ViewGroup): DrugViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemLayout = inflater.inflate(R.layout.list_patientsummary_prescription_drug, parent, false)
        return DrugViewHolder(itemLayout)
      }
    }
  }
}
