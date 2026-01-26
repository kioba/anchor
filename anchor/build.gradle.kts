import com.android.build.api.dsl.androidLibrary

plugins {
  alias(libs.plugins.android.multiplatformLibrary)
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)
  id("dev.kioba.publish")
  id("co.touchlab.skie") version "0.10.6"
}

kotlin {
  explicitApi()

  jvm("desktop")

  androidLibrary {
    namespace = "dev.kioba.anchor"

    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    @Suppress("UnstableApiUsage")
    withHostTestBuilder {}.configure {}
    @Suppress("UnstableApiUsage")
    withDeviceTestBuilder {
      sourceSetTreeName = "test"
    }

    compilations.configureEach {
      compilerOptions.configure {
        jvmTarget.set(
          org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        )
      }
    }
  }


  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64(),
  ).forEach { iosTarget ->
    iosTarget.binaries {
      framework {
        baseName = "anchor"
        isStatic = true
        binaryOption("bundleId", "dev.kioba.anchor")
        binaryOption("bundleVersion", "2")
      }
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kotlin.coroutinesCore)
        implementation(libs.lifecycle.viewmodel)
        implementation(libs.lifecycle.rumtime)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }


    androidMain {
      dependencies {
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material)
        implementation(compose.ui)
      }
    }

    androidUnitTest {
      dependencies {
        implementation(libs.junit)
      }
    }

    androidInstrumentedTest {
      dependencies {
        implementation(libs.android.junit)
        implementation(libs.espresso.core)
      }
    }
  }
}

