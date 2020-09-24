package org.simple.clinic.teleconsultlog.drugduration

import com.spotify.mobius.test.NextMatchers.hasEffects
import com.spotify.mobius.test.NextMatchers.hasModel
import com.spotify.mobius.test.NextMatchers.hasNoEffects
import com.spotify.mobius.test.NextMatchers.hasNoModel
import com.spotify.mobius.test.UpdateSpec
import com.spotify.mobius.test.UpdateSpec.assertThatNext
import org.junit.Test
import java.time.Duration

class DrugDurationUpdateTest {

  private val config = DrugDurationConfig(
      maxAllowedDuration = Duration.ofDays(1000)
  )
  private val validator = DrugDurationValidator(config)
  private val updateSpec = UpdateSpec(DrugDurationUpdate(validator))

  @Test
  fun `hide drug duration error when drug duration changes`() {
    val duration = "10"
    val model = DrugDurationModel.create(duration)

    updateSpec
        .given(model)
        .whenEvent(DurationChanged(duration))
        .then(assertThatNext(
            hasModel(model.durationChanged(duration)),
            hasNoEffects()
        ))
  }

  @Test
  fun `show drug duration error when drug duration is blank`() {
    val duration = ""
    val model = DrugDurationModel.create(duration)

    updateSpec
        .given(model)
        .whenEvent(DrugDurationSaveClicked)
        .then(assertThatNext(
            hasModel(model.durationInvalid(Blank)),
            hasNoEffects()
        ))
  }

  @Test
  fun `show drug duration error when drug duration is more than max allowed drug duration`() {
    val duration = "1001"
    val model = DrugDurationModel.create(duration)

    updateSpec
        .given(model)
        .whenEvent(DrugDurationSaveClicked)
        .then(assertThatNext(
            hasModel(model.durationInvalid(MaxDrugDuration(1000))),
            hasNoEffects()
        ))
  }

  @Test
  fun `save drug duration when drug duration is not empty`() {
    val duration = "20"

    updateSpec
        .given(DrugDurationModel.create(duration))
        .whenEvent(DrugDurationSaveClicked)
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(SaveDrugDuration(duration.toInt()) as DrugDurationEffect)
        ))
  }
}
