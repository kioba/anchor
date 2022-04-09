@file:Suppress("UnstableApiUsage")

import java.util.Properties

pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

val localProperties: Properties =
  Properties()
    .apply { load(file("local.properties").inputStream()) }

fun Properties.readGprUser(): String = get("gpr.user") as String

fun Properties.readGprKey(): String = get("gpr.key") as String

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven {
      url = uri("https://maven.pkg.github.com/kioba/anchor")
      credentials {
        username = localProperties.readGprUser()
        password = localProperties.readGprKey()
      }
    }
  }
}

rootProject.name = "anchor"
include(":app")
include(":anchor")
