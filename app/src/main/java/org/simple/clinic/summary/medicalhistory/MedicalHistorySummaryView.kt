package org.simple.clinic.summary.medicalhistory

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.jakewharton.rxbinding3.view.detaches
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.medicalhistory_summary_view.view.*
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.bindUiToController
import org.simple.clinic.di.injector
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.MedicalHistory
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.DIAGNOSED_WITH_DIABETES
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.DIAGNOSED_WITH_HYPERTENSION
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_HEART_ATTACK
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_KIDNEY_DISEASE
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_STROKE
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.summary.PatientSummaryScreenKey
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

class MedicalHistorySummaryView(
    context: Context,
    attributeSet: AttributeSet
) : FrameLayout(context, attributeSet), MedicalHistorySummaryUi {

  private val internalEvents = PublishSubject.create<MedicalHistorySummaryEvent>()

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var controllerFactory: MedicalHistorySummaryUiController.Factory

  @Inject
  lateinit var effectHandler: MedicalHistorySummaryEffectHandler

  init {
    LayoutInflater.from(context).inflate(R.layout.medicalhistory_summary_view, this, true)
  }

  private val screenKey by unsafeLazy { screenRouter.key<PatientSummaryScreenKey>(this) }

  private val events by unsafeLazy {
    Observable
        .merge(
            screenCreates(),
            internalEvents
        )
        .compose(ReportAnalyticsEvents())
        .share()
  }

  private val uiRenderer = MedicalHistorySummaryUiRenderer(this)

  private val delegate by unsafeLazy {
    MobiusDelegate.forView(
        events = events.ofType(),
        defaultModel = MedicalHistorySummaryModel.create(screenKey.patientUuid),
        update = MedicalHistorySummaryUpdate(),
        init = MedicalHistorySummaryInit(),
        effectHandler = effectHandler.create(),
        modelUpdateListener = uiRenderer::render
    )
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }

    context.injector<MedicalHistorySummaryViewInjector>().inject(this)

    bindUiToController(
        ui = this,
        events = events,
        controller = controllerFactory.create(screenKey.patientUuid),
        screenDestroys = detaches().map { ScreenDestroyed() }
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

  private fun screenCreates(): Observable<UiEvent> = Observable.just(ScreenCreated())

  override fun populateMedicalHistory(medicalHistory: MedicalHistory) {
    renderMedicalHistory(medicalHistory)
    renderDiagnosis(medicalHistory)
  }

  private fun renderMedicalHistory(medicalHistory: MedicalHistory) {
    heartAttackQuestionView.render(HAS_HAD_A_HEART_ATTACK, medicalHistory.hasHadHeartAttack, ::answerToggled)
    strokeQuestionView.render(HAS_HAD_A_STROKE, medicalHistory.hasHadStroke, ::answerToggled)
    kidneyDiseaseQuestionView.render(HAS_HAD_A_KIDNEY_DISEASE, medicalHistory.hasHadKidneyDisease, ::answerToggled)
    diabetesQuestionView.render(DIAGNOSED_WITH_DIABETES, medicalHistory.diagnosedWithDiabetes, ::answerToggled)
  }

  private fun renderDiagnosis(medicalHistory: MedicalHistory) {
    hypertensionDiagnosisView.render(DIAGNOSED_WITH_HYPERTENSION, medicalHistory.diagnosedWithHypertension, ::answerToggled)
    diabetesDiagnosisView.render(DIAGNOSED_WITH_DIABETES, medicalHistory.diagnosedWithDiabetes, ::answerToggled)
  }

  override fun showDiagnosisView() {
    diagnosisViewContainer.visibility = VISIBLE
    diabetesDiagnosisView.hideDivider()
  }

  override fun hideDiagnosisView() {
    diagnosisViewContainer.visibility = GONE
  }

  override fun showDiabetesHistorySection() {
    diabetesQuestionView.visibility = VISIBLE
    kidneyDiseaseQuestionView.showDivider()
    diabetesQuestionView.hideDivider()
  }

  override fun hideDiabetesHistorySection() {
    diabetesQuestionView.visibility = GONE
    kidneyDiseaseQuestionView.hideDivider()
  }

  override fun hideDiagnosisError() {
    diagnosisRequiredError.visibility = GONE
  }

  fun showDiagnosisError() {
    diagnosisRequiredError.visibility = VISIBLE
  }

  private fun answerToggled(question: MedicalHistoryQuestion, answer: Answer) {
    internalEvents.onNext(SummaryMedicalHistoryAnswerToggled(question, answer))
  }
}
