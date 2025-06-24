import com.android.build.api.dsl.androidLibrary

plugins {
  alias(libs.plugins.android.multiplatformLibrary)
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.vaniktechMavenPublish)
  id("co.touchlab.skie") version "0.10.2"
}

kotlin {
  explicitApi()

  jvm("desktop")

  androidLibrary {
    namespace = "dev.kioba.anchor"

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
    commonMain {
      dependencies {
        implementation(libs.kotlin.coroutinesCore)
        implementation(libs.lifecycle.viewmodel)
        implementation(libs.lifecycle.rumtime)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }


    androidMain {
      dependencies {
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material)
        implementation(compose.ui)
      }
    }

    androidUnitTest {
      dependencies {
        implementation(libs.junit)
      }
    }

    androidInstrumentedTest {
      dependencies {
        implementation(libs.android.junit)
        implementation(libs.espresso.core)
      }
    }
  }
}

mavenPublishing {
  coordinates(
    groupId = "dev.kioba",
    artifactId = "anchor",
    version = "0.0.8",
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
