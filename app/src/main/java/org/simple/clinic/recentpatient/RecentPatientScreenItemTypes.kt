package org.simple.clinic.recentpatient

import android.annotation.SuppressLint
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.xwray.groupie.ViewHolder
import io.reactivex.subjects.Subject
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.displayIconRes
import org.simple.clinic.recentpatient.RecentPatientItem.RecentPatientItemViewHolder
import org.simple.clinic.summary.GroupieItemWithUiEvents
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.toLocalDateAtZone
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Instant
import org.threeten.bp.format.DateTimeFormatter
import java.util.UUID

sealed class RecentPatientScreenItemTypes<VH : ViewHolder>(adapterId: Long) : GroupieItemWithUiEvents<VH>(adapterId) {
  override lateinit var uiEvents: Subject<UiEvent>
}

data class RecentPatientItem(
    val uuid: UUID,
    val name: String,
    val age: Int,
    val gender: Gender,
    val lastSeen: Instant,
    val dateFormatter: DateTimeFormatter,
    val clock: UserClock
) : RecentPatientScreenItemTypes<RecentPatientItemViewHolder>(uuid.hashCode().toLong()) {

  override fun getLayout(): Int = R.layout.recent_patient_item_view

  override fun createViewHolder(itemView: View) = RecentPatientItemViewHolder(itemView)

  @SuppressLint("SetTextI18n")
  override fun bind(viewHolder: RecentPatientItemViewHolder, position: Int) {
    viewHolder.apply {
      titleTextView.text = itemView.context.getString(R.string.recent_patients_itemview_title, name, age.toString())

      lastSeenTextView.text = dateFormatter.format(lastSeen.toLocalDateAtZone(clock.zone))
      genderImageView.setImageResource(gender.displayIconRes)

      itemView.setOnClickListener {
        uiEvents.onNext(RecentPatientItemClicked(patientUuid = uuid))
      }
    }
  }

  class RecentPatientItemViewHolder(rootView: View) : ViewHolder(rootView) {
    val titleTextView by bindView<TextView>(R.id.recentpatient_item_title)
    val lastSeenTextView by bindView<TextView>(R.id.recentpatient_item_last_seen)
    val genderImageView by bindView<ImageView>(R.id.recentpatient_item_gender)
  }
}
