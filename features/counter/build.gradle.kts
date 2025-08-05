plugins {
  alias(libs.plugins.android.multiplatformLibrary)
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)

  id("co.touchlab.skie") version "0.10.5"
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "dev.kioba.anchor.features.counter"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    @Suppress("UnstableApiUsage")
    withHostTestBuilder {}.configure {}

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
      isStatic = true
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

    getByName("androidHostTest") {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(projects.anchorTest)
      }
    }

    commonMain {
      dependencies {
        implementation(libs.kotlin.coroutinesCore)
        implementation(projects.anchor)
      }
    }
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}
