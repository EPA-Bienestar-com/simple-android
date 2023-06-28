package org.simple.clinic.setup

import com.spotify.mobius.First
import com.spotify.mobius.Init
import org.simple.clinic.mobius.first

class SetupActivityInit(
    private val isDatabaseEncryptionEnabled: Boolean
) : Init<SetupActivityModel, SetupActivityEffect> {

  override fun init(model: SetupActivityModel): First<SetupActivityModel, SetupActivityEffect> {
    return first(model, ExecuteDatabaseEncryptionDecryption(isDatabaseEncryptionEnabled))
  }
}
