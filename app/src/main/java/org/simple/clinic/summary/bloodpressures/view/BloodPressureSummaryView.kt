package org.simple.clinic.summary.bloodpressures.view

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.view.detaches
import io.reactivex.Observable
import io.reactivex.rxkotlin.cast
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.patientsummary_bpsummary_content.view.*
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.entry.BloodPressureEntrySheet
import org.simple.clinic.bp.history.BloodPressureHistoryScreenKey
import org.simple.clinic.di.injector
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.router.screen.ActivityResult
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.summary.PatientSummaryConfig
import org.simple.clinic.summary.PatientSummaryScreenKey
import org.simple.clinic.summary.SUMMARY_REQCODE_BP_ENTRY
import org.simple.clinic.summary.bloodpressures.AddNewBloodPressureClicked
import org.simple.clinic.summary.bloodpressures.BloodPressureClicked
import org.simple.clinic.summary.bloodpressures.BloodPressureSummaryViewConfig
import org.simple.clinic.summary.bloodpressures.BloodPressureSummaryViewEffect
import org.simple.clinic.summary.bloodpressures.BloodPressureSummaryViewEffectHandler
import org.simple.clinic.summary.bloodpressures.BloodPressureSummaryViewEvent
import org.simple.clinic.summary.bloodpressures.BloodPressureSummaryViewInit
import org.simple.clinic.summary.bloodpressures.BloodPressureSummaryViewModel
import org.simple.clinic.summary.bloodpressures.BloodPressureSummaryViewUi
import org.simple.clinic.summary.bloodpressures.BloodPressureSummaryViewUiActions
import org.simple.clinic.summary.bloodpressures.BloodPressureSummaryViewUiRenderer
import org.simple.clinic.summary.bloodpressures.BloodPressureSummaryViewUpdate
import org.simple.clinic.summary.bloodpressures.SeeAllClicked
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.UtcClock
import org.simple.clinic.util.toLocalDateAtZone
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.setPaddingBottom
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

private typealias BpRecorded = () -> Unit

