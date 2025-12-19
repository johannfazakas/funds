group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    mavenLocal()
}

plugins {
    `maven-publish`
}

tasks.register("installLocal") {
    group = "build"
    description = "Build the artifact, publish it to local maven, create docker image in local registry"

    dependsOn("build", "publishToMavenLocal")
    if (tasks.findByName("publishImageToLocalRegistry") != null) {
        dependsOn("publishImageToLocalRegistry")
    }
}
