plugins {
  kotlin("android")
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.composeCompiler)
}

android {
  namespace = "dev.kioba.anchor.features.config"

  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.android.minSdk.get().toInt()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_17.toString()
  }
}

kotlin {
  explicitApi()
}

dependencies {

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.material3)
  implementation(libs.core.ktx)
  implementation(libs.jetbrains.kotlinx.serialization.core.jvm)
  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.ui)
  implementation(libs.ui.tooling.preview)
  implementation(projects.anchor)

  debugImplementation(libs.androidx.ui.tooling)

  testImplementation(libs.kotlin.test)
  testImplementation(projects.anchorTest)
}

tasks.withType<Test> {
  useJUnitPlatform()
}
