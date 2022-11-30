enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":app",
    ":mobius-migration",
    ":mobius-base",
    ":simple-platform",
    ":simple-visuals",
    ":lint",
    ":sharedTestCode"
)

plugins {
  id("com.gradle.enterprise") version("3.11.4")
}

gradleEnterprise {
  buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
  }
}
