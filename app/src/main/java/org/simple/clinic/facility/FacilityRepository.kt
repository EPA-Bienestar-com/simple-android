package org.simple.clinic.facility

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.toObservable
import org.simple.clinic.di.AppScope
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.canBeOverriddenByServerCopy
import org.simple.clinic.sync.SynceableRepository
import org.simple.clinic.user.User
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.Optional
import org.simple.clinic.util.toOptional
import java.util.UUID
import javax.inject.Inject

@AppScope
class FacilityRepository @Inject constructor(
    private val facilityDao: Facility.RoomDao,
    private val userDao: User.RoomDao
) : SynceableRepository<Facility, FacilityPayload> {

  fun facilities(searchQuery: String = ""): Observable<List<Facility>> {
    return if (searchQuery.isBlank()) {
      facilityDao.all().toObservable()
    } else {
      facilityDao.filteredByName(searchQuery).toObservable()
    }
  }

  fun facilitiesInCurrentGroup(searchQuery: String = "", user: User): Observable<List<Facility>> {
    val filteredByName = {
      if (searchQuery.isBlank()) {
        facilityDao.all().toObservable()
      } else {
        facilityDao.filteredByName(searchQuery).toObservable()
      }
    }

    val filteredByNameAndGroup = { group: UUID ->
      if (searchQuery.isBlank()) {
        facilityDao.filteredByGroup(group).toObservable()
      } else {
        facilityDao.filteredByNameAndGroup(searchQuery, group).toObservable()
      }
    }

    return currentFacility(user)
        .switchMap { current ->
          when {
            current.groupUuid == null -> filteredByName()
            else -> filteredByNameAndGroup(current.groupUuid)
          }
        }
  }

  fun setCurrentFacility(user: User, facility: Facility): Completable {
    return setCurrentFacility(user, facility.uuid)
  }

  fun setCurrentFacility(user: User, facilityUuid: UUID): Completable {
    return Completable.fromAction { userDao.setCurrentFacility(user.uuid, facilityUuid) }
  }

  @Deprecated(
      message = "Use currentFacility(User) instead",
      replaceWith = ReplaceWith("userSession.requireLoggedInUser().switchMap { currentFacility(it) }"))
  fun currentFacility(userSession: UserSession): Observable<Facility> {
    return userSession.requireLoggedInUser()
        .switchMap { currentFacility(it) }
  }

  fun currentFacility(user: User): Observable<Facility> {
    return userDao.currentFacility(user.uuid).toObservable()
  }

  fun currentFacilityImmediate(user: User): Facility? {
    return userDao.currentFacilityImmediate(user.uuid)
  }

  fun currentFacilityUuid(user: User): UUID? {
    return userDao.currentFacilityUuid(user.uuid)
  }

  override fun mergeWithLocalData(payloads: List<FacilityPayload>): Completable {
    return payloads
        .toObservable()
        .filter { payload ->
          val localCopy = facilityDao.getOne(payload.uuid)
          localCopy?.syncStatus.canBeOverriddenByServerCopy()
        }
        .map { it.toDatabaseModel(SyncStatus.DONE) }
        .toList()
        .flatMapCompletable { Completable.fromAction { facilityDao.save(it) } }
  }

  override fun save(records: List<Facility>): Completable {
    return Completable.fromAction { facilityDao.save(records) }
  }

  override fun recordsWithSyncStatus(syncStatus: SyncStatus): Single<List<Facility>> {
    return facilityDao.withSyncStatus(syncStatus).firstOrError()
  }

  override fun setSyncStatus(from: SyncStatus, to: SyncStatus): Completable {
    return Completable.fromAction { facilityDao.updateSyncStatus(oldStatus = from, newStatus = to) }
  }

  override fun setSyncStatus(ids: List<UUID>, to: SyncStatus): Completable {
    if (ids.isEmpty()) {
      throw AssertionError("IDs must not be empty!")
    }

    return Completable.fromAction { facilityDao.updateSyncStatus(uuids = ids, newStatus = to) }
  }

  override fun recordCount(): Observable<Int> {
    return facilityDao.count().toObservable()
  }

  override fun pendingSyncRecordCount(): Observable<Int> {
    return facilityDao.count(SyncStatus.PENDING).toObservable()
  }

  fun facility(uuid: UUID): Optional<Facility> {
    return facilityDao.getOne(uuid).toOptional()
  }
}
