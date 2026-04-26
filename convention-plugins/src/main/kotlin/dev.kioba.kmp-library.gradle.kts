import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

kotlin {
  explicitApi()

  jvm("desktop")

  android {
    namespace = "dev.kioba.${project.name.replace("-", ".")}"
    compileSdk = 36
    minSdk = 23

    compilations.configureEach {
      compileTaskProvider.configure {
        compilerOptions {
          jvmTarget.set(JvmTarget.JVM_17)
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
        baseName = project.name
        isStatic = true
        binaryOption("bundleId", "dev.kioba.${project.name}")
        binaryOption("bundleVersion", "2")
      }
    }
  }
}
