/**
 * Dokka (v2 Gradle plugin) setup for a published module.
 *
 * Applied through `dev.kioba.publish`, so every published module takes part in
 * the aggregated API reference that the root project builds with
 * `./gradlew :dokkaGenerate`. The root `build.gradle.kts` lists the modules to
 * aggregate; keep that list in sync when a module gains or loses published status.
 */
plugins {
  id("org.jetbrains.dokka")
}

dokka {
  dokkaSourceSets.configureEach {
    // Public declarations in `*.internal` packages exist only so public inline
    // functions can call them. They are not part of the supported API.
    perPackageOption {
      matchingRegex.set(""".*\.internal(\..*)?""")
      suppress.set(true)
    }

    sourceLink {
      localDirectory.set(layout.projectDirectory.dir("src"))
      remoteUrl("https://github.com/kioba/anchor/tree/master/${project.name}/src")
      remoteLineSuffix.set("#L")
    }
  }
}
