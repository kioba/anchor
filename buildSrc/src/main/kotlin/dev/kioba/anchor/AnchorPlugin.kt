package dev.kioba.anchor

import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AnchorPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.run {
      configureKotlinCompiler()
      configureAndroid()
    }
  }
}

private fun Project.configureAndroid() {
  extensions.getByType<LibraryExtension>().apply {
    compileSdk = 33

    defaultConfig {
      minSdk = 21

      testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
      release {
        isMinifyEnabled = false
      }
    }
    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
      compose = true
    }
    composeOptions {
      kotlinCompilerExtensionVersion = "1.4.7"
    }
  }
}


