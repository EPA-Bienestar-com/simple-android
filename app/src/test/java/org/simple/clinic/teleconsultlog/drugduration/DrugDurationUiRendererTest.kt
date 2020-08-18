package org.simple.clinic.teleconsultlog.drugduration

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import org.junit.Test
import org.simple.clinic.teleconsultlog.drugduration.DrugDurationValidationResult.BLANK

class DrugDurationUiRendererTest {

  private val ui = mock<DrugDurationUi>()
  private val uiRenderer = DrugDurationUiRenderer(ui)

  @Test
  fun `when drug duration validation result is not validated, then hide error`() {
    // given
    val initialDuration = ""
    val duration = "10"
    val model = DrugDurationModel.create(initialDuration)
        .durationChanged(duration)

    // when
    uiRenderer.render(model)

    // then
    verify(ui).hideDurationError()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when drug duration validation result is blank, then show error`() {
    // given
    val duration = ""
    val model = DrugDurationModel.create(duration)
        .invalid(BLANK)

    // when
    uiRenderer.render(model)

    // then
    verify(ui).showBlankDurationError()
    verifyNoMoreInteractions(ui)
  }
}
