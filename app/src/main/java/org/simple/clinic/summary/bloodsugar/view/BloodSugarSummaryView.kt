package org.simple.clinic.summary.bloodsugar.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import kotlinx.android.synthetic.main.patientsummary_bloodsugarsummary_content.view.*
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.bloodsugar.BloodSugarMeasurement
import org.simple.clinic.bloodsugar.BloodSugarMeasurementType
import org.simple.clinic.bloodsugar.Unknown
import org.simple.clinic.bloodsugar.entry.BloodSugarEntrySheet
import org.simple.clinic.bloodsugar.history.BloodSugarHistoryScreenKey
import org.simple.clinic.bloodsugar.selection.type.BloodSugarTypePickerSheet
import org.simple.clinic.di.injector
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.router.screen.ActivityResult
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.summary.PatientSummaryScreenKey
import org.simple.clinic.summary.TYPE_PICKER_SHEET
import org.simple.clinic.summary.bloodsugar.BloodSugarClicked
import org.simple.clinic.summary.bloodsugar.BloodSugarSummaryConfig
import org.simple.clinic.summary.bloodsugar.BloodSugarSummaryViewEffect
import org.simple.clinic.summary.bloodsugar.BloodSugarSummaryViewEffectHandler
import org.simple.clinic.summary.bloodsugar.BloodSugarSummaryViewEvent
import org.simple.clinic.summary.bloodsugar.BloodSugarSummaryViewInit
import org.simple.clinic.summary.bloodsugar.BloodSugarSummaryViewModel
import org.simple.clinic.summary.bloodsugar.BloodSugarSummaryViewUi
import org.simple.clinic.summary.bloodsugar.BloodSugarSummaryViewUiRenderer
import org.simple.clinic.summary.bloodsugar.BloodSugarSummaryViewUpdate
import org.simple.clinic.summary.bloodsugar.NewBloodSugarClicked
import org.simple.clinic.summary.bloodsugar.SeeAllClicked
import org.simple.clinic.summary.bloodsugar.UiActions
import org.simple.clinic.util.RelativeTimestampGenerator
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.UtcClock
import org.simple.clinic.util.toLocalDateAtZone
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.setPaddingBottom
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

