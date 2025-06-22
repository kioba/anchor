import com.android.build.api.dsl.androidLibrary

plugins {
  alias(libs.plugins.android.multiplatformLibrary)
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "dev.kioba.anchor.features.counter"
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

    getByName("androidHostTest") {
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
