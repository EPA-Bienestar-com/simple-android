package org.simple.clinic.recentpatient

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.bindUiToController
import org.simple.clinic.main.TheActivity
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.summary.OpenIntention
import org.simple.clinic.summary.PatientSummaryScreenKey
import org.simple.clinic.util.UtcClock
import org.simple.clinic.widgets.ItemAdapter
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RecentPatientsScreen(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var controller: RecentPatientsScreenController

  @Inject
  lateinit var utcClock: UtcClock

  private val toolbar by bindView<Toolbar>(R.id.recentpatients_toolbar)
  private val recyclerView by bindView<RecyclerView>(R.id.recentpatients_recyclerview)

  private val recentAdapter = ItemAdapter(RecentPatientItem.DiffCallback())

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    TheActivity.component.inject(this)

    setupScreen()

    bindUiToController(
        ui = this,
        events = Observable.merge(screenCreates(), adapterEvents()),
        controller = controller,
        screenDestroys = RxView.detaches(this).map { ScreenDestroyed() }
    )
  }

  private fun screenCreates() = Observable.just(ScreenCreated())

  private fun setupScreen() {
    toolbar.setNavigationOnClickListener {
      screenRouter.pop()
    }

    recyclerView.apply {
      layoutManager = LinearLayoutManager(context)
      adapter = recentAdapter
    }
  }

  private fun adapterEvents(): Observable<UiEvent> {
    return recentAdapter
        .itemEvents
        .ofType()
  }

  fun openPatientSummary(patientUuid: UUID) {
    screenRouter.push(
        PatientSummaryScreenKey(
            patientUuid = patientUuid,
            intention = OpenIntention.ViewExistingPatient,
            screenCreatedTimestamp = Instant.now(utcClock)
        ))
  }

  fun updateRecentPatients(allItemTypes: List<RecentPatientItem>) {
    recentAdapter.submitList(allItemTypes)
  }
}
