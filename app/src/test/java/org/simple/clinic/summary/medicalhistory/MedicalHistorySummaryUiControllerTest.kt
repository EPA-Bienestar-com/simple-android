package org.simple.clinic.summary.medicalhistory

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.facility.Facility
import org.simple.clinic.facility.FacilityConfig
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion
import org.simple.clinic.medicalhistory.MedicalHistoryRepository
import org.simple.clinic.TestData
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.TestUtcClock
import org.simple.clinic.util.randomMedicalHistoryAnswer
import org.simple.clinic.util.toOptional
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Instant
import java.util.UUID

@RunWith(JUnitParamsRunner::class)
class MedicalHistorySummaryUiControllerTest {

  private val patientUuid = UUID.fromString("31665de6-0265-4e33-888f-526bdb274699")
  private val medicalHistory = TestData.medicalHistory(
      uuid = UUID.fromString("6182e51f-13b3-47d5-a479-bee127070814"),
      patientUuid = patientUuid,
      updatedAt = Instant.parse("2018-01-01T00:00:00Z")
  )
  private val user = TestData.loggedInUser(uuid = UUID.fromString("80305d68-3b8d-4b16-8d1c-a9a87e88b227"))
  private val facilityWithDiabetesManagementEnabled = TestData.facility(
      uuid = UUID.fromString("90bedaf8-5521-490e-b725-2b41839a83c7"),
      facilityConfig = FacilityConfig(diabetesManagementEnabled = true)
  )
  private val facilityWithDiabetesManagementDisabled = TestData.facility(
      uuid = UUID.fromString("7c1708a2-585c-4e80-adaa-6544368a46c4"),
      facilityConfig = FacilityConfig(diabetesManagementEnabled = false)
  )

  private val medicalHistoryRepository = mock<MedicalHistoryRepository>()
  private val ui = mock<MedicalHistorySummaryUi>()
  private val userSession = mock<UserSession>()
  private val facilityRepository = mock<FacilityRepository>()
  private val clock = TestUtcClock()

  private val events = PublishSubject.create<UiEvent>()

  private lateinit var controllerSubscription: Disposable

  @After
  fun tearDown() {
    controllerSubscription.dispose()
  }

  @Test
  fun `patient's medical history should be populated`() {
    // given
    whenever(medicalHistoryRepository.historyForPatientOrDefault(patientUuid)) doReturn Observable.just(medicalHistory)

    // when
    setupController()

    // then
    verify(ui).hideDiagnosisView()
    verify(ui).showDiabetesHistorySection()
    verify(ui).populateMedicalHistory(medicalHistory)
    verifyNoMoreInteractions(ui)
  }

  @Test
  @Parameters(method = "medicalHistoryQuestionsAndAnswers")
  fun `when answers for medical history questions are toggled, then the updated medical history should be saved`(
      question: MedicalHistoryQuestion,
      newAnswer: Answer
  ) {
    // given
    val medicalHistory = TestData.medicalHistory(
        diagnosedWithHypertension = Answer.Unanswered,
        hasHadHeartAttack = Answer.Unanswered,
        hasHadStroke = Answer.Unanswered,
        hasHadKidneyDisease = Answer.Unanswered,
        hasDiabetes = Answer.Unanswered,
        updatedAt = Instant.parse("2017-12-31T00:00:00Z")
    )
    val updatedMedicalHistory = medicalHistory.answered(question, newAnswer)
    val now = Instant.now(clock)

    whenever(medicalHistoryRepository.historyForPatientOrDefault(patientUuid)) doReturn Observable.just(medicalHistory)
    whenever(medicalHistoryRepository.save(updatedMedicalHistory, now)) doReturn Completable.complete()

    // when
    setupController()
    events.onNext(SummaryMedicalHistoryAnswerToggled(question, answer = newAnswer))

    // then
    verify(medicalHistoryRepository).save(updatedMedicalHistory, now)
  }

  @Test
  fun `when the current facility supports diabetes management, show the diagnosis view and hide the diabetes history question`() {
    // given
    whenever(medicalHistoryRepository.historyForPatientOrDefault(patientUuid)) doReturn Observable.just(medicalHistory)

    // when
    setupController(facility = facilityWithDiabetesManagementEnabled)

    // then
    verify(ui).populateMedicalHistory(medicalHistory)
    verify(ui).showDiagnosisView()
    verify(ui).hideDiabetesHistorySection()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the current facility does not support diabetes management, hide the diagnosis view and show the diabetes history question`() {
    // given
    whenever(medicalHistoryRepository.historyForPatientOrDefault(patientUuid)) doReturn Observable.just(medicalHistory)

    // when
    setupController()

    // then
    verify(ui).populateMedicalHistory(medicalHistory)
    verify(ui).hideDiagnosisView()
    verify(ui).showDiabetesHistorySection()
    verifyNoMoreInteractions(ui)
  }

  @Suppress("unused")
  fun medicalHistoryQuestionsAndAnswers(): List<List<Any>> {
    val questions = MedicalHistoryQuestion.values().asList()
    return questions
        .map { question -> listOf(question, randomMedicalHistoryAnswer()) }
        .toList()
  }

  private fun setupController(facility: Facility = facilityWithDiabetesManagementDisabled) {
    whenever(userSession.loggedInUser()) doReturn Observable.just(user.toOptional())
    whenever(facilityRepository.currentFacility(user)) doReturn Observable.just(facility)

    val controller = MedicalHistorySummaryUiController(patientUuid, medicalHistoryRepository, userSession, facilityRepository, clock)
    controllerSubscription = events.compose(controller).subscribe { it.invoke(ui) }

    events.onNext(ScreenCreated())
  }
}
