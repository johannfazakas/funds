plugins {
    `maven-publish`
}

group = "ro.jf.funds"
version = "1.0.0"

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }

    group = rootProject.group
    version = rootProject.version
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "kotlin")

    tasks.register("installLocal") {
        group = "build"
        description = "Build the artifact, publish it to local maven, create docker image in local registry"

        dependsOn("build", "publishToMavenLocal")
        if (tasks.findByName("publishImageToLocalRegistry") != null) {
            dependsOn("publishImageToLocalRegistry")
        }
    }

    publishing {
        publications {
            create<MavenPublication>("mavenKotlin") {
                from(components["kotlin"])

                groupId = group.toString()
                artifactId = project.name
                version = rootProject.version.toString()
            }
        }
    }
}
