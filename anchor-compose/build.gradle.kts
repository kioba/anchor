plugins {
  id("dev.kioba.kmp-library")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)
  id("dev.kioba.publish")
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(projects.anchor)
        implementation(libs.lifecycle.viewmodel)
        implementation(libs.lifecycle.runtime)
        api(libs.compose.multiplatform.runtime)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}
