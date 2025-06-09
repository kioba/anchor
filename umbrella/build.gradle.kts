plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)

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
      binaryOption("bundleId", "dev.kioba.anchor.umbrella")
      binaryOption("bundleVersion", "2")
      export(projects.anchor)
      export(projects.anchorTest)
      export(projects.features.main)
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(projects.anchor)
      api(projects.anchorTest)
      api(projects.features.main)
      //put your multiplatform dependencies here
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}
