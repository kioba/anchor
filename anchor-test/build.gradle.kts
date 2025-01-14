import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.kmpNativeCoroutines)
  alias(libs.plugins.vaniktechMavenPublish)
}

kotlin {
  jvmToolchain(jdkVersion = 17)
  explicitApi()

  jvm("desktop")

  androidTarget {
    publishLibraryVariants("release", "debug")
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
        baseName = "anchor-test"
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
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.kotlin.test)
        api(projects.anchor)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
    val androidMain by getting {
      dependencies {
      }
    }

    val androidUnitTest by getting {
      dependencies {
      }
    }
    val androidInstrumentedTest by getting {
      dependencies {
      }
    }
  }
}

android {
  namespace = "dev.kioba.anchor.test"

  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.android.minSdk.get().toInt()
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.15"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

mavenPublishing {
  coordinates(
    groupId = "dev.kioba",
    artifactId = "anchor-test",
    version = "0.0.7",
  )

  pom {
    name.set("Architecture based on UDF design and Functional Programming for Multiplatform applications")
    description.set("Architecture based on UDF design and Functional Programming for Multiplatform applications")
    inceptionYear.set("2023")
    url.set("https://github.com/kioba/anchor")
    licenses {
      license {
        name.set("Apache-2.0")
        url.set("https://opensource.org/licenses/Apache-2.0")
      }
    }
    developers {
      developer {
        id.set("kioba")
        name.set("Kioba Somodi")
        email.set("kioba@hey.com")
      }
    }
    scm {
      url.set("https://github.com/kioba/anchor")
    }
  }
}

publishing {
  repositories {
    maven {
      name = "githubPackages"
      url = uri("https://maven.pkg.github.com/kioba/anchor")
      credentials(PasswordCredentials::class)
    }
  }
}
