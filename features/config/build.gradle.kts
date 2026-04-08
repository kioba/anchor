plugins {
  alias(libs.plugins.android.multiplatformLibrary)
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "dev.kioba.anchor.features.config"
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
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kotlin.coroutinesCore)
        implementation(libs.kotlin.serializationJson)
        implementation(projects.anchor)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.compose.activity)
        implementation(libs.android.activity)
        implementation(libs.compose.material3)
        implementation(libs.android.core)
        implementation(libs.kotlin.coroutinesAndroid)
        implementation(libs.compose.ui)
        implementation(libs.compose.uiToolingPreview)
        implementation(libs.compose.uiTooling)
      }
    }

  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}
