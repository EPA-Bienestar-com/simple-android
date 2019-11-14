package org.simple.clinic.registration

import dagger.Module
import dagger.Provides
import io.reactivex.Single
import org.simple.clinic.registration.phone.IndianPhoneNumberValidator
import org.simple.clinic.registration.phone.PhoneNumberValidator
import org.simple.clinic.util.Distance
import org.threeten.bp.Duration
import retrofit2.Retrofit
import javax.inject.Named

@Module
class RegistrationModule {

  @Provides
  fun api(@Named("for_country") retrofit: Retrofit): RegistrationApi {
    return retrofit.create(RegistrationApi::class.java)
  }

  @Provides
  fun config(): Single<RegistrationConfig> {
    return Single.just(RegistrationConfig(
        locationListenerExpiry = Duration.ofSeconds(5),
        locationUpdateInterval = Duration.ofSeconds(1),
        proximityThresholdForNearbyFacilities = Distance.ofKilometers(2.0),
        staleLocationThreshold = Duration.ofMinutes(10)))
  }

  @Provides
  fun phoneNumberValidator(): PhoneNumberValidator {
    // In the future, we will want to return a validator depending upon the location.
    return IndianPhoneNumberValidator()
  }
}
