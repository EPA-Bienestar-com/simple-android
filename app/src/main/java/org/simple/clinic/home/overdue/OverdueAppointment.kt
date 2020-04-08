package org.simple.clinic.home.overdue

import androidx.room.Dao
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Query
import io.reactivex.Flowable
import io.reactivex.Observable
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.overdue.Appointment
import org.simple.clinic.patient.Age
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.PatientPhoneNumber
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.UUID

@DatabaseView(
    """
      SELECT P.fullName, P.gender, P.dateOfBirth, P.age_value, P.age_updatedAt,

          A.uuid appt_uuid, A.patientUuid appt_patientUuid, A.facilityUuid appt_facilityUuid, A.scheduledDate appt_scheduledDate, A.status appt_status,
          A.cancelReason appt_cancelReason, A.remindOn appt_remindOn, A.agreedToVisit appt_agreedToVisit, A.appointmentType appt_appointmentType,
          A.syncStatus appt_syncStatus, A.createdAt appt_createdAt, A.updatedAt appt_updatedAt, A.creationFacilityUuid appt_creationFacilityUuid,

          PPN.uuid phone_uuid, PPN.patientUuid phone_patientUuid, PPN.number phone_number, PPN.phoneType phone_phoneType, PPN.active phone_active,
          PPN.createdAt phone_createdAt, PPN.updatedAt phone_updatedAt,

          MH.hasDiabetes diagnosedWithDiabetes, MH.diagnosedWithHypertension diagnosedWithHypertension,

          (
            CASE
                WHEN BP.uuid IS NULL THEN BloodSugar.recordedAt
                WHEN BloodSugar.uuid IS NULL THEN BP.recordedAt
                ELSE MAX(BP.recordedAt, BloodSugar.recordedAt)
            END
          ) AS patientLastSeen,

          (
            CASE
              WHEN BloodSugar.reading_type = 'fasting' AND CAST (BloodSugar.reading_value AS REAL) >= 200 THEN 1
              WHEN BloodSugar.reading_type = 'random' AND CAST (BloodSugar.reading_value AS REAL) >= 300 THEN 1
              WHEN BloodSugar.reading_type = 'post_prandial' AND CAST (BloodSugar.reading_value AS REAL) >= 300 THEN 1
              WHEN BloodSugar.reading_type = 'hba1c' AND CAST (BloodSugar.reading_value AS REAL) >= 9 THEN 1
              WHEN BP.systolic >= 180 OR BP.diastolic >= 110 THEN 1
              WHEN (MH.hasHadHeartAttack = 'yes' OR MH.hasHadStroke = 'yes') AND (BP.systolic >= 140 OR BP.diastolic >= 110) 
                THEN 1 
              ELSE 0
            END
          ) AS isAtHighRisk

          FROM Patient P

          INNER JOIN Appointment A ON A.patientUuid = P.uuid
          LEFT JOIN PatientPhoneNumber PPN ON (PPN.patientUuid = P.uuid AND PPN.deletedAt IS NULL)
          LEFT JOIN MedicalHistory MH ON MH.patientUuid = P.uuid

          LEFT JOIN (
            SELECT * FROM BloodPressureMeasurement WHERE deletedAt IS NULL GROUP BY patientUuid HAVING max(recordedAt)
          ) BP ON BP.patientUuid = P.uuid

          LEFT JOIN (
            SELECT * FROM BloodSugarMeasurements WHERE deletedAt IS NULL GROUP BY patientUuid HAVING max(recordedAt)
          ) BloodSugar ON BloodSugar.patientUuid = P.uuid
          
          WHERE 
            P.deletedAt IS NULL
            AND A.deletedAt IS NULL
            AND A.status = 'scheduled'
            AND PPN.number IS NOT NULL
            AND (BP.recordedAt IS NOT NULL OR BloodSugar.recordedAt IS NOT NULL)
    """
)
data class OverdueAppointment(

    val fullName: String,

    val gender: Gender,

    val dateOfBirth: LocalDate?,

    @Embedded(prefix = "age_")
    val age: Age?,

    @Embedded(prefix = "appt_")
    val appointment: Appointment,

    @Embedded(prefix = "phone_")
    val phoneNumber: PatientPhoneNumber?,

    val isAtHighRisk: Boolean,

    val patientLastSeen: Instant,

    val diagnosedWithDiabetes: Answer?,

    val diagnosedWithHypertension: Answer?
) {

  @Dao
  interface RoomDao {

    @Query("""
      SELECT * FROM OverdueAppointment
      WHERE 
        appt_facilityUuid = :facilityUuid 
        AND (appt_scheduledDate < :scheduledBefore AND appt_scheduledDate > :scheduledAfter)
        AND (appt_remindOn < :scheduledBefore OR appt_remindOn IS NULL)
        GROUP BY appt_patientUuid
        ORDER BY isAtHighRisk DESC, appt_scheduledDate DESC, appt_updatedAt ASC
    """)
    fun overdueAtFacility(
        facilityUuid: UUID,
        scheduledBefore: LocalDate,
        scheduledAfter: LocalDate
    ): Observable<List<OverdueAppointment>>
  }
}
