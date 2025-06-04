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
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_11.toString()
  }
}

kotlin {
  explicitApi()
}

dependencies {

  implementation(libs.compose.activity)
  implementation(libs.android.activity)
  implementation(libs.compose.material3)
  implementation(libs.android.core)
  implementation(libs.kotlin.serializationCore)
  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlin.coroutinesAndroid)
  implementation(libs.kotlin.coroutinesCore)
  implementation(libs.kotlin.serializationJson)
  implementation(libs.compose.ui)
  implementation(libs.compose.uiToolingPreview)
  implementation(projects.anchor)

  debugImplementation(libs.compose.uiTooling)

  testImplementation(libs.kotlin.test)
  testImplementation(projects.anchorTest)
}

tasks.withType<Test> {
  useJUnitPlatform()
}
