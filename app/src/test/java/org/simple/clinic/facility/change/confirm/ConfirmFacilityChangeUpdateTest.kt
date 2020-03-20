package org.simple.clinic.facility.change.confirm

import com.spotify.mobius.test.NextMatchers.hasEffects
import com.spotify.mobius.test.NextMatchers.hasNoModel
import com.spotify.mobius.test.UpdateSpec
import com.spotify.mobius.test.UpdateSpec.assertThatNext
import org.junit.Test
import org.simple.clinic.TestData
import java.util.UUID

class ConfirmFacilityChangeUpdateTest {

  @Test
  fun `when facility change is confirmed then user's facility should be changed`() {
    val updateSpec = UpdateSpec<ConfirmFacilityChangeModel, ConfirmFacilityChangeEvent, ConfirmFacilityChangeEffect>(ConfirmFacilityChangeUpdate())
    val selectedFacility = TestData.facility(UUID.fromString("ef47531f-9b8c-4045-8578-eda31f0952c4"))
    updateSpec
        .given(ConfirmFacilityChangeModel())
        .whenEvent(FacilityChangeConfirmed(selectedFacility))
        .then(
            assertThatNext(
                hasNoModel(),
                hasEffects(ChangeFacilityEffect(selectedFacility) as ConfirmFacilityChangeEffect)
            )
        )
  }
}
