import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  androidTarget {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_11)
    }
  }
}

android {
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "dev.kioba.anchor"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
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
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
  namespace = "dev.kioba.anchor.example"
}

dependencies {

  androidTestImplementation(libs.espresso.core)
  androidTestImplementation(libs.android.junit)
  androidTestImplementation(libs.compose.junitTest)

  debugImplementation(libs.compose.uiTooling)

  implementation(libs.android.activity)
  implementation(libs.android.core)
  implementation(libs.compose.activity)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)
  implementation(libs.compose.uiToolingPreview)
  implementation(libs.kotlin.stdlib)
  implementation(projects.anchor)
  implementation(projects.features.counter)
  implementation(projects.features.main)

  testImplementation(libs.junit)
}

tasks.withType<Test> {
  useJUnitPlatform()
}
