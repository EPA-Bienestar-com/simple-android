package org.simple.clinic.scanid

import com.google.common.truth.Truth.assertThat
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.scanid.ShortCodeValidationResult.Empty
import org.simple.clinic.scanid.ShortCodeValidationResult.NotEqualToRequiredLength
import org.simple.clinic.scanid.ShortCodeValidationResult.Success

@RunWith(JUnitParamsRunner::class)
class ShortCodeInputTest {

  @Parameters(value = [
    "3",
    "34",
    "345",
    "3456",
    "34567",
    "345678",
    "34567890"
  ])
  @Test
  fun `when short code length is not equal to 7 then validation should fail`(input: String) {
    //given
    val shortCodeInput = ShortCodeInput(shortCodeText = input)

    //when
    val result = shortCodeInput.validate()

    //then
    assertThat(result)
        .isEqualTo(NotEqualToRequiredLength)
  }

  @Test
  fun `when short code length is equal to 7 then validation should succeed`() {
    //given
    val shortCodeInput = ShortCodeInput(shortCodeText = "3456789")

    //when
    val result = shortCodeInput.validate()

    //then
    assertThat(result)
        .isEqualTo(Success)
  }

  @Test
  fun `when short code is empty, then validation should fail with empty`() {
    // when
    val result = ShortCodeInput("").validate()

    // then
    assertThat(result)
        .isEqualTo(Empty)
  }
}
