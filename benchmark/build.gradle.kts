plugins {
  id("com.android.library")
  id("androidx.benchmark")
  id("kotlin-android")
}

android {
  compileSdkVersion(versions.compileSdk)

  kotlinOptions {
    jvmTarget = "1.8"
  }

  defaultConfig {
    minSdkVersion(26)
    targetSdkVersion(versions.compileSdk)
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
  }

  testBuildType = "debug"
  buildTypes {
    getByName("debug") {
      isDebuggable = false
      isMinifyEnabled = true

      val defaultProguardFile: String by project
      proguardFiles(
          getDefaultProguardFile(defaultProguardFile),
          "benchmark-proguard-rules.pro"
      )
    }
  }
}

dependencies {
  implementation ("org.jetbrains.kotlin:kotlin-stdlib:${versions.kotlin}")
  androidTestImplementation("androidx.test:runner:${versions.androidXTest}")
  androidTestImplementation("androidx.test.ext:junit:${versions.androidXTestExt}")
  testImplementation("junit:junit:${versions.junit}")
  androidTestImplementation("androidx.benchmark:benchmark-junit4:${versions.benchmark}")
}
