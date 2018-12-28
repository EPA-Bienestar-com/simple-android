package org.simple.clinic.overdue

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.Observables
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.home.overdue.OverdueAppointment
import org.simple.clinic.overdue.Appointment.Status.CANCELLED
import org.simple.clinic.overdue.Appointment.Status.SCHEDULED
import org.simple.clinic.overdue.Appointment.Status.VISITED
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.canBeOverriddenByServerCopy
import org.simple.clinic.sync.SynceableRepository
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.Just
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.threeten.bp.Clock
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.UUID
import javax.inject.Inject

class AppointmentRepository @Inject constructor(
    private val appointmentDao: Appointment.RoomDao,
    private val overdueDao: OverdueAppointment.RoomDao,
    private val userSession: UserSession,
    private val facilityRepository: FacilityRepository,
    private val clock: Clock,
    private val appointmentConfigProvider: Single<AppointmentConfig>
) : SynceableRepository<Appointment, AppointmentPayload> {

  fun schedule(patientUuid: UUID, appointmentDate: LocalDate): Completable {
    val newAppointmentStream = facilityRepository
        .currentFacility(userSession)
        .take(1)
        .map { facility ->
          Appointment(
              uuid = UUID.randomUUID(),
              patientUuid = patientUuid,
              facilityUuid = facility.uuid,
              scheduledDate = appointmentDate,
              status = SCHEDULED,
              cancelReason = null,
              remindOn = null,
              agreedToVisit = null,
              syncStatus = SyncStatus.PENDING,
              createdAt = Instant.now(clock),
              updatedAt = Instant.now(clock),
              deletedAt = null)
        }
        .flatMapCompletable { save(listOf(it)) }

    return markOlderAppointmentsAsVisited(patientUuid).andThen(newAppointmentStream)
  }

  private fun markOlderAppointmentsAsVisited(patientUuid: UUID): Completable {
    return Completable.fromAction {
      appointmentDao.markOlderAppointmentsAsVisited(
          patientUuid = patientUuid,
          updatedStatus = VISITED,
          scheduledStatus = SCHEDULED,
          newSyncStatus = SyncStatus.PENDING,
          newUpdatedAt = Instant.now(clock)
      )
    }
  }

  fun createReminder(appointmentUuid: UUID, reminderDate: LocalDate): Completable {
    return Completable.fromAction {
      appointmentDao.saveRemindDate(
          appointmentUUID = appointmentUuid,
          reminderDate = reminderDate,
          newSyncStatus = SyncStatus.PENDING,
          newUpdatedAt = Instant.now(clock)
      )
    }
  }

  fun markAsAgreedToVisit(appointmentUuid: UUID): Completable {
    return Completable.fromAction {
      appointmentDao.markAsAgreedToVisit(
          appointmentUUID = appointmentUuid,
          reminderDate = LocalDate.now(clock).plusDays(30),
          newSyncStatus = SyncStatus.PENDING,
          newUpdatedAt = Instant.now(clock))
    }
  }

  fun markAsAlreadyVisited(appointmentUuid: UUID): Completable {
    return Completable.fromAction {
      appointmentDao.markAsVisited(
          appointmentUuid = appointmentUuid,
          newStatus = VISITED,
          newSyncStatus = SyncStatus.PENDING,
          newUpdatedAt = Instant.now(clock))
    }
  }

  fun cancelWithReason(appointmentUuid: UUID, reason: AppointmentCancelReason): Completable {
    return Completable.fromAction {
      appointmentDao.cancelWithReason(
          appointmentUuid = appointmentUuid,
          cancelReason = reason,
          newStatus = CANCELLED,
          newSyncStatus = SyncStatus.PENDING,
          newUpdatedAt = Instant.now(clock))
    }
  }

  override fun save(records: List<Appointment>): Completable {
    return Completable.fromAction {
      appointmentDao.save(records)
    }
  }

  override fun recordCount(): Observable<Int> {
    return appointmentDao.count().toObservable()
  }

  fun overdueAppointments(): Observable<List<OverdueAppointment>> {
    val facilityUuidStream = facilityRepository.currentFacility(userSession)
        .map { it.uuid }

    val appointmentConfigStream = appointmentConfigProvider.toObservable()

    return Observables.combineLatest(facilityUuidStream, appointmentConfigStream)
        .flatMap { (facilityUuid, appointmentConfig) ->
          val today = LocalDate.now(clock)
          overdueDao
              .appointmentsForFacility(
                  facilityUuid = facilityUuid,
                  scheduledStatus = SCHEDULED,
                  scheduledBefore = today,
                  minimumOverdueDateForHighRisk = today.minus(appointmentConfig.minimumOverduePeriodForHighRisk),
                  overdueDateForLowestRiskLevel = today.minus(appointmentConfig.overduePeriodForLowestRiskLevel)
              ).toObservable()
        }
  }

  fun scheduledAppointmentForPatient(patientUuid: UUID): Observable<Optional<Appointment>> {
    return appointmentDao
        .scheduledAppointmentForPatient(patientUuid = patientUuid, status = SCHEDULED)
        .toObservable()
        .map { appointments ->
          when {
            appointments.isNotEmpty() -> Just(appointments.first())
            else -> None
          }
        }
  }

  override fun recordsWithSyncStatus(syncStatus: SyncStatus): Single<List<Appointment>> {
    return appointmentDao.recordsWithSyncStatus(syncStatus).firstOrError()
  }

  override fun setSyncStatus(from: SyncStatus, to: SyncStatus): Completable {
    return Completable.fromAction { appointmentDao.updateSyncStatus(from, to) }
  }

  override fun setSyncStatus(ids: List<UUID>, to: SyncStatus): Completable {
    if (ids.isEmpty()) {
      throw AssertionError()
    }
    return Completable.fromAction { appointmentDao.updateSyncStatus(ids, to) }
  }

  override fun mergeWithLocalData(payloads: List<AppointmentPayload>): Completable {
    val newOrUpdatedAppointments = payloads
        .asSequence()
        .filter { payload ->
          val localCopy = appointmentDao.getOne(payload.uuid)
          localCopy?.syncStatus.canBeOverriddenByServerCopy()
        }
        .map { toDatabaseModel(it, SyncStatus.DONE) }
        .toList()

    return Completable.fromAction { appointmentDao.save(newOrUpdatedAppointments) }
  }

  private fun toDatabaseModel(payload: AppointmentPayload, syncStatus: SyncStatus): Appointment {
    return payload.run {
      Appointment(
          uuid = uuid,
          patientUuid = patientUuid,
          facilityUuid = facilityUuid,
          scheduledDate = date,
          status = status,
          cancelReason = cancelReason,
          remindOn = remindOn,
          agreedToVisit = agreedToVisit,
          syncStatus = syncStatus,
          createdAt = createdAt,
          updatedAt = updatedAt,
          deletedAt = deletedAt)
    }
  }
}
