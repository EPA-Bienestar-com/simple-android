package org.simple.clinic.summary.linkId

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.view.detaches
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.link_id_with_patient_view.view.*
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.bindUiToController
import org.simple.clinic.di.injector
import org.simple.clinic.main.TheActivity
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.text.style.TextAppearanceWithLetterSpacingSpan
import org.simple.clinic.util.Truss
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.animateBottomSheetIn
import org.simple.clinic.widgets.animateBottomSheetOut
import javax.inject.Inject

/**
 *
 * One thing different about this bottom sheet, and the others is that
 * if this sheet is closed without linking the passport with the patient,
 * the summary sheet should also be closed.
 *
 * There was a weird bug happening where [Flow] would attempt to recreate
 * the state of the activity when it resumed before the command to pop the
 * summary screen would be invoked, which caused the link ID sheet to open
 * twice.
 *
 * There was a workaround to fix this by rewriting [Flow]'s history
 * before opening the [LinkIdWithPatientSheet] to one where the open
 * intention was [ViewExistingPatient], but it was a very wrong hack since
 * we might also need to use the original intention later.
 *
 * In addition, the screen result from [LinkIdWithBottomSheet] was being
 * delivered to [TheActivity] **BEFORE** the [PatientSummaryScreen] could
 * subscribe to the results stream, which would not allow the screen to
 * close when the [LinkIdWithBottomSheet] was closed as well. We cannot use
 * the imperative  * way of delivering results via the [ScreenRouter] because
 * that instance is scoped to [TheActivity] and is not accessible to
 * [LinkIdWithBottomSheet].
 *
 * Since this feature was a little time sensitive, instead of spending
 * time to make it work as a [BottomSheetActivity], we made the decision to
 * implement it as a child view of [PatientSummaryScreen] instead. This
 * means that [Flow] neither tries to recreate the history as well as the
 * summary screen can directly observe the results from
 * [LinkIdWithPatientView].
 *
 * A consequence of this, is that:
 * - All behaviour that comes with the [BottomSheetActivity] (animations,
 * background click handling) will have to be reimplemented here.
 * - Unlike other bottom sheets, this one will not cover the status bar.
 */
class LinkIdWithPatientView(
    context: Context,
    attributeSet: AttributeSet
) : FrameLayout(context, attributeSet), LinkIdWithPatientViewUi {

  @Inject
  lateinit var controller: LinkIdWithPatientViewController

  @Inject
  lateinit var effectHandlerFactory: LinkIdWithPatientEffectHandler.Factory

  val downstreamUiEvents: Subject<UiEvent> = PublishSubject.create()
  private val upstreamUiEvents: Subject<UiEvent> = PublishSubject.create()

  private val events by unsafeLazy {
    Observable
        .merge(
            viewShows(),
            addClicks(),
            cancelClicks(),
            downstreamUiEvents
        )
        .compose(ReportAnalyticsEvents())
        .share()
  }

  private val delegate by unsafeLazy {
    MobiusDelegate.forView(
        events = events.ofType(),
        defaultModel = LinkIdWithPatientModel.create(),
        update = LinkIdWithPatientUpdate(),
        effectHandler = effectHandlerFactory.create(this).build()
    )
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

  @SuppressLint("CheckResult")
  override fun onFinishInflate() {
    super.onFinishInflate()
    View.inflate(context, R.layout.link_id_with_patient_view, this)
    if (isInEditMode) {
      return
    }

    context.injector<Injector>().inject(this)

    backgroundView.setOnClickListener {
      // Intentionally done to swallow click events.
    }

    bindUiToController(
        ui = this,
        events = events,
        controller = controller,
        screenDestroys = detaches().map { ScreenDestroyed() }
    )
  }

  private fun viewShows(): Observable<UiEvent> {
    return downstreamUiEvents
        .ofType<LinkIdWithPatientViewShown>()
        .map { it as UiEvent }
  }

  private fun addClicks(): Observable<UiEvent> {
    return addButton.clicks().map { LinkIdWithPatientAddClicked }
  }

  private fun cancelClicks(): Observable<UiEvent> {
    return cancelButton.clicks().map { LinkIdWithPatientCancelClicked }
  }

  fun uiEvents(): Observable<UiEvent> = upstreamUiEvents.hide()

  override fun renderIdentifierText(identifier: Identifier) {
    val identifierType = identifier.displayType(resources)
    val identifierValue = identifier.displayValue()

    val identifierTextAppearanceSpan = TextAppearanceWithLetterSpacingSpan(context, R.style.Clinic_V2_TextAppearance_Body0Left_NumericBold_Grey0)

    idTextView.text = Truss()
        .append(resources.getString(R.string.linkidwithpatient_add_id_text, identifierType))
        .pushSpan(identifierTextAppearanceSpan)
        .append(identifierValue)
        .popSpan()
        .append(resources.getString(R.string.linkidwithpatient_to_patient_text))
        .build()
  }

  override fun closeSheetWithIdLinked() {
    upstreamUiEvents.onNext(LinkIdWithPatientLinked)
  }

  override fun closeSheetWithoutIdLinked() {
    upstreamUiEvents.onNext(LinkIdWithPatientCancelled)
  }

  fun show(runBefore: () -> Unit) {
    animateBottomSheetIn(
        backgroundView = backgroundView,
        contentContainer = contentContainer,
        startAction = runBefore
    )
  }

  fun hide(runAfter: () -> Unit) {
    animateBottomSheetOut(
        backgroundView = backgroundView,
        contentContainer = contentContainer,
        endAction = runAfter
    )
  }

  interface Injector {
    fun inject(target: LinkIdWithPatientView)
  }
}
