plugins {
  `kotlin-dsl`
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

dependencies {
  compileOnly(libs.android.gradle.plugin)
  compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
  plugins {
    register("androidApplication") {
      id = "simple.android.application"
      implementationClass = "org.simple.clinic.gradle.AndroidApplicationConventionPlugin"
    }
  }
}
