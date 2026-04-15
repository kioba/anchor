plugins {
  alias(libs.plugins.android.multiplatformLibrary)
  alias(libs.plugins.kotlinMultiplatform)

}

kotlin {
  explicitApi()

  android {
    namespace = "dev.kioba.anchor.umbrella"
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
    iosSimulatorArm64()
  ).forEach {
    it.binaries.framework {
      baseName = "shared"
      isStatic = true
      binaryOption("bundleId", "dev.kioba.anchor.umbrella")
      binaryOption("bundleVersion", "2")
      export(projects.anchor)
      export(projects.anchorCompose)
      export(projects.anchorTest)
      export(projects.features.main)
      export(projects.features.config)
      export(projects.features.counter)
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(projects.anchor)
      api(projects.anchorCompose)
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
