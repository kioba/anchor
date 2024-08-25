plugins {
  alias(libs.plugins.jetbrainsCompose).apply(false)
  alias(libs.plugins.compose.compiler).apply(false)
  alias(libs.plugins.androidLibrary).apply(false)
  alias(libs.plugins.kotlinMultiplatform).apply(false)
  alias(libs.plugins.kmpNativeCoroutines).apply(false)
  alias(libs.plugins.vaniktechMavenPublish).apply(false)
}
