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

    tasks.named("build") {
        dependsOn(tasks.named("publishToMavenLocal"))
    }

    tasks.named("build") {
        dependsOn(tasks.named("test"))
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