class BloodPressureSummaryView(
    context: Context,
    attrs: AttributeSet
) : CardView(context, attrs), BloodPressureSummaryViewUi, BloodPressureSummaryViewUiActions {

  @Inject
  lateinit var activity: AppCompatActivity

  @Inject
  lateinit var bloodPressureSummaryConfig: BloodPressureSummaryViewConfig

  @Inject
  lateinit var patientSummaryConfig: PatientSummaryConfig

  @Inject
  lateinit var effectHandlerFactory: BloodPressureSummaryViewEffectHandler.Factory

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var utcClock: UtcClock

  @Inject
  lateinit var userClock: UserClock

  @field:[Inject Named("date_for_measurement_history")]
  lateinit var dateFormatter: DateTimeFormatter

  @field:[Inject Named("time_for_measurement_history")]
  lateinit var timeFormatter: DateTimeFormatter

  @Inject
  lateinit var crashReporter: CrashReporter

  private val viewEvents = PublishSubject.create<BloodPressureSummaryViewEvent>()

  private val events: Observable<BloodPressureSummaryViewEvent> by unsafeLazy {
    Observable
        .merge(
            addNewBpClicked(),
            seeAllClicked(),
            viewEvents
        )
        .compose(ReportAnalyticsEvents())
        .cast<BloodPressureSummaryViewEvent>()
  }

  private val uiRenderer: BloodPressureSummaryViewUiRenderer by unsafeLazy {
    BloodPressureSummaryViewUiRenderer(this, bloodPressureSummaryConfig)
  }

  private val delegate: MobiusDelegate<BloodPressureSummaryViewModel, BloodPressureSummaryViewEvent, BloodPressureSummaryViewEffect> by unsafeLazy {
    val screenKey = screenRouter.key<PatientSummaryScreenKey>(this)
    MobiusDelegate(
        events = events,
        defaultModel = BloodPressureSummaryViewModel.create(screenKey.patientUuid),
        init = BloodPressureSummaryViewInit(bloodPressureSummaryConfig),
        update = BloodPressureSummaryViewUpdate(bloodPressureSummaryConfig),
        effectHandler = effectHandlerFactory.create(this).build(),
        modelUpdateListener = uiRenderer::render,
        crashReporter = crashReporter
    )
  }

  var bpRecorded: BpRecorded? = null

  init {
    inflate(context, R.layout.patientsummary_bpsummary_content, this)
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    context.injector<BloodPressureSummaryViewInjector>().inject(this)

    delegate.prepare()

    val screenDestroys: Observable<ScreenDestroyed> = detaches().map { ScreenDestroyed() }

    setupBpRecordedEvents(screenDestroys)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    delegate.start()
  }

  override fun onDetachedFromWindow() {
    delegate.stop()
    super.onDetachedFromWindow()
  }

  override fun onSaveInstanceState(): Parcelable? {
    return delegate.onSaveInstanceState(super.onSaveInstanceState())
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    super.onRestoreInstanceState(delegate.onRestoreInstanceState(state))
  }

  override fun showNoBloodPressuresView() {
    newBPItemContainer.visibility = View.GONE
    placeHolderMessageTextView.visibility = View.VISIBLE
  }

  override fun showBloodPressures(bloodPressures: List<BloodPressureMeasurement>) {
    newBPItemContainer.visibility = View.VISIBLE
    placeHolderMessageTextView.visibility = View.GONE

    render(
        measurements = bloodPressures,
        canEditFor = patientSummaryConfig.bpEditableDuration,
        utcClock = utcClock,
        userClock = userClock,
        dateFormatter = dateFormatter,
        timeFormatter = timeFormatter
    )
  }

  override fun showSeeAllButton() {
    seeAll.visibility = View.VISIBLE
  }

  override fun hideSeeAllButton() {
    seeAll.visibility = View.GONE
  }

  override fun openBloodPressureEntrySheet(patientUuid: UUID) {
    val intent = BloodPressureEntrySheet.intentForNewBp(context, patientUuid)
    activity.startActivityForResult(intent, SUMMARY_REQCODE_BP_ENTRY)
  }

  override fun openBloodPressureUpdateSheet(bpUuid: UUID) {
    val intent = BloodPressureEntrySheet.intentForUpdateBp(context, bpUuid)
    activity.startActivity(intent)
  }

  override fun showBloodPressureHistoryScreen(patientUuid: UUID) {
    screenRouter.push(BloodPressureHistoryScreenKey(patientUuid))
  }

  @SuppressLint("CheckResult")
  private fun setupBpRecordedEvents(screenDestroys: Observable<ScreenDestroyed>) {
    screenRouter.streamScreenResults()
        .ofType<ActivityResult>()
        .filter { it.requestCode == SUMMARY_REQCODE_BP_ENTRY && it.succeeded() }
        .filter { BloodPressureEntrySheet.wasBloodPressureSaved(it.data!!) }
        .takeUntil(screenDestroys)
        .subscribe { bpRecorded?.invoke() }
  }

  private fun addNewBpClicked(): Observable<BloodPressureSummaryViewEvent> {
    return addNewBP.clicks().map { AddNewBloodPressureClicked }
  }

  private fun seeAllClicked(): Observable<BloodPressureSummaryViewEvent> {
    return seeAll.clicks().map { SeeAllClicked }
  }

  private fun render(
      measurements: List<BloodPressureMeasurement>,
      canEditFor: Duration,
      utcClock: UtcClock,
      userClock: UserClock,
      dateFormatter: DateTimeFormatter,
      timeFormatter: DateTimeFormatter
  ) {
    val listItemViews = generateBpViews(
        measurements = measurements,
        canEditFor = canEditFor,
        utcClock = utcClock,
        userClock = userClock,
        dateFormatter = dateFormatter,
        timeFormatter = timeFormatter
    )
    val itemContainerBottomPadding = if (listItemViews.size > 1) {
      R.dimen.patientsummary_bp_summary_item_container_bottom_padding_8
    } else {
      R.dimen.patientsummary_bp_summary_item_container_bottom_padding_24
    }

    newBPItemContainer.removeAllViews()
    listItemViews.forEach(newBPItemContainer::addView)
    newBPItemContainer.setPaddingBottom(itemContainerBottomPadding)
  }

  private fun generateBpViews(
      measurements: List<BloodPressureMeasurement>,
      canEditFor: Duration,
      utcClock: UtcClock,
      userClock: UserClock,
      dateFormatter: DateTimeFormatter,
      timeFormatter: DateTimeFormatter
  ): List<View> {
    val measurementByDate = measurements.groupBy { it.recordedAt.toLocalDateAtZone(userClock.zone) }

    return measurementByDate.mapValues { (_, measurementsList) ->
      val hasMultipleMeasurementsInSameDate = measurementsList.size > 1
      measurementsList.map { measurement ->
        val isBpEditable = isBpEditable(measurement, canEditFor, utcClock)
        val recordedAt = measurement.recordedAt.toLocalDateAtZone(userClock.zone)
        val bpTime = if (hasMultipleMeasurementsInSameDate) {
          timeFormatter.format(measurement.recordedAt.atZone(userClock.zone))
        } else {
          null
        }

        val bloodPressureItemView = LayoutInflater.from(context).inflate(R.layout.list_patientsummary_bp_measurement, this, false) as BloodPressureItemView
        bloodPressureItemView.render(
            measurement = measurement,
            isBpEditable = isBpEditable,
            bpDate = dateFormatter.format(recordedAt),
            bpTime = bpTime,
            editMeasurementClicked = { clickedMeasurement -> viewEvents.onNext(BloodPressureClicked(clickedMeasurement)) }
        )
        bloodPressureItemView
      }
    }.values.flatten()
  }

  private fun isBpEditable(
      bloodPressureMeasurement: BloodPressureMeasurement,
      bpEditableFor: Duration,
      utcClock: UtcClock
  ): Boolean {
    val now = Instant.now(utcClock)
    val createdAt = bloodPressureMeasurement.createdAt

    val durationSinceBpCreated = Duration.between(createdAt, now)

    return durationSinceBpCreated <= bpEditableFor
  }
}
