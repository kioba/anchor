plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  google()
}

dependencies {
  implementation(libs.maven.publish.plugin)
  implementation(libs.agp)
  implementation(libs.kotlin.gradle.plugin)
}
