plugins {
    id("com.vanniktech.maven.publish")
}

val githubPackagesUsername = System.getenv("ORG_GRADLE_PROJECT_githubPackagesUsername")
    ?: project.findProperty("githubPackagesUsername")?.toString()
    ?: ""
val githubPackagesPassword = System.getenv("ORG_GRADLE_PROJECT_githubPackagesPassword")
    ?: project.findProperty("githubPackagesPassword")?.toString()
    ?: ""

mavenPublishing {
    coordinates(
        groupId = project.property("POM_GROUP_ID").toString(),
        artifactId = project.name,
        version = project.property("POM_VERSION").toString()
    )

    publishToMavenCentral()

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
