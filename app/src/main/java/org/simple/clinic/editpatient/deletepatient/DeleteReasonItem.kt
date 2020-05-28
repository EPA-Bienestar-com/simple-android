package org.simple.clinic.editpatient.deletepatient

import androidx.recyclerview.widget.DiffUtil
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.list_delete_reason.*
import org.simple.clinic.R
import org.simple.clinic.widgets.ItemAdapter
import org.simple.clinic.widgets.recyclerview.ViewHolderX

data class DeleteReasonItem(
    val reason: PatientDeleteReason,
    val isSelected: Boolean
) : ItemAdapter.Item<DeleteReasonItem.Event> {

  companion object {

    fun from(
        deleteReasons: List<PatientDeleteReason>,
        selectedReason: PatientDeleteReason?
    ): List<DeleteReasonItem> {
      return deleteReasons.map { deleteReason ->
        DeleteReasonItem(
            reason = deleteReason,
            isSelected = deleteReason == selectedReason
        )
      }
    }
  }

  sealed class Event {
    data class Clicked(val reason: PatientDeleteReason) : Event()
  }

  override fun layoutResId(): Int = R.layout.list_delete_reason

  override fun render(holder: ViewHolderX, subject: Subject<Event>) {
    holder.deleteReasonRadioButton.setText(reason.displayText)
    holder.deleteReasonRadioButton.isChecked = isSelected
    holder.deleteReasonRadioButton.setOnClickListener { subject.onNext(Event.Clicked(reason)) }
  }

  class DiffCallback : DiffUtil.ItemCallback<DeleteReasonItem>() {
    override fun areItemsTheSame(oldItem: DeleteReasonItem, newItem: DeleteReasonItem): Boolean {
      return oldItem.reason == newItem.reason
    }

    override fun areContentsTheSame(oldItem: DeleteReasonItem, newItem: DeleteReasonItem): Boolean {
      return oldItem == newItem
    }
  }
}
