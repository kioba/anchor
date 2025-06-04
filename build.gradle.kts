plugins {
  //android
  alias(libs.plugins.androidApplication).apply(false)
  alias(libs.plugins.androidLibrary).apply(false)

  // android kotlin multiplatform
  alias(libs.plugins.androidMultiplatformLibrary).apply(false)

  // kotlin multiplatform
  alias(libs.plugins.kotlinMultiplatform).apply(false)

  // compose
  alias(libs.plugins.composeMultiplatform).apply(false)
  alias(libs.plugins.composeCompiler).apply(false)

  // publish
  alias(libs.plugins.vaniktechMavenPublish).apply(false)
}
