import java.util.Properties

plugins {
  id("com.android.library")
  kotlin("android")
  id("dev.kioba.anchor")
  `maven-publish`
}

android {
  namespace = "dev.kioba.anchor"
}

val localProperties: Properties =
  Properties()
    .apply { load(file("../local.properties").inputStream()) }

fun Properties.readGprUser(): String = get("gpr.user") as String

fun Properties.readGprKey(): String = get("gpr.key") as String

publishing {
  repositories {
    maven {
      name = "Anchor"
      url = uri("https://maven.pkg.github.com/kioba/anchor")
//      authentication {
//        create<BasicAuthentication>("basic")
//      }
      credentials {
        username = localProperties.readGprUser()
        password = localProperties.readGprKey()
      }
    }
  }
  val mavenArtifactId = "${project.name}-release.aar"
  val mavenArtifactPath = "$buildDir/outputs/aar/$mavenArtifactId"
  publications {
    register("gprRelease", MavenPublication::class) {
      groupId = "dev.kioba"
      artifactId = "anchor"
      version = "0.0.2"
      artifact(mavenArtifactPath)
    }
  }
}

dependencies {
  implementation("androidx.compose.ui:ui:1.4.3")
  implementation("androidx.compose.foundation:foundation:1.4.3")
  implementation("androidx.compose.runtime:runtime:1.4.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}