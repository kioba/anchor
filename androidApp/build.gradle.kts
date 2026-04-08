plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
  namespace = "dev.kioba.anchor.example"

  sourceSets {
    getByName("main") {
      manifest.srcFile("src/androidMain/AndroidManifest.xml")
      java.srcDirs("src/androidMain/kotlin")
      res.srcDirs("src/androidMain/res")
    }
    getByName("androidTest") {
      java.srcDirs("src/androidInstrumentedTest/kotlin")
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
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
