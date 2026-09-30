plugins {
  //android
  alias(libs.plugins.android.application).apply(false)
  alias(libs.plugins.android.library).apply(false)

  // android kotlin multiplatform
  alias(libs.plugins.android.multiplatformLibrary).apply(false)

  // kotlin multiplatform
  alias(libs.plugins.kotlinMultiplatform).apply(false)

  // compose
  alias(libs.plugins.compose.multiplatform).apply(false)
  alias(libs.plugins.compose.compiler).apply(false)

  // publish
  alias(libs.plugins.vaniktechMavenPublish).apply(false)

  // documentation
  alias(libs.plugins.dokka)
}

// Aggregated API reference for the published modules: `./gradlew :dokkaGenerate`
// writes HTML to build/dokka/html. Keep this list in sync with the modules that
// apply `dev.kioba.publish`.
dependencies {
  dokka(projects.anchor)
  dokka(projects.anchorCompose)
  dokka(projects.anchorTest)
}

dokka {
  moduleName.set("Anchor")
}
