group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    mavenLocal()
}

plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

tasks.named<Test>("test") {
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
