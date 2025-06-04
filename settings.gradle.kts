import java.lang.System.getProperty

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  includeBuild("convention-plugins")
  repositories {
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    mavenCentral()
    gradlePluginPortal()
  }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven {
      url = uri("https://maven.pkg.github.com/kioba/anchor")
      credentials {
        username = getProperty("gpr.user") ?: System.getenv("USERNAME")
        password = getProperty("gpr.key") ?: System.getenv("TOKEN")
      }
      authentication {
        create<BasicAuthentication>("basic")
      }
    }
  }
}

rootProject.name = "anchorS"
include(":anchor")
include(":anchor-test")
include(":androidApp")
include(":features:config")
include(":features:counter")
include(":features:main:presentation")
include(":features:main:ui")
include(":umbrella")
