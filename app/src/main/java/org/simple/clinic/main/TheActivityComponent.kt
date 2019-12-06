package org.simple.clinic.main

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.f2prateek.rx.preferences2.Preference
import com.f2prateek.rx.preferences2.RxSharedPreferences
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import io.reactivex.Observable
import org.simple.clinic.activity.ActivityLifecycle
import org.simple.clinic.activity.BindsActivity
import org.simple.clinic.activity.BindsScreenRouter
import org.simple.clinic.activity.RxActivityLifecycle
import org.simple.clinic.addidtopatient.searchforpatient.AddIdToPatientSearchScreen
import org.simple.clinic.addidtopatient.searchresults.AddIdToPatientSearchResultsScreen
import org.simple.clinic.allpatientsinfacility.AllPatientsInFacilityView
import org.simple.clinic.bp.entry.BloodPressureEntrySheet
import org.simple.clinic.bp.entry.confirmremovebloodpressure.ConfirmRemoveBloodPressureDialog
import org.simple.clinic.bp.entry.di.BloodPressureEntryModule
import org.simple.clinic.di.AssistedInjectModule
import org.simple.clinic.drugs.selection.PrescribedDrugScreen
import org.simple.clinic.drugs.selection.dosage.DosagePickerSheet
import org.simple.clinic.drugs.selection.entry.CustomPrescriptionEntrySheet
import org.simple.clinic.drugs.selection.entry.confirmremovedialog.ConfirmRemovePrescriptionDialog
import org.simple.clinic.editpatient.ConfirmDiscardChangesDialog
import org.simple.clinic.editpatient.EditPatientScreen
import org.simple.clinic.enterotp.EnterOtpScreen
import org.simple.clinic.facility.change.FacilityChangeScreen
import org.simple.clinic.forgotpin.confirmpin.ForgotPinConfirmPinScreen
import org.simple.clinic.forgotpin.createnewpin.ForgotPinCreateNewPinScreen
import org.simple.clinic.home.HomeScreen
import org.simple.clinic.home.help.HelpScreen
import org.simple.clinic.home.overdue.OverdueScreen
import org.simple.clinic.home.overdue.appointmentreminder.AppointmentReminderSheet
import org.simple.clinic.home.overdue.phonemask.PhoneMaskBottomSheet
import org.simple.clinic.home.overdue.removepatient.RemoveAppointmentScreen
import org.simple.clinic.home.patients.PatientsModule
import org.simple.clinic.home.patients.PatientsScreen
import org.simple.clinic.home.patients.PatientsScreenKey
import org.simple.clinic.home.report.ReportsScreen
import org.simple.clinic.login.applock.AppLockScreen
import org.simple.clinic.login.applock.ConfirmResetPinDialog
import org.simple.clinic.login.pin.LoginPinScreen
import org.simple.clinic.medicalhistory.newentry.NewMedicalHistoryScreen
import org.simple.clinic.newentry.PatientEntryScreen
import org.simple.clinic.onboarding.OnboardingScreenInjector
import org.simple.clinic.recentpatient.RecentPatientsScreen
import org.simple.clinic.recentpatientsview.RecentPatientsView
import org.simple.clinic.registration.confirmpin.RegistrationConfirmPinScreen
import org.simple.clinic.registration.facility.RegistrationFacilitySelectionScreen
import org.simple.clinic.registration.location.RegistrationLocationPermissionScreen
import org.simple.clinic.registration.name.RegistrationFullNameScreen
import org.simple.clinic.registration.phone.RegistrationPhoneScreen
import org.simple.clinic.registration.phone.loggedout.LoggedOutOfDeviceDialog
import org.simple.clinic.registration.pin.RegistrationPinScreen
import org.simple.clinic.registration.register.RegistrationLoadingScreen
import org.simple.clinic.remoteconfig.RemoteConfigService
import org.simple.clinic.scanid.ScanSimpleIdScreen
import org.simple.clinic.scheduleappointment.ScheduleAppointmentSheet
import org.simple.clinic.search.PatientSearchScreen
import org.simple.clinic.search.results.PatientSearchResultsScreen
import org.simple.clinic.searchresultsview.PatientSearchView
import org.simple.clinic.searchresultsview.SearchResultsModule
import org.simple.clinic.security.pin.PinEntryCardView
import org.simple.clinic.settings.SettingsScreen
import org.simple.clinic.settings.changelanguage.ChangeLanguageScreen
import org.simple.clinic.shortcodesearchresult.ShortCodeSearchResultScreen
import org.simple.clinic.summary.PatientSummaryScreen
import org.simple.clinic.summary.addphone.AddPhoneNumberDialog
import org.simple.clinic.summary.linkId.LinkIdWithPatientView
import org.simple.clinic.summary.updatephone.UpdatePhoneNumberDialog
import org.simple.clinic.sync.indicator.SyncIndicatorView
import org.simple.clinic.util.preference.InstantRxPreferencesConverter
import org.simple.clinic.widgets.PatientSearchResultItemView
import org.simple.clinic.widgets.qrcodescanner.QrCodeScannerView
import org.threeten.bp.Instant
import javax.inject.Named

