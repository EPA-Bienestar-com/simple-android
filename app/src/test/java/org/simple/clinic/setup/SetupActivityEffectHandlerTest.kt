package org.simple.clinic.setup

import com.f2prateek.rx.preferences2.Preference
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Completable
import io.reactivex.Single
import org.junit.After
import org.junit.Test
import org.simple.clinic.appconfig.AppConfigRepository
import org.simple.clinic.mobius.EffectHandlerTestCase
import org.simple.clinic.TestData
import org.simple.clinic.user.User
import org.simple.clinic.util.Just
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import org.simple.clinic.util.toOptional
import java.util.UUID

class SetupActivityEffectHandlerTest {

  private val onboardingCompletePreference = mock<Preference<Boolean>>()
  private val uiActions = mock<UiActions>()
  private val userDao = mock<User.RoomDao>()
  private val appConfigRepository = mock<AppConfigRepository>()
  private val fallbackCountry = TestData.country()

  private val effectHandler = SetupActivityEffectHandler(
      onboardingCompletePreference,
      uiActions,
      userDao,
      appConfigRepository,
      fallbackCountry,
      TrampolineSchedulersProvider()
  ).build()

  private val testCase = EffectHandlerTestCase(effectHandler)

  @After
  fun tearDown() {
    testCase.dispose()
  }

  @Test
  fun `the user details must be fetched when the fetch user details effect is received`() {
    // given
    whenever(onboardingCompletePreference.get()) doReturn true

    val user = TestData.loggedInUser(uuid = UUID.fromString("426d2eb9-ebf7-4a62-b157-1de221c7c3d0"))
    whenever(userDao.userImmediate()).doReturn(user)

    val country = TestData.country()
    whenever(appConfigRepository.currentCountry()) doReturn country.toOptional()

    // when
    testCase.dispatch(FetchUserDetails)

    // then
    testCase.assertOutgoingEvents(UserDetailsFetched(
        hasUserCompletedOnboarding = true,
        loggedInUser = Just(user),
        userSelectedCountry = Just(country)
    ))
    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when the go to main activity effect is received, the main activity must be opened`() {
    // when
    testCase.dispatch(GoToMainActivity)

    // then
    testCase.assertNoOutgoingEvents()
    verify(uiActions).goToMainActivity()
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when the show onboarding screen effect is received, the splash screen must be shown`() {
    // when
    testCase.dispatch(ShowOnboardingScreen)

    // then
    testCase.assertNoOutgoingEvents()
    verify(uiActions).showSplashScreen()
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `when the initialize database screen effect is received, the database must be initialized`() {
    // given
    whenever(userDao.userCount()) doReturn Single.just(0)

    // when
    testCase.dispatch(InitializeDatabase)

    // then
    testCase.assertOutgoingEvents(DatabaseInitialized)
    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `the country selection screen must be opened when the show country selection effect is received`() {
    // when
    testCase.dispatch(ShowCountrySelectionScreen)

    // then
    testCase.assertNoOutgoingEvents()
    verify(uiActions).showCountrySelectionScreen()
    verifyNoMoreInteractions(uiActions)
  }

  @Test
  fun `the fallback country must be set as the selected country when the set fallback country as selected effect is received`() {
    // given
    whenever(appConfigRepository.saveCurrentCountry(fallbackCountry)) doReturn Completable.complete()

    // when
    testCase.dispatch(SetFallbackCountryAsCurrentCountry)

    // then
    testCase.assertOutgoingEvents(FallbackCountrySetAsSelected)
    verifyZeroInteractions(uiActions)
  }
}
