group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    mavenLocal()
}

//// TODO(Johann) this is some workaround. What did it fix? Where should it be placed? Seems like it could be removed. I'm leaving it here until I test everything at runtime.
//configurations.all {
//    resolutionStrategy {
//        force("org.apache.commons:commons-compress:1.26.0")
//    }
//}

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
