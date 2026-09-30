import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("com.vanniktech.maven.publish")
    id("dev.kioba.dokka")
}

val githubPackagesUsername = System.getenv("ORG_GRADLE_PROJECT_githubPackagesUsername")
    ?: project.findProperty("githubPackagesUsername")?.toString()
    ?: ""
val githubPackagesPassword = System.getenv("ORG_GRADLE_PROJECT_githubPackagesPassword")
    ?: project.findProperty("githubPackagesPassword")?.toString()
    ?: ""

mavenPublishing {
    // With Dokka applied, the publish plugin would otherwise switch the -javadoc.jar
    // to Dokka output and run Dokka on every publish. Keep the published artifacts
    // unchanged (empty javadoc jar); the HTML reference ships with the docs site.
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        configure(
            KotlinMultiplatform(
                javadocJar = JavadocJar.Empty(),
                sourcesJar = SourcesJar.Sources(),
                androidVariantsToPublish = emptyList(),
            )
        )
    }

    coordinates(
        groupId = project.property("POM_GROUP_ID").toString(),
        artifactId = project.name,
        version = project.property("POM_VERSION").toString()
    )

    publishToMavenCentral(automaticRelease = true)

    if (project.findProperty("signingInMemoryKey")?.toString()?.isNotBlank() == true) {
        signAllPublications()
    }

    pom {
        name.set("Architecture based on UDF design and Functional Programming for Multiplatform applications")
        description.set("Architecture based on UDF design and Functional Programming for Multiplatform applications")
        inceptionYear.set("2023")
        url.set("https://github.com/kioba/anchor")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://opensource.org/licenses/Apache-2.0")
            }
        }
        developers {
            developer {
                id.set("kioba")
                name.set("Kioba Somodi")
                email.set("kioba@hey.com")
            }
        }
        scm {
            url.set("https://github.com/kioba/anchor")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/kioba/anchor")
            credentials {
                username = githubPackagesUsername
                password = githubPackagesPassword
            }
        }
    }
}
