package org.simple.clinic.medicalhistory.newentry

import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import org.simple.clinic.facility.Facility
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.medicalhistory.MedicalHistoryRepository
import org.simple.clinic.medicalhistory.OngoingMedicalHistoryEntry
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.user.User
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.scheduler.SchedulersProvider

class NewMedicalHistoryEffectHandler @AssistedInject constructor(
    private val schedulersProvider: SchedulersProvider,
    @Assisted private val uiActions: NewMedicalHistoryUiActions,
    private val userSession: UserSession,
    private val facilityRepository: FacilityRepository,
    private val patientRepository: PatientRepository,
    private val medicalHistoryRepository: MedicalHistoryRepository
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(uiActions: NewMedicalHistoryUiActions): NewMedicalHistoryEffectHandler
  }

  fun build(): ObservableTransformer<NewMedicalHistoryEffect, NewMedicalHistoryEvent> {
    return RxMobius
        .subtypeEffectHandler<NewMedicalHistoryEffect, NewMedicalHistoryEvent>()
        .addConsumer(OpenPatientSummaryScreen::class.java, { effect -> uiActions.openPatientSummaryScreen(effect.patientUuid) }, schedulersProvider.ui())
        .addTransformer(RegisterPatient::class.java, registerPatient(schedulersProvider.io()))
        .addTransformer(LoadOngoingPatientEntry::class.java, loadOngoingNewPatientEntry(schedulersProvider.io()))
        .build()
  }

  private fun registerPatient(scheduler: Scheduler): ObservableTransformer<RegisterPatient, NewMedicalHistoryEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(scheduler)
          .flatMap { registerPatientEffect ->
            val loggedInUser = userSession.loggedInUserImmediate()
            requireNotNull(loggedInUser)

            facilityRepository
                .currentFacility(loggedInUser)
                .take(1)
                .map { facility ->
                  val ongoingMedicalHistoryEntry = registerPatientEffect.ongoingMedicalHistoryEntry

                  RegisterPatientData(loggedInUser, facility, ongoingMedicalHistoryEntry)
                }
          }
          .flatMapSingle { (user, facility, ongoingMedicalHistoryEntry) ->
            patientRepository.saveOngoingEntryAsPatient(user, facility)
                .flatMap { registeredPatient ->
                  medicalHistoryRepository
                      .save(registeredPatient.uuid, ongoingMedicalHistoryEntry)
                      .toSingleDefault(PatientRegistered(registeredPatient.uuid))
                }
          }
    }
  }

  private fun loadOngoingNewPatientEntry(scheduler: Scheduler): ObservableTransformer<LoadOngoingPatientEntry, NewMedicalHistoryEvent> {
    return ObservableTransformer { effects ->
      effects
          .flatMapSingle { patientRepository.ongoingEntry().subscribeOn(scheduler) }
          .map(::OngoingPatientEntryLoaded)
    }
  }

  private data class RegisterPatientData(
      val user: User,
      val facility: Facility,
      val ongoingMedicalHistoryEntry: OngoingMedicalHistoryEntry
  )
}
