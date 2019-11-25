package org.simple.clinic.overdue

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import org.simple.clinic.facility.Facility
import org.simple.clinic.home.overdue.OverdueAppointment
import org.simple.clinic.overdue.Appointment.AppointmentType
import org.simple.clinic.overdue.Appointment.Status.Cancelled
import org.simple.clinic.overdue.Appointment.Status.Scheduled
import org.simple.clinic.overdue.Appointment.Status.Visited
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.canBeOverriddenByServerCopy
import org.simple.clinic.sync.SynceableRepository
import org.simple.clinic.util.Just
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.UtcClock
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset
import java.util.UUID
import javax.inject.Inject

class AppointmentRepository @Inject constructor(
    private val appointmentDao: Appointment.RoomDao,
    private val overdueDao: OverdueAppointment.RoomDao,
    private val utcClock: UtcClock,
    private val appointmentConfigProvider: Observable<AppointmentConfig>
) : SynceableRepository<Appointment, AppointmentPayload> {

  fun schedule(
      patientUuid: UUID,
      appointmentUuid: UUID,
      appointmentDate: LocalDate,
      appointmentType: AppointmentType,
      currentFacility: Facility
  ): Single<Appointment> {
    val newAppointmentStream = Single.just(Appointment(
        uuid = appointmentUuid,
        patientUuid = patientUuid,
        facilityUuid = currentFacility.uuid,
        scheduledDate = appointmentDate,
        status = Scheduled,
        cancelReason = null,
        remindOn = null,
        agreedToVisit = null,
        appointmentType = appointmentType,
        syncStatus = SyncStatus.PENDING,
        createdAt = Instant.now(utcClock),
        updatedAt = Instant.now(utcClock),
        deletedAt = null)
    ).flatMap { appointment ->
      save(listOf(appointment)).andThen(Single.just(appointment))
    }

    return markOlderAppointmentsAsVisited(patientUuid).andThen(newAppointmentStream)
  }

  private fun markOlderAppointmentsAsVisited(patientUuid: UUID): Completable {
    return Completable.fromAction {
      appointmentDao.markOlderAppointmentsAsVisited(
          patientUuid = patientUuid,
          updatedStatus = Visited,
          scheduledStatus = Scheduled,
          newSyncStatus = SyncStatus.PENDING,
          newUpdatedAt = Instant.now(utcClock)
      )
    }
  }

  fun createReminder(appointmentUuid: UUID, reminderDate: LocalDate): Completable {
    return Completable.fromAction {
      appointmentDao.saveRemindDate(
          appointmentUUID = appointmentUuid,
          reminderDate = reminderDate,
          newSyncStatus = SyncStatus.PENDING,
          newUpdatedAt = Instant.now(utcClock)
      )
    }
  }

  fun markAsAgreedToVisit(appointmentUuid: UUID, userClock: UserClock): Completable {
    return Completable.fromAction {
      appointmentDao.markAsAgreedToVisit(
          appointmentUUID = appointmentUuid,
          reminderDate = LocalDate.now(userClock).plusMonths(1),
          newSyncStatus = SyncStatus.PENDING,
          newUpdatedAt = Instant.now(utcClock))
    }
  }

  fun markAsAlreadyVisited(appointmentUuid: UUID): Completable {
    return Completable.fromAction {
      appointmentDao.markAsVisited(
          appointmentUuid = appointmentUuid,
          newStatus = Visited,
          newSyncStatus = SyncStatus.PENDING,
          newUpdatedAt = Instant.now(utcClock))
    }
  }

  fun cancelWithReason(appointmentUuid: UUID, reason: AppointmentCancelReason): Completable {
    return Completable.fromAction {
      appointmentDao.cancelWithReason(
          appointmentUuid = appointmentUuid,
          cancelReason = reason,
          newStatus = Cancelled,
          newSyncStatus = SyncStatus.PENDING,
          newUpdatedAt = Instant.now(utcClock))
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

  fun overdueAppointments(
      since: LocalDate,
      facility: Facility
  ): Observable<List<OverdueAppointment>> {
    return appointmentConfigProvider
        .flatMap { appointmentConfig ->
          overdueDao
              .appointmentsForFacility(
                  facilityUuid = facility.uuid,
                  scheduledStatus = Scheduled,
                  scheduledBefore = since,
                  scheduledAfter = since.minusMonths(12),
                  minimumOverdueDateForHighRisk = since.minus(appointmentConfig.minimumOverduePeriodForHighRisk),
                  overdueDateForLowestRiskLevel = since.minus(appointmentConfig.overduePeriodForLowestRiskLevel)
              ).toObservable()
        }
  }

  fun lastCreatedAppointmentForPatient(patientUuid: UUID): Observable<Optional<Appointment>> {
    return appointmentDao.lastCreatedAppointmentForPatient(patientUuid)
        .toObservable()
        .map { appointments ->
          when {
            appointments.isNotEmpty() -> Just(appointments.first())
            else -> None
          }
        }
  }

  fun markAppointmentsCreatedBeforeTodayAsVisited(patientUuid: UUID): Completable {
    val startOfToday = LocalDate
        .now(utcClock)
        .atStartOfDay()
        .toInstant(ZoneOffset.of(utcClock.zone.id))

    return Completable.fromAction {
      appointmentDao.markAsVisited(
          patientUuid = patientUuid,
          updatedStatus = Visited,
          scheduledStatus = Scheduled,
          newSyncStatus = SyncStatus.PENDING,
          newUpdatedAt = Instant.now(utcClock),
          createdBefore = startOfToday
      )
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
          appointmentType = appointmentType,
          syncStatus = syncStatus,
          createdAt = createdAt,
          updatedAt = updatedAt,
          deletedAt = deletedAt)
    }
  }

  override fun pendingSyncRecordCount(): Observable<Int> {
    return appointmentDao
        .count(SyncStatus.PENDING)
        .toObservable()
  }
}
