package org.simple.clinic.util

import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.impl.NameBasedGenerator
import org.threeten.bp.LocalDate
import java.util.UUID

// TODO(vs): 2019-12-05 Delete this file and the associated library
fun createUuid5(name: String): UUID {
  val nameBasedGenerator = Generators.nameBasedGenerator(NameBasedGenerator.NAMESPACE_DNS)
  return nameBasedGenerator.generate(name)
}

fun generateEncounterUuid(facilityUuid: UUID, patientUuid: UUID, encounteredDate: LocalDate): UUID {
  val uuidName = facilityUuid.toString() + patientUuid + encounteredDate
  return createUuid5(uuidName)
}