class BloodSugarSummaryView(
    context: Context,
    attributes: AttributeSet
) : CardView(context, attributes), BloodSugarSummaryViewUi, UiActions {

  @Inject
  lateinit var activity: AppCompatActivity

  @Inject
  lateinit var userClock: UserClock

  @Inject
  lateinit var timestampGenerator: RelativeTimestampGenerator

  @Inject
  lateinit var bloodSugarSummaryConfig: BloodSugarSummaryConfig

  @Inject
  lateinit var utcClock: UtcClock

  @Inject
  lateinit var zoneId: ZoneId

  @Inject
  lateinit var effectHandlerFactory: BloodSugarSummaryViewEffectHandler.Factory

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var crashReporter: CrashReporter

  @field:[Inject Named("full_date")]
  lateinit var dateFormatter: DateTimeFormatter

  @field:[Inject Named("time_for_measurement_history")]
  lateinit var timeFormatter: DateTimeFormatter

  private val uiRenderer: BloodSugarSummaryViewUiRenderer by unsafeLazy {
    BloodSugarSummaryViewUiRenderer(this, bloodSugarSummaryConfig)
  }

  private val viewEvents = PublishSubject.create<BloodSugarSummaryViewEvent>()

  private val events: Observable<BloodSugarSummaryViewEvent> by unsafeLazy {
    Observable
        .merge(
            addNewBloodSugarClicks(),
            seeAllClicks(),
            viewEvents
        )
        .compose(ReportAnalyticsEvents())
        .cast<BloodSugarSummaryViewEvent>()
  }

  private val delegate: MobiusDelegate<BloodSugarSummaryViewModel, BloodSugarSummaryViewEvent, BloodSugarSummaryViewEffect> by unsafeLazy {
    val screenKey = screenRouter.key<PatientSummaryScreenKey>(this)
    MobiusDelegate(
        events = events,
        defaultModel = BloodSugarSummaryViewModel.create(screenKey.patientUuid),
        init = BloodSugarSummaryViewInit(),
        update = BloodSugarSummaryViewUpdate(),
        effectHandler = effectHandlerFactory.create(this).build(),
        modelUpdateListener = uiRenderer::render,
        crashReporter = crashReporter
    )
  }

  init {
    LayoutInflater.from(context).inflate(R.layout.patientsummary_bloodsugarsummary_content, this, true)
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    context.injector<BloodSugarSummaryViewInjector>().inject(this)

    delegate.prepare()

    val screenDestroys: Observable<ScreenDestroyed> = detaches().map { ScreenDestroyed() }
    openEntrySheetAfterTypeIsSelected(screenDestroys)
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

  private fun addNewBloodSugarClicks(): Observable<BloodSugarSummaryViewEvent> {
    return addNewBloodSugar.clicks().map { NewBloodSugarClicked }
  }

  private fun seeAllClicks(): Observable<BloodSugarSummaryViewEvent> {
    return bloodSugarSeeAll.clicks().map { SeeAllClicked }
  }

  override fun showBloodSugarSummary(bloodSugars: List<BloodSugarMeasurement>) {
    render(bloodSugars)
  }

  override fun showNoBloodSugarsView() {
    bloodSugarItemContainer.visibility = View.GONE
    noBloodSugarTextView.visibility = View.VISIBLE
  }

  override fun showBloodSugarTypeSelector() {
    activity.startActivityForResult(BloodSugarTypePickerSheet.intent(context), TYPE_PICKER_SHEET)
  }

  override fun showSeeAllButton() {
    bloodSugarSeeAll.visibility = View.VISIBLE
  }

  override fun hideSeeAllButton() {
    bloodSugarSeeAll.visibility = View.GONE
  }

  override fun showBloodSugarHistoryScreen(patientUuid: UUID) {
    screenRouter.push(BloodSugarHistoryScreenKey(patientUuid))
  }

  override fun openBloodSugarUpdateSheet(bloodSugarMeasurementUuid: UUID, measurementType: BloodSugarMeasurementType) {
    val intent = BloodSugarEntrySheet.intentForUpdateBloodSugar(context, bloodSugarMeasurementUuid, measurementType)
    context.startActivity(intent)
  }

  @SuppressLint("CheckResult")
  private fun openEntrySheetAfterTypeIsSelected(onDestroys: Observable<ScreenDestroyed>) {
    screenRouter.streamScreenResults()
        .ofType<ActivityResult>()
        .filter { it.requestCode == TYPE_PICKER_SHEET && it.succeeded() && it.data != null }
        .takeUntil(onDestroys)
        .map { it.data!! }
        .subscribe(::showBloodSugarEntrySheet)
  }

  private fun showBloodSugarEntrySheet(intent: Intent) {
    val screenKey = screenRouter.key<PatientSummaryScreenKey>(this)
    val patientUuid = screenKey.patientUuid

    val intentForNewBloodSugar = BloodSugarEntrySheet.intentForNewBloodSugar(
        context,
        patientUuid,
        BloodSugarTypePickerSheet.selectedBloodSugarType(intent)
    )
    activity.startActivity(intentForNewBloodSugar)
  }

  private fun render(bloodSugarMeasurements: List<BloodSugarMeasurement>) {
    val listItemViews = generateBloodSugarRows(bloodSugarMeasurements)

    bloodSugarItemContainer.removeAllViews()
    listItemViews.forEach(bloodSugarItemContainer::addView)

    val itemContainerBottomPadding = if (listItemViews.size > 1) {
      R.dimen.patientsummary_blood_sugar_summary_item_container_bottom_padding_8
    } else {
      R.dimen.patientsummary_blood_sugar_summary_item_container_bottom_padding_24
    }
    bloodSugarItemContainer.setPaddingBottom(itemContainerBottomPadding)

    bloodSugarItemContainer.visibility = View.VISIBLE
    noBloodSugarTextView.visibility = View.GONE
  }

  private fun generateBloodSugarRows(bloodSugarMeasurements: List<BloodSugarMeasurement>): List<View> {
    val measurementsByDate = bloodSugarMeasurements.groupBy { item -> item.recordedAt.atZone(utcClock.zone).toLocalDate() }

    return measurementsByDate.mapValues { (_, measurementList) ->
      val hasMultipleMeasurementsInSameDate = measurementList.size > 1
      measurementList.map { measurement ->
        val isBloodSugarEditable = isBloodSugarEditable(measurement)
        val recordedAt = measurement.recordedAt.toLocalDateAtZone(userClock.zone)
        val bloodSugarTime = if (hasMultipleMeasurementsInSameDate) {
          timeFormatter.format(measurement.recordedAt.atZone(userClock.zone))
        } else {
          null
        }

        val bloodSugarItemView = LayoutInflater.from(context).inflate(R.layout.list_patientsummary_bloodsugar_measurement, this, false) as BloodSugarItemView
        bloodSugarItemView.render(
            measurement = measurement,
            bloodSugarDate = dateFormatter.format(recordedAt),
            bloodSugarTime = bloodSugarTime,
            isBloodSugarEditable = isBloodSugarEditable,
            editMeasurementClicked = { clickedMeasurement -> viewEvents.onNext(BloodSugarClicked(clickedMeasurement)) }
        )

        bloodSugarItemView
      }
    }.values.flatten()
  }

  private fun isBloodSugarEditable(measurement: BloodSugarMeasurement): Boolean {
    if (bloodSugarSummaryConfig.bloodSugarEditFeatureEnabled.not() || measurement.reading.type is Unknown) {
      return false
    }
    val now = Instant.now(utcClock)
    val createdAt = measurement.timestamps.createdAt

    val durationSinceBloodSugarCreated = Duration.between(createdAt, now)
    return durationSinceBloodSugarCreated <= bloodSugarSummaryConfig.bloodSugarEditableDuration
  }
}
