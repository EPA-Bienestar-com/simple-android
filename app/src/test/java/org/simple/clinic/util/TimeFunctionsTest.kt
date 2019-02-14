package org.simple.clinic.util

import com.google.common.truth.Truth.assertThat
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Test
import org.junit.runner.RunWith
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.Period
import org.threeten.bp.temporal.ChronoUnit

@RunWith(JUnitParamsRunner::class)
class TimeFunctionsTest {

  @Test
  @Parameters(method = "params for estimating current age from recorded age")
  fun `estimated ages from recorded age should be calculated properly`(
      utcClock: TestUtcClock,
      recordedAge: Int,
      ageRecordedAt: Instant,
      expectedEstimatedAge: Int
  ) {
    val estimatedAge = estimateCurrentAge(recordedAge, ageRecordedAt, utcClock)

    assertThat(estimatedAge).isEqualTo(expectedEstimatedAge)
  }

  @Suppress("Unused")
  private fun `params for estimating current age from recorded age`(): List<List<Any>> {
    val oneYear = Period.ofYears(1)
    val twoYears = Period.ofYears(2)
    val thirtyDays = Period.ofDays(30)

    fun daysBetweenNowAndPeriod(utcClock: TestUtcClock, period: Period): Duration {
      val now = LocalDate.now(utcClock)
      val then = LocalDate.now(utcClock).plus(period)

      return Duration.ofDays(ChronoUnit.DAYS.between(now, then))
    }

    fun generateTestData(
        year: Int,
        age: Int,
        advanceClockBy: Period,
        turnBackAgeRecordedAtBy: Period,
        expectedEstimatedAge: Int
    ): List<Any> {
      val clock = TestUtcClock()
      clock.setYear(year)

      val ageRecordedAt = Instant.now(clock).minus(daysBetweenNowAndPeriod(clock, turnBackAgeRecordedAtBy))
      clock.advanceBy(daysBetweenNowAndPeriod(clock, advanceClockBy))
      return listOf(
          clock,
          age,
          ageRecordedAt,
          expectedEstimatedAge)
    }
    return listOf(
        generateTestData(
            year = 1970,
            age = 40,
            advanceClockBy = Period.ZERO,
            expectedEstimatedAge = 40,
            turnBackAgeRecordedAtBy = Period.ZERO),
        generateTestData(
            year = 1970,
            age = 40,
            advanceClockBy = thirtyDays,
            expectedEstimatedAge = 40,
            turnBackAgeRecordedAtBy = Period.ZERO),
        generateTestData(
            year = 1970,
            age = 40,
            advanceClockBy = oneYear,
            expectedEstimatedAge = 41,
            turnBackAgeRecordedAtBy = Period.ZERO),
        generateTestData(
            year = 1970,
            age = 25,
            advanceClockBy = Period.ZERO,
            expectedEstimatedAge = 26,
            turnBackAgeRecordedAtBy = oneYear),
        generateTestData(
            year = 1971,
            age = 25,
            advanceClockBy = Period.ZERO,
            expectedEstimatedAge = 27,
            turnBackAgeRecordedAtBy = twoYears)
    )
  }

  @Test
  @Parameters(method = "params for estimating current age from recorded date of birth")
  fun `estimated ages from recorded date of birth should be calculated properly`(
      utcClock: TestUtcClock,
      recordedDateOfBirth: LocalDate,
      expectedEstimatedAge: Int
  ) {
    val estimatedAge = estimateCurrentAge(recordedDateOfBirth, utcClock)

    assertThat(estimatedAge).isEqualTo(estimatedAge)
  }
  @Suppress("Unused")
  private fun `params for estimating current age from recorded date of birth`(): List<List<Any>> {
    val oneYear = Period.ofYears(1)
    val twoYears = Period.ofYears(2)
    val thirtyDays = Period.ofDays(30)

    fun daysBetweenNowAndPeriod(utcClock: TestUtcClock, period: Period): Duration {
      val now = LocalDate.now(utcClock)
      val then = LocalDate.now(utcClock).plus(period)

      return Duration.ofDays(ChronoUnit.DAYS.between(now, then))
    }

    fun generateTestData(
        year: Int,
        recordedDateOfBirth: LocalDate,
        advanceClockBy: Period,
        expectedEstimatedAge: Int
    ): List<Any> {
      val clock = TestUtcClock()
      clock.setYear(year)
      clock.advanceBy(daysBetweenNowAndPeriod(clock, advanceClockBy))

      return listOf(
          clock,
          recordedDateOfBirth,
          expectedEstimatedAge)
    }
    return listOf(
        generateTestData(
            year = 1970,
            recordedDateOfBirth = LocalDate.parse("1930-01-01"),
            advanceClockBy = Period.ZERO,
            expectedEstimatedAge = 40),
        generateTestData(
            year = 1970,
            recordedDateOfBirth = LocalDate.parse("1930-01-01"),
            advanceClockBy = thirtyDays,
            expectedEstimatedAge = 40),
        generateTestData(
            year = 1970,
            recordedDateOfBirth = LocalDate.parse("1930-01-01"),
            advanceClockBy = oneYear,
            expectedEstimatedAge = 41),
        generateTestData(
            year = 1970,
            recordedDateOfBirth = LocalDate.parse("1945-01-01"),
            advanceClockBy = oneYear,
            expectedEstimatedAge = 26),
        generateTestData(
            year = 1970,
            recordedDateOfBirth = LocalDate.parse("1945-01-01"),
            advanceClockBy = twoYears,
            expectedEstimatedAge = 27)
    )
  }

}
