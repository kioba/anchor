plugins {
  alias(libs.plugins.kotlinMultiplatform)
  id("co.touchlab.skie") version "0.10.2"
}

kotlin {
  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach {
    it.binaries.framework {
      baseName = "shared"
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(projects.anchor)
      //put your multiplatform dependencies here
    }
    commonTest.dependencies {
      implementation(projects.anchorTest)
      implementation(libs.kotlin.test)
    }
  }
}
