plugins {
  kotlin("android")
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "dev.kioba.anchor.features.counter"

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

  implementation(libs.core.ktx)
  implementation(libs.ui)
  implementation(libs.androidx.material3)
  implementation(libs.ui.tooling.preview)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.activity.ktx)
  implementation(libs.kotlin.stdlib)
  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.jetbrains.kotlinx.serialization.core.jvm)

}
