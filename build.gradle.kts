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
}
