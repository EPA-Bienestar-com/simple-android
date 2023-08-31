package org.simple.clinic.gradle

import com.android.build.gradle.BaseExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.provideDelegate

fun Project.configureAndroid() {
  android {
    val androidNdkVersion: String by project
    val compileSdkVersion: Int by rootProject.extra
    val minSdkVersion: Int by rootProject.extra
    val targetSdkVersion: Int by rootProject.extra

    compileSdkVersion(compileSdkVersion)
    ndkVersion = androidNdkVersion

    defaultConfig {
      minSdk = minSdkVersion
      targetSdk = targetSdkVersion
    }
    compileOptions {
      // https://developer.android.com/studio/write/java8-support
      isCoreLibraryDesugaringEnabled = true
    }
  }

  dependencies {
    // https://developer.android.com/studio/write/java8-support
    "coreLibraryDesugaring"(libs.findLibrary("tools.desugarjdklibs").get())
  }
}

private fun Project.android(action: BaseExtension.() -> Unit) = extensions.configure<BaseExtension>(action)
