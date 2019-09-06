package org.simple.clinic.patient

import com.f2prateek.rx.preferences2.Preference
import com.f2prateek.rx.preferences2.RxSharedPreferences
import dagger.Module
import dagger.Provides
import java.util.Locale
import javax.inject.Named

// SimpleVideoModule class is used to show a training video on home screen.
// The URL is hard-coded right now which means the video view on UI is fixed as well.
// This includes the video title, thumbnail and duration.
// At this point, we are not sure what this training video section would look like in the future
// so we are keeping this implementation as simple as possible.
@Module
class SimpleVideoModule {

  @Provides
  @Named("number_of_patients_registered")
  fun provideCountOfRegisteredPatients(rxSharedPreferences: RxSharedPreferences): Preference<Int> {
    return rxSharedPreferences.getInteger("number_of_patients_registered", 0)
  }

  @Provides
  @Named("training_video_youtube_id")
  fun provideSimpleVideoUrlBasedOnLocale(locale: Locale): String {
    return when (locale.language) {
      "hi" -> "nHsQ06tiLzw"
      // Default to English
      else -> "YO3D1paAuqU"
    }
  }
}
