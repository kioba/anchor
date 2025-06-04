import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

kotlin {
  androidTarget {
    publishLibraryVariants("release")
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_11)
    }
  }
}

android {

  namespace = "dev.kioba.anchor.features.main.ui"

  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.android.minSdk.get().toInt()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  debugImplementation(libs.compose.uiTooling)
  implementation(libs.compose.activity)
  implementation(libs.android.activity)
  implementation(libs.compose.material3Android)
  implementation(libs.android.core)
  implementation(libs.kotlin.stdlib)
  implementation(libs.compose.ui)
  implementation(libs.compose.uiToolingPreview)
  implementation(projects.anchor)
  implementation(projects.features.counter)
  implementation(projects.features.config)
  implementation(projects.features.main.presentation)
}
