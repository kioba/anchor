plugins {
  id("com.android.library")
  kotlin("android")
  id("dev.kioba.anchor")
  `maven-publish`
}

android {
  namespace = "dev.kioba.anchor"
}

publishing {
  repositories {
    maven {
      name = "Anchor"
      url = uri("https://maven.pkg.github.com/kioba/anchor")
      authentication {
        create<BasicAuthentication>("basic")
      }
      credentials {
        username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
        password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
      }
    }
  }
  val mavenArtifactId = "${project.name}-release.aar"
  val mavenArtifactPath = "$buildDir/outputs/aar/$mavenArtifactId"
  publications {
    register("gprRelease", MavenPublication::class) {
      groupId = "dev.kioba"
      artifactId = "anchor"
      version = "0.0.3"
      artifact(mavenArtifactPath)
    }
  }
}

dependencies {
  implementation("androidx.compose.ui:ui:1.4.3")
  implementation("androidx.compose.foundation:foundation:1.4.3")
  implementation("androidx.compose.runtime:runtime:1.4.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
  implementation("androidx.lifecycle:lifecycle-common:2.6.1")

  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
