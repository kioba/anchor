plugins {
  alias(libs.plugins.composeMultiplatform).apply(false)
  alias(libs.plugins.composeCompiler).apply(false)
  alias(libs.plugins.androidLibrary).apply(false)
  alias(libs.plugins.kotlinMultiplatform).apply(false)
  alias(libs.plugins.kmpNativeCoroutines).apply(false)
  alias(libs.plugins.vaniktechMavenPublish).apply(false)
}
