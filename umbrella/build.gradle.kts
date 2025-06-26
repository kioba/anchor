import com.android.build.api.dsl.androidLibrary

plugins {
  alias(libs.plugins.android.multiplatformLibrary)
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)

  id("co.touchlab.skie") version "0.10.4"
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "dev.kioba.anchor.umbrella"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

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
    iosSimulatorArm64()
  ).forEach {
    it.binaries.framework {
      baseName = "shared"
      isStatic = true
      binaryOption("bundleId", "dev.kioba.anchor.umbrella")
      binaryOption("bundleVersion", "2")
      export(projects.anchor)
      export(projects.anchorTest)
      export(projects.features.main)
      export(projects.features.config)
      export(projects.features.counter)
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(projects.anchor)
      api(projects.anchorTest)
      api(projects.features.main)
      api(projects.features.config)
      api(projects.features.counter)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}
