package org.simple.clinic.summary.medicalhistory

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.cardview.widget.CardView
import com.jakewharton.rxbinding3.view.detaches
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.medicalhistory_summary_view.view.*
import org.simple.clinic.R
import org.simple.clinic.bindUiToController
import org.simple.clinic.di.injector
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.MedicalHistory
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.DIAGNOSED_WITH_HYPERTENSION
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_DIABETES
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_HEART_ATTACK
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_KIDNEY_DISEASE
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_STROKE
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.IS_ON_TREATMENT_FOR_HYPERTENSION
import org.simple.clinic.medicalhistory.MedicalHistoryQuestionView_Old
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.summary.PatientSummaryScreenKey
import org.simple.clinic.util.RelativeTimestamp
import org.simple.clinic.util.RelativeTimestampGenerator
import org.simple.clinic.util.UserClock
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named

class MedicalHistorySummaryView(
    context: Context,
    attributeSet: AttributeSet
) : CardView(context, attributeSet), MedicalHistorySummaryUi {

  private val internalEvents = PublishSubject.create<MedicalHistorySummaryEvent>()

  @Inject
  lateinit var timestampGenerator: RelativeTimestampGenerator

  @Inject
  lateinit var userClock: UserClock

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var controllerFactory: MedicalHistorySummaryUiController.Factory

  @field:[Inject Named("exact_date")]
  lateinit var exactDateFormatter: DateTimeFormatter

  init {
    LayoutInflater.from(context).inflate(R.layout.medicalhistory_summary_view, this, true)
    diabetesQuestionView.hideDivider()
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }

    context.injector<MedicalHistorySummaryViewInjector>().inject(this)

    val screenKey = screenRouter.key<PatientSummaryScreenKey>(this)

    bindUiToController(
        ui = this,
        events = Observable.merge(screenCreates(), internalEvents),
        controller = controllerFactory.create(screenKey.patientUuid),
        screenDestroys = detaches().map { ScreenDestroyed() }
    )
  }

  private fun screenCreates(): Observable<UiEvent> = Observable.just(ScreenCreated())

  override fun populateMedicalHistory(medicalHistory: MedicalHistory) {
    bind(
        medicalHistory = medicalHistory,
        lastUpdatedAt = timestampGenerator.generate(medicalHistory.updatedAt, userClock),
        dateFormatter = exactDateFormatter
    )
  }

  private fun bind(
      medicalHistory: MedicalHistory,
      lastUpdatedAt: RelativeTimestamp,
      dateFormatter: DateTimeFormatter
  ) {
    val updatedAtDisplayText = lastUpdatedAt.displayText(context, dateFormatter)

    lastUpdatedAtTextView.text = context.getString(
        R.string.patientsummary_medicalhistory_last_updated,
        updatedAtDisplayText
    )

    diagnosedForHypertensionQuestionView.render(DIAGNOSED_WITH_HYPERTENSION, medicalHistory.diagnosedWithHypertension, ::answerToggled)
    treatmentForHypertensionQuestionView.render(IS_ON_TREATMENT_FOR_HYPERTENSION, medicalHistory.isOnTreatmentForHypertension, ::answerToggled)
    heartAttackQuestionView.render(HAS_HAD_A_HEART_ATTACK, medicalHistory.hasHadHeartAttack, ::answerToggled)
    strokeQuestionView.render(HAS_HAD_A_STROKE, medicalHistory.hasHadStroke, ::answerToggled)
    kidneyDiseaseQuestionView.render(HAS_HAD_A_KIDNEY_DISEASE, medicalHistory.hasHadKidneyDisease, ::answerToggled)
    diabetesQuestionView.render(HAS_DIABETES, medicalHistory.hasDiabetes, ::answerToggled)
  }

  private fun answerToggled(question: MedicalHistoryQuestion, answer: Answer) {
    internalEvents.onNext(SummaryMedicalHistoryAnswerToggled(question, answer))
  }

  private fun MedicalHistoryQuestionView_Old.render(
      question: MedicalHistoryQuestion,
      answer: Answer,
      answerToggled: (MedicalHistoryQuestion, Answer) -> Unit
  ) {
    render(question, answer)
    answerChangeListener = { newAnswer -> answerToggled.invoke(question, newAnswer) }
  }
}
