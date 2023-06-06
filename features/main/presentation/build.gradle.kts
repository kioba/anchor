plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("dev.kioba.anchor")
}

android {
  namespace = "dev.kioba.anchor.features.main.presentation"
}

dependencies {
  implementation(projects.anchor)
  debugImplementation("androidx.compose.ui:ui-tooling:1.4.3")
}