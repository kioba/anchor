plugins {
  id("dev.kioba.kmp-library")
  id("dev.kioba.publish")
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kotlin.coroutinesCore)
        implementation(libs.kotlin.coroutinesTest)
        implementation(libs.kotlin.test)
        api(projects.anchor)
        api(projects.anchorInternal)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}
