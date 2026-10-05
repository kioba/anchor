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

    val desktopTest by getting {
      dependencies {
        implementation(libs.compose.multiplatform.uiTest)
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlin.coroutinesTest)
        implementation(libs.kotlin.coroutinesSwing)
      }
    }
  }
}
