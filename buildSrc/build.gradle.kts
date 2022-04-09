plugins {
  `kotlin-dsl`
}
repositories {
  google()
  mavenCentral()
  gradlePluginPortal()
}

object PluginsVersions {
  const val GRADLE_ANDROID = "7.1.2"
  const val KOTLIN = "1.6.10"
}

dependencies {
  implementation("com.android.tools.build:gradle:7.1.2")
  implementation("com.android.tools.build:gradle-api:7.1.2")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${PluginsVersions.KOTLIN}")
  implementation(gradleApi())
}