@Subcomponent(modules = [TheActivityModule::class])
interface TheActivityComponent : OnboardingScreenInjector {

  fun inject(target: TheActivity)
  fun inject(target: HomeScreen)
  fun inject(target: PatientsScreen)
  fun inject(target: LoginPinScreen)
  fun inject(target: AppLockScreen)
  fun inject(target: OverdueScreen)
  fun inject(target: PatientEntryScreen)
  fun inject(target: PatientSearchScreen)
  fun inject(target: PatientSearchResultsScreen)
  fun inject(target: PatientSummaryScreen)
  fun inject(target: BloodPressureEntrySheet)
  fun inject(target: CustomPrescriptionEntrySheet)
  fun inject(target: RegistrationPhoneScreen)
  fun inject(target: RegistrationFullNameScreen)
  fun inject(target: RegistrationPinScreen)
  fun inject(target: RegistrationConfirmPinScreen)
  fun inject(target: RegistrationLocationPermissionScreen)
  fun inject(target: RegistrationFacilitySelectionScreen)
  fun inject(target: FacilityChangeScreen)
  fun inject(target: EnterOtpScreen)
  fun inject(target: ScheduleAppointmentSheet)
  fun inject(target: ConfirmResetPinDialog)
  fun inject(target: ForgotPinCreateNewPinScreen)
  fun inject(target: ForgotPinConfirmPinScreen)
  fun inject(target: AppointmentReminderSheet)
  fun inject(target: RemoveAppointmentScreen)
  fun inject(target: NewMedicalHistoryScreen)
  fun inject(target: PinEntryCardView)
  fun inject(target: ConfirmDiscardChangesDialog)
  fun inject(target: UpdatePhoneNumberDialog)
  fun inject(target: ConfirmRemoveBloodPressureDialog)
  fun inject(target: DosagePickerSheet)
  fun inject(target: PrescribedDrugScreen)
  fun inject(target: ConfirmRemovePrescriptionDialog)
  fun inject(target: ReportsScreen)
  fun inject(target: ScanSimpleIdScreen)
  fun inject(target: QrCodeScannerView)
  fun inject(target: RecentPatientsView)
  fun inject(target: PatientsScreenKey)
  fun inject(target: SyncIndicatorView)
  fun inject(target: AddPhoneNumberDialog)
  fun inject(target: HelpScreen)
  fun inject(target: AddIdToPatientSearchScreen)
  fun inject(target: PatientSearchView)
  fun inject(target: AddIdToPatientSearchResultsScreen)
  fun inject(target: LinkIdWithPatientView)
  fun inject(target: RecentPatientsScreen)
  fun inject(target: PhoneMaskBottomSheet)
  fun inject(target: PatientSearchResultItemView)
  fun inject(target: AllPatientsInFacilityView)
  fun inject(target: RegistrationLoadingScreen)
  fun inject(target: LoggedOutOfDeviceDialog)
  fun inject(target: ShortCodeSearchResultScreen)
  fun inject(target: EditPatientScreen)
  fun inject(target: SettingsScreen)
  fun inject(target: ChangeLanguageScreen)

  @Subcomponent.Builder
  interface Builder : BindsActivity<Builder>, BindsScreenRouter<Builder> {

    fun build(): TheActivityComponent
  }
}

@Module(includes = [
  PatientsModule::class,
  SearchResultsModule::class,
  BloodPressureEntryModule::class,
  AssistedInjectModule::class
])
class TheActivityModule {

  @Provides
  fun theActivityLifecycle(activity: AppCompatActivity): Observable<ActivityLifecycle> {
    return RxActivityLifecycle.from(activity).stream()
  }

  @Provides
  @Named("should_lock_after")
  fun lastAppStopTimestamp(rxSharedPrefs: RxSharedPreferences): Preference<Instant> {
    return rxSharedPrefs.getObject("should_lock_after", Instant.MAX, InstantRxPreferencesConverter())
  }

  @Provides
  fun fragmentManager(activity: AppCompatActivity): FragmentManager = activity.supportFragmentManager

  @Provides
  fun provideTheActivityConfig(configService: RemoteConfigService): TheActivityConfig {
    return TheActivityConfig.read(configService.reader())
  }
}
