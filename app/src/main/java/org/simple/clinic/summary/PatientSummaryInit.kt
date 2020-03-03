package org.simple.clinic.summary

import com.spotify.mobius.First
import com.spotify.mobius.First.first
import com.spotify.mobius.Init
import org.simple.clinic.summary.OpenIntention.LinkIdWithPatient

class PatientSummaryInit : Init<PatientSummaryModel, PatientSummaryEffect> {

  override fun init(model: PatientSummaryModel): First<PatientSummaryModel, PatientSummaryEffect> {
    val effects = mutableSetOf<PatientSummaryEffect>()

    if(!model.hasLoadedPatientSummaryProfile) {
      effects.add(LoadPatientSummaryProfile(model.patientUuid))
    }

    if(!model.hasLoadedCurrentFacility) {
      effects.add(LoadCurrentFacility)
    }

    if(!model.hasCheckedForInvalidPhone) {
      effects.add(CheckForInvalidPhone(model.patientUuid))
    }

    if(!model.linkIdWithPatientViewShown && model.openIntention is LinkIdWithPatient) {
      effects.add(ShowLinkIdWithPatientView(model.patientUuid, model.openIntention.identifier))
    }

    return first(model, effects)
  }
}
