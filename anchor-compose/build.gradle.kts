plugins {
  alias(libs.plugins.android.multiplatformLibrary)
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)
  id("dev.kioba.publish")
}

kotlin {
  explicitApi()

  jvm("desktop")

  android {
    namespace = "dev.kioba.anchor.compose"

    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    compilations.configureEach {
      compileTaskProvider.configure {
        compilerOptions {
          jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
          )
        }
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
        baseName = "anchor-compose"
        isStatic = true
        binaryOption("bundleId", "dev.kioba.anchor.compose")
        binaryOption("bundleVersion", "2")
      }
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        api(projects.anchor)
        implementation(libs.lifecycle.viewmodel)
        implementation(libs.lifecycle.rumtime)
        implementation(compose.runtime)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}
