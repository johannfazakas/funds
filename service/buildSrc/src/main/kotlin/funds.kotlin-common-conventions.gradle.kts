group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    mavenLocal()
}

configurations.all {
    resolutionStrategy {
        force("org.apache.commons:commons-compress:1.26.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

tasks.named<Test>("test") {
    // TODO(Johann) is JUnit multiplatform? should this be here?
    useJUnitPlatform()
}

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
