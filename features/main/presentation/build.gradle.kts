plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("dev.kioba.anchor")
}

android {
  namespace = "dev.kioba.anchor.features.main.presentation"

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

  debugImplementation("androidx.compose.ui:ui-tooling:1.4.3")
  implementation("androidx.compose.ui:ui:1.4.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
}
