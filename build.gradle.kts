plugins {
  alias(libs.plugins.jetbrainsCompose).apply(false)
  alias(libs.plugins.compose.compiler).apply(false)
  alias(libs.plugins.androidLibrary).apply(false)
  alias(libs.plugins.kotlinMultiplatform).apply(false)
}

//tasks.register('clean', Delete) {
//  delete rootProject.buildDir
//}
