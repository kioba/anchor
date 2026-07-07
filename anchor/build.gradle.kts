plugins {
  id("dev.kioba.kmp-library")
  id("dev.kioba.publish")
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kotlin.coroutinesCore)
        implementation(libs.lifecycle.viewmodel.core)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}
