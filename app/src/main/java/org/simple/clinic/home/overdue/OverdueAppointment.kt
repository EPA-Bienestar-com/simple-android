package org.simple.clinic.home.overdue

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Ignore
import androidx.room.Query
import io.reactivex.Flowable
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.home.overdue.OverdueAppointment.RiskLevel
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.overdue.Appointment
import org.simple.clinic.patient.Age
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.PatientPhoneNumber
import org.threeten.bp.LocalDate
import java.util.UUID

data class OverdueAppointment(

    val fullName: String,

    val gender: Gender,

    val dateOfBirth: LocalDate?,

    @Embedded(prefix = "age_")
    val age: Age?,

    @Embedded(prefix = "appt_")
    val appointment: Appointment,

    @Embedded(prefix = "bp_")
    val bloodPressure: BloodPressureMeasurement,

    @Embedded(prefix = "phone_")
    val phoneNumber: PatientPhoneNumber?,

    /**
     * Referencing a column alias in the same SQL query isn't allowed so
     * mapping this index to [RiskLevel] is manually done using [riskLevel].
     * Also see [isAtHighRisk].
     */
    @Deprecated(
        message = "This property is meant for internal use only. Use riskLevel instead.",
        replaceWith = ReplaceWith("riskLevel")
    )
    val riskLevelIndex: Int
) {

  @Ignore
  val isAtHighRisk = riskLevelIndex <= RiskLevel.HIGH.levelIndex

  @delegate:Ignore
  val riskLevel by lazy {
    RiskLevel.values().first { it.levelIndex == this.riskLevelIndex }
  }

  enum class RiskLevel(val levelIndex: Int) {
    HIGHEST(0),
    VERY_HIGH(1),
    HIGH(2),
    REGULAR(3),
    LOW(4),
    NONE(5);
  }

  @Dao
  interface RoomDao {

    /**
     * FYI: IntelliJ's SQL parser highlights `riskLevelIndex` as an error, but it's not. This is probably
     * because referencing column aliases in a WHERE clause is not SQL standard, but sqlite still allows it.
     */
    @Query("""
          SELECT P.fullName, P.gender, P.dateOfBirth, P.age_value, P.age_updatedAt,

          BP.uuid bp_uuid, BP.systolic bp_systolic, BP.diastolic bp_diastolic, BP.syncStatus bp_syncStatus, BP.userUuid bp_userUuid,
          BP.facilityUuid bp_facilityUuid, BP.patientUuid bp_patientUuid, BP.createdAt bp_createdAt, BP.updatedAt bp_updatedAt, BP.recordedAt bp_recordedAt,

          A.uuid appt_uuid, A.patientUuid appt_patientUuid, A.facilityUuid appt_facilityUuid, A.scheduledDate appt_scheduledDate, A.status appt_status,
          A.cancelReason appt_cancelReason, A.remindOn appt_remindOn, A.agreedToVisit appt_agreedToVisit, A.appointmentType appt_appointmentType,
          A.syncStatus appt_syncStatus, A.createdAt appt_createdAt, A.updatedAt appt_updatedAt, A.creationFacilityUuid appt_creationFacilityUuid,

          PPN.uuid phone_uuid, PPN.patientUuid phone_patientUuid, PPN.number phone_number, PPN.phoneType phone_phoneType, PPN.active phone_active,
          PPN.createdAt phone_createdAt, PPN.updatedAt phone_updatedAt,

          (
            CASE
              WHEN A.scheduledDate > :minimumOverdueDateForHighRisk THEN 5
              WHEN BP.systolic >= 180 OR BP.diastolic >= 110 THEN 0
              WHEN MH.hasHadHeartAttack = :yesAnswer
                OR MH.hasHadStroke = :yesAnswer
                THEN 1
              ELSE 5
            END
          ) AS riskLevelIndex

          FROM Patient P

          INNER JOIN Appointment A ON A.patientUuid = P.uuid
          INNER JOIN BloodPressureMeasurement BP ON (BP.patientUuid = P.uuid AND BP.deletedAt IS NULL)
          LEFT JOIN PatientPhoneNumber PPN ON (PPN.patientUuid = P.uuid AND PPN.deletedAt IS NULL)
          LEFT JOIN MedicalHistory MH ON MH.patientUuid = P.uuid

          WHERE 
            P.deletedAt IS NULL 
            AND A.deletedAt IS NULL
            AND A.facilityUuid = :facilityUuid
            AND A.status = :scheduledStatus
            AND A.scheduledDate < :scheduledBefore
            AND A.scheduledDate > :scheduledAfter
            AND PPN.number IS NOT NULL
            AND (A.remindOn < :scheduledBefore OR A.remindOn IS NULL)

          GROUP BY P.uuid HAVING max(BP.recordedAt)
          ORDER BY riskLevelIndex ASC, A.scheduledDate DESC, A.updatedAt ASC
          """)
    fun appointmentsForFacility(
        facilityUuid: UUID,
        scheduledStatus: Appointment.Status,
        scheduledBefore: LocalDate,
        scheduledAfter: LocalDate,
        yesAnswer: Answer = Answer.Yes,
        minimumOverdueDateForHighRisk: LocalDate
    ): Flowable<List<OverdueAppointment>>
  }
}
