package org.simple.clinic.di

import android.app.Application
import android.content.res.Resources
import dagger.Module
import dagger.Provides
import org.simple.clinic.appconfig.AppConfigModule
import org.simple.clinic.di.network.NetworkModule
import org.simple.clinic.login.LoginModule
import org.simple.clinic.onboarding.OnboardingModule
import org.simple.clinic.patient.shortcode.UuidShortCodeCreatorModule
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.platform.crash.NoOpCrashReporter
import org.simple.clinic.registration.RegistrationModule
import org.simple.clinic.security.pin.BruteForceProtectionModule
import org.simple.clinic.sync.SyncModule
import org.simple.clinic.util.identifierdisplay.IdentifierDisplayAdapterModule
import org.simple.clinic.util.scheduler.DefaultSchedulersProvider
import org.simple.clinic.util.scheduler.SchedulersProvider
import org.threeten.bp.ZoneId
import org.threeten.bp.ZoneOffset
import java.util.Locale

@Module(includes = [
  TestStorageModule::class,
  TestClockModule::class,
  AppConfigModule::class,
  TestPatientSearchModule::class,
  BruteForceProtectionModule::class,
  IdentifierDisplayAdapterModule::class,
  LoginModule::class,
  NetworkModule::class,
  RegistrationModule::class,
  OnboardingModule::class,
  TestRetrofitModule::class,
  TestRemoteConfigModule::class,
  SyncModule::class,
  UuidShortCodeCreatorModule::class,
  DateFormatterModule::class
])
class TestAppModule(private val application: Application) {

  @Provides
  fun application(): Application = application

  @Provides
  fun schedulersProvider(): SchedulersProvider = DefaultSchedulersProvider()

  @Provides
  fun crashReporter(): CrashReporter = NoOpCrashReporter()

  @Provides
  fun zoneId(): ZoneId = ZoneOffset.UTC

  @Provides
  fun locale(): Locale = Locale.ENGLISH

  @Provides
  fun resources(): Resources = application.resources
}
