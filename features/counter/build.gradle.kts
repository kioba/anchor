plugins {
  alias(libs.plugins.android.multiplatformLibrary)
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)

}

kotlin {
  explicitApi()

  android {
    namespace = "dev.kioba.anchor.features.counter"
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
      isStatic = true
      binaryOption("bundleId", "dev.kioba.anchor.features.counter")
    }
  }

  sourceSets {
    androidMain {
      dependencies {

        implementation(libs.android.core)
        implementation(libs.compose.activity)
        implementation(libs.compose.material3)
        implementation(libs.compose.ui)

        implementation(libs.kotlin.coroutinesAndroid)

        implementation(libs.kotlin.serializationCore)
        implementation(libs.kotlin.serializationJson)

        implementation(libs.compose.uiTooling)
        implementation(libs.compose.uiToolingPreview)

        implementation(projects.features.resources)
      }
    }

    commonMain {
      dependencies {
        implementation(libs.kotlin.coroutinesCore)
        implementation(projects.anchor)
        implementation(projects.anchorCompose)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(projects.anchorTest)
      }
    }
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}
