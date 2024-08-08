import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("com.android.application")
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  androidTarget {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }
}

android {
  compileSdk = 34

  defaultConfig {
    applicationId = "dev.kioba.anchor"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
  namespace = "dev.kioba.anchor.example"
}

dependencies {

  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.ui.test.junit4)

  debugImplementation(libs.androidx.ui.tooling)

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.core.ktx)
  implementation(libs.kotlin.stdlib)
  implementation(libs.material)
  implementation(libs.ui)
  implementation(libs.ui.tooling.preview)
  implementation(projects.anchor)
  implementation(projects.features.counter)
  implementation(projects.features.main.ui)
  implementation(projects.features.main.presentation)

  testImplementation(libs.junit)
}
