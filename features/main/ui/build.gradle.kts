import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
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
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }
}

android {

  namespace = "dev.kioba.anchor.features.main.ui"

  compileSdk = 34

  defaultConfig {
    minSdk = 21

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  //  implementation("dev.kioba:anchor:0.0.1")
  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.material3.android)
  implementation(libs.core.ktx)
  implementation(libs.kotlin.stdlib)
  implementation(libs.ui)
  implementation(libs.ui.tooling.preview)
  implementation(projects.anchor)
  implementation(projects.features.counter)
  implementation(projects.features.main.presentation)

//  testImplementation 'junit:junit:4.13.2'
//  androidTestImplementation 'androidx.test.ext:junit:1.1.5'
//  androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
//  androidTestImplementation "androidx.compose.ui:ui-test-junit4:1.4.3"
//  debugImplementation "androidx.compose.ui:ui-tooling:1.4.3"
}
