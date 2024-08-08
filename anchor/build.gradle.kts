import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidLibrary)
  `maven-publish`
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.kmpNativeCoroutines)

//  id("module.publication")
}

kotlin {
  jvmToolchain(jdkVersion = 17)
  explicitApi()

  jvm("desktop")

  androidTarget {
    publishLibraryVariants("release")
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
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
    val commonMain by getting {
      dependencies {
        implementation(libs.kotlinx.coroutines.core)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material)
        implementation(compose.components.resources)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
    val androidMain by getting {
      dependencies {
        implementation(libs.ui)
        implementation(libs.androidx.foundation)
        implementation(libs.androidx.runtime)
        implementation(libs.androidx.lifecycle.viewmodel.ktx)
        implementation(libs.androidx.lifecycle.runtime.ktx)
        implementation(libs.androidx.lifecycle.common)
      }
    }

    val androidUnitTest by getting {
      dependencies {
        implementation(libs.junit)
      }
    }
    val androidInstrumentedTest by getting {
      dependencies {
        implementation(libs.androidx.junit)
        implementation(libs.androidx.espresso.core)
      }
    }
  }
}

android {
  namespace = "dev.kioba.anchor"

  compileSdk = 34

  defaultConfig {
    minSdk = 21
  }
  buildFeatures {
    compose = true
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

publishing {
  repositories {
    maven {
      name = "Anchor"
      url = uri("https://maven.pkg.github.com/kioba/anchor")
      authentication {
        create<BasicAuthentication>("basic")
      }
      credentials {
        username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
        password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
      }
    }
  }
  val mavenArtifactId = "${project.name}-release.aar"
  val mavenArtifactPath = "${layout.buildDirectory}/outputs/aar/$mavenArtifactId"
  publications {
    register("gprRelease", MavenPublication::class) {
      groupId = "dev.kioba"
      artifactId = "anchor"
      version = "0.0.4"
      artifact(mavenArtifactPath)
    }
  }
}
