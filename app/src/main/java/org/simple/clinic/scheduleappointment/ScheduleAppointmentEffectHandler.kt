package org.simple.clinic.scheduleappointment

import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import dagger.Lazy
import io.reactivex.ObservableTransformer
import org.simple.clinic.facility.Facility
import org.simple.clinic.overdue.AppointmentConfig
import org.simple.clinic.overdue.PotentialAppointmentDate
import org.simple.clinic.overdue.TimeToAppointment
import org.simple.clinic.protocol.Protocol
import org.simple.clinic.protocol.ProtocolRepository
import org.simple.clinic.util.Just
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.plus
import org.simple.clinic.util.scheduler.SchedulersProvider
import org.simple.clinic.util.toOptional
import org.threeten.bp.LocalDate

class ScheduleAppointmentEffectHandler @AssistedInject constructor(
    private val currentFacility: Lazy<Facility>,
    private val protocolRepository: ProtocolRepository,
    private val appointmentConfig: AppointmentConfig,
    private val userClock: UserClock,
    private val schedulers: SchedulersProvider,
    @Assisted private val uiActions: ScheduleAppointmentUiActions
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(uiActions: ScheduleAppointmentUiActions): ScheduleAppointmentEffectHandler
  }

  fun build(): ObservableTransformer<ScheduleAppointmentEffect, ScheduleAppointmentEvent> {
    return RxMobius
        .subtypeEffectHandler<ScheduleAppointmentEffect, ScheduleAppointmentEvent>()
        .addTransformer(LoadDefaultAppointmentDate::class.java, loadDefaultAppointmentDate())
        .addConsumer(ShowDatePicker::class.java, { uiActions.showManualDateSelector(it.selectedDate) }, schedulers.ui())
        .addTransformer(LoadCurrentFacility::class.java, loadCurrentFacility())
        .build()
  }

  private fun loadDefaultAppointmentDate(): ObservableTransformer<LoadDefaultAppointmentDate, ScheduleAppointmentEvent> {
    return ObservableTransformer { effects ->
      effects
          .map { currentProtocol(currentFacility.get()) }
          .map(::defaultTimeToAppointment)
          .map(::generatePotentialAppointmentDate)
          .map(::DefaultAppointmentDateLoaded)
    }
  }

  private fun currentProtocol(facility: Facility): Optional<Protocol> {
    return if (facility.protocolUuid == null)
      None
    else
      protocolRepository.protocolImmediate(facility.protocolUuid).toOptional()
  }

  private fun defaultTimeToAppointment(protocol: Optional<Protocol>): TimeToAppointment {
    return if (protocol is Just) {
      TimeToAppointment.Days(protocol.value.followUpDays)
    } else {
      appointmentConfig.defaultTimeToAppointment
    }
  }

  private fun generatePotentialAppointmentDate(scheduleAppointmentIn: TimeToAppointment): PotentialAppointmentDate {
    val today = LocalDate.now(userClock)
    return PotentialAppointmentDate(today.plus(scheduleAppointmentIn), scheduleAppointmentIn)
  }

  private fun loadCurrentFacility(): ObservableTransformer<LoadCurrentFacility, ScheduleAppointmentEvent> {
    return ObservableTransformer { effects ->
      effects.map { CurrentFacilityLoaded(currentFacility.get()) }
    }
  }
}
