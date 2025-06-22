import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.library)
  kotlin("android")
}

android {
  namespace = "dev.kioba.features.resources"

  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
  minSdk = libs.versions.android.minSdk.get().toInt()
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  kotlinOptions {
    jvmTarget = JvmTarget.JVM_11.target
  }
}

dependencies {}
