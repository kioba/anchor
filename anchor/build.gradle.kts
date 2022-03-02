import org.jetbrains.kotlin.konan.properties.Properties
import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
  id("com.android.library")
  kotlin("android")
  id("dev.kioba.anchor")
  id("maven-publish")
}

val githubProperties = loadProperties("github.properties")

fun getVersionName(): String {
  return "1.0.2" // Replace with version Name
}

fun getArtificatId(): String {
  return "sampleAndroidLib" // Replace with library name ID
}

dependencies {
  implementation("androidx.compose.ui:ui:1.0.5")
  implementation("androidx.compose.foundation:foundation:1.0.5")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.3")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}
