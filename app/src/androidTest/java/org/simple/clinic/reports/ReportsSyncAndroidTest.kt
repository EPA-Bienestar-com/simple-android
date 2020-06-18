package org.simple.clinic.reports

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.simple.clinic.TestClinicApp
import org.simple.clinic.reports.ReportsRepository.Companion.REPORTS_KEY
import org.simple.clinic.rules.ServerAuthenticationRule
import org.simple.clinic.storage.text.TextStore
import org.simple.clinic.util.Rules
import javax.inject.Inject


class ReportsSyncAndroidTest {

  @Inject
  lateinit var reportsRepository: ReportsRepository

  @Inject
  lateinit var reportsSync: ReportsSync

  @Inject
  lateinit var textStore: TextStore

  @get:Rule
  val ruleChain: RuleChain = Rules
      .global()
      .around(ServerAuthenticationRule())

  @Before
  fun setUp() {
    TestClinicApp.appComponent().inject(this)
  }

  @After
  fun tearDown() {
    textStore.delete(REPORTS_KEY)
  }

  @Test
  fun when_pulling_reports_from_the_server_it_should_save_the_reports_as_a_file() {
    textStore.delete(REPORTS_KEY)
    assertThat(textStore.get(REPORTS_KEY)).isNull()

    reportsSync
        .pull()
        .test()
        .assertNoErrors()

    assertThat(textStore.get(REPORTS_KEY)).isNotEmpty()
  }
}
