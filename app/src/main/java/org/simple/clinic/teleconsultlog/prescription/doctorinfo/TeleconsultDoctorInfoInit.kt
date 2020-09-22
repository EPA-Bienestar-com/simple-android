package org.simple.clinic.teleconsultlog.prescription.doctorinfo

import com.spotify.mobius.First
import com.spotify.mobius.First.first
import com.spotify.mobius.Init

class TeleconsultDoctorInfoInit : Init<TeleconsultDoctorInfoModel, TeleconsultDoctorInfoEffect> {

  override fun init(model: TeleconsultDoctorInfoModel): First<TeleconsultDoctorInfoModel, TeleconsultDoctorInfoEffect> {
    val effects = mutableSetOf<TeleconsultDoctorInfoEffect>(LoadSignatureBitmap)
    if (model.hasMedicalRegistrationId) {
      effects.add(SetMedicalRegistrationId(model.medicalRegistrationId!!))
    } else {
      effects.add(LoadMedicalRegistrationId)
    }

    if (model.hasUser.not()) {
      effects.add(LoadCurrentUser)
    }

    return first(model, effects)
  }
}
