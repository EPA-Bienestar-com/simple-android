package org.simple.clinic.medicalhistory.newentry

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.medicalhistory.Answer.No
import org.simple.clinic.medicalhistory.Answer.Unanswered
import org.simple.clinic.medicalhistory.Answer.Yes
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.DIAGNOSED_WITH_HYPERTENSION
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.DIAGNOSED_WITH_DIABETES
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_HEART_ATTACK
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_KIDNEY_DISEASE
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_STROKE
import org.simple.clinic.medicalhistory.MedicalHistoryRepository
import org.simple.clinic.medicalhistory.OngoingMedicalHistoryEntry
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.OngoingNewPatientEntry
import org.simple.clinic.patient.OngoingNewPatientEntry.PersonalDetails
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import org.simple.clinic.widgets.UiEvent
import org.simple.mobius.migration.MobiusTestFixture
import java.util.UUID

class NewMedicalHistoryScreenLogicTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val screen: NewMedicalHistoryUi = mock()
  private val uiActions: NewMedicalHistoryUiActions = mock()
  private val viewRenderer = NewMedicalHistoryUiRenderer(screen)
  private val medicalHistoryRepository: MedicalHistoryRepository = mock()
  private val facilityRepository = mock<FacilityRepository>()
  private val patientRepository: PatientRepository = mock()
  private val userSession = mock<UserSession>()

  private val uiEvents = PublishSubject.create<UiEvent>()
  private val user = PatientMocker.loggedInUser(uuid = UUID.fromString("4eb3d692-7362-4b10-848a-a7d679aee23a"))
  private val facility = PatientMocker.facility(uuid = UUID.fromString("6fc07446-c508-47e7-998e-8c475f9114d1"))
  private val patientUuid = UUID.fromString("d4f0fb3a-0146-4bc6-afec-95b76c61edca")

  private lateinit var testFixture: MobiusTestFixture<NewMedicalHistoryModel, NewMedicalHistoryEvent, NewMedicalHistoryEffect>

  @Before
  fun setUp() {
    whenever(medicalHistoryRepository.save(eq(patientUuid), any())).thenReturn(Completable.complete())
    whenever(patientRepository.ongoingEntry()).thenReturn(Single.never())
    whenever(userSession.loggedInUserImmediate()).thenReturn(user)
    whenever(facilityRepository.currentFacility(user)).thenReturn(Observable.just(facility))

    val effectHandler = NewMedicalHistoryEffectHandler(
        schedulersProvider = TrampolineSchedulersProvider(),
        uiActions = uiActions,
        userSession = userSession,
        facilityRepository = facilityRepository,
        patientRepository = patientRepository,
        medicalHistoryRepository = medicalHistoryRepository
    ).build()

    testFixture = MobiusTestFixture(
        events = uiEvents.ofType(),
        defaultModel = NewMedicalHistoryModel.default(),
        init = NewMedicalHistoryInit(),
        update = NewMedicalHistoryUpdate(),
        effectHandler = effectHandler,
        modelUpdateListener = viewRenderer::render
    )
  }

  @After
  fun tearDown() {
    testFixture.dispose()
  }

  @Test
  fun `when screen is started then the patient's name should be shown on the toolbar`() {
    val patientName = "Ashok Kumar"
    val patientEntry = OngoingNewPatientEntry(personalDetails = PersonalDetails(
        fullName = patientName,
        dateOfBirth = null,
        age = "20",
        gender = Gender.Transgender))
    whenever(patientRepository.ongoingEntry()).thenReturn(Single.just(patientEntry))

    startMobiusLoop()

    // This gets set twice:
    // 1. When we read the patient entry
    // 2. When we load the current facility and update the model
    verify(screen, times(2)).setPatientName(patientName)
  }

  @Test
  fun `when save is clicked with selected answers then patient with the answers should be saved and summary screen should be opened`() {
    // given
    val savedPatient = PatientMocker.patient(uuid = patientUuid)
    whenever(patientRepository.saveOngoingEntryAsPatient(user, facility)).thenReturn(Single.just(savedPatient))

    // when
    startMobiusLoop()

    uiEvents.onNext(NewMedicalHistoryAnswerToggled(DIAGNOSED_WITH_HYPERTENSION, No))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(HAS_HAD_A_HEART_ATTACK, No))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(HAS_HAD_A_STROKE, No))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(HAS_HAD_A_KIDNEY_DISEASE, Yes))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(DIAGNOSED_WITH_DIABETES, Yes))
    uiEvents.onNext(SaveMedicalHistoryClicked())

    // then
    with(inOrder(medicalHistoryRepository, patientRepository, uiActions)) {
      verify(patientRepository).saveOngoingEntryAsPatient(user, facility)
      verify(medicalHistoryRepository).save(
          patientUuid = savedPatient.uuid,
          historyEntry = OngoingMedicalHistoryEntry(
              hasHadHeartAttack = No,
              hasHadStroke = No,
              hasHadKidneyDisease = Yes,
              diagnosedWithHypertension = No,
              hasDiabetes = Yes
          )
      )
      verify(uiActions).openPatientSummaryScreen(savedPatient.uuid)
    }
  }

  @Test
  fun `when save is clicked with no answers then patient with an empty medical history should be saved and summary screen should be opened`() {
    // given
    val savedPatient = PatientMocker.patient(uuid = patientUuid)
    whenever(patientRepository.saveOngoingEntryAsPatient(user, facility)).thenReturn(Single.just(savedPatient))

    // when
    startMobiusLoop()

    uiEvents.onNext(SaveMedicalHistoryClicked())

    // then
    with(inOrder(medicalHistoryRepository, patientRepository, uiActions)) {
      verify(patientRepository).saveOngoingEntryAsPatient(user, facility)
      verify(medicalHistoryRepository).save(
          patientUuid = savedPatient.uuid,
          historyEntry = OngoingMedicalHistoryEntry(
              // We currently default the hypertension diagnosis answer to 'Yes' if the facility
              // does not support diabetes management. The mock facility we use in tests has DM
              // off by default, so this is hidden behaviour.
              hasHadHeartAttack = Unanswered,
              hasHadStroke = Unanswered,
              hasHadKidneyDisease = Unanswered,
              diagnosedWithHypertension = Yes,
              hasDiabetes = Unanswered))
      verify(uiActions).openPatientSummaryScreen(savedPatient.uuid)
    }
  }

  @Test
  fun `when an already selected answer for a question is changed, the new answer should be used when saving the medical history`() {
    // given
    val savedPatient = PatientMocker.patient(uuid = patientUuid)
    whenever(patientRepository.saveOngoingEntryAsPatient(user, facility)).thenReturn(Single.just(savedPatient))

    // when
    startMobiusLoop()

    // Initial answers
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(DIAGNOSED_WITH_HYPERTENSION, No))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(HAS_HAD_A_HEART_ATTACK, No))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(HAS_HAD_A_STROKE, No))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(HAS_HAD_A_KIDNEY_DISEASE, Yes))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(DIAGNOSED_WITH_DIABETES, Yes))

    // Updated answers
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(DIAGNOSED_WITH_HYPERTENSION, Yes))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(HAS_HAD_A_HEART_ATTACK, Unanswered))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(HAS_HAD_A_STROKE, Unanswered))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(HAS_HAD_A_KIDNEY_DISEASE, No))
    uiEvents.onNext(NewMedicalHistoryAnswerToggled(DIAGNOSED_WITH_DIABETES, No))

    uiEvents.onNext(SaveMedicalHistoryClicked())

    // then
    with(inOrder(medicalHistoryRepository, patientRepository, uiActions)) {
      verify(patientRepository).saveOngoingEntryAsPatient(user, facility)
      verify(medicalHistoryRepository).save(
          patientUuid = savedPatient.uuid,
          historyEntry = OngoingMedicalHistoryEntry(
              hasHadHeartAttack = Unanswered,
              hasHadStroke = Unanswered,
              hasHadKidneyDisease = No,
              diagnosedWithHypertension = Yes,
              hasDiabetes = No
          )
      )
      verify(uiActions).openPatientSummaryScreen(savedPatient.uuid)
    }
  }

  private fun startMobiusLoop() {
    testFixture.start()
  }
}
