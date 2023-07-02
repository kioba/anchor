plugins {
  id("com.android.library")
  kotlin("android")
  id("org.jetbrains.kotlin.plugin.serialization") version "1.8.21"
  id("dev.kioba.anchor")
}

android {
  namespace = "dev.kioba.anchor.features.counter"

  @Suppress("UnstableApiUsage")
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.4.7"
  }
}

dependencies {
  implementation(projects.anchor)

  implementation("androidx.core:core-ktx:1.10.1")
  implementation("androidx.compose.ui:ui:1.4.3")
  implementation("androidx.compose.material3:material3:1.1.1")
  implementation("androidx.compose.ui:ui-tooling-preview:1.4.3")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
  implementation("androidx.activity:activity-compose:1.7.2")
  implementation("androidx.activity:activity-ktx:1.7.2")
  implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.21")
  debugImplementation("androidx.compose.ui:ui-tooling:1.4.3")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.5.1")

}
