package org.simple.clinic.facility

import androidx.test.runner.AndroidJUnit4
import com.f2prateek.rx.preferences2.Preference
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.AuthenticationRule
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.sync.BaseSyncCoordinatorAndroidTest
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.RxErrorsRule
import javax.inject.Inject
import javax.inject.Named

/**
 * Facility sync is a special case. We only pull from it, and not push.
 *
 * However, [BaseSyncCoordinatorAndroidTest] performs a push operation even in the test for the pull
 * operation. We can't extend that test over here since facilities aren't supposed to be pushed from
 * the app at all.
 *
 * TODO: Revisit this when making a composite sync android test.
 **/
@RunWith(AndroidJUnit4::class)
class FacilitySyncAndroidTest {

  @Inject
  lateinit var repository: FacilityRepository

  @Inject
  lateinit var database: AppDatabase

  @Inject
  lateinit var userSession: UserSession

  @Inject
  @field:Named("last_facility_pull_token")
  lateinit var lastPullToken: Preference<Optional<String>>

  @Inject
  lateinit var facilitySync: FacilitySync

  @Inject
  lateinit var testData: TestData

  private val authenticationRule = AuthenticationRule()

  private val rxErrorsRule = RxErrorsRule()

  @get:Rule
  val ruleChain = RuleChain
      .outerRule(authenticationRule)
      .around(rxErrorsRule)!!

  @Before
  fun setUp() {
    TestClinicApp.appComponent().inject(this)
  }

  @Test
  fun when_pulling_facilities_then_paginate_till_the_server_does_not_have_anymore_facilities() {
    lastPullToken.set(None)

    facilitySync.pull()
        .test()
        .assertNoErrors()

    val count = database.facilityDao().count().blockingFirst()
    assertThat(count).isAtLeast(1)
  }
}
