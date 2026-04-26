plugins {
  id("dev.kioba.kmp-library")
  id("dev.kioba.publish")
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kotlin.coroutinesCore)
      }
    }
  }
}
