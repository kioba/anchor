plugins {
  alias(libs.plugins.androidLibrary)
}

android {
  namespace = "dev.kioba.anchor.features.main.presentation"

  buildFeatures {
    compose = true
  }

  compileSdk = 34

  defaultConfig {
    minSdk = 21

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

}

dependencies {
  implementation(projects.anchor)

  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.ui)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
}
