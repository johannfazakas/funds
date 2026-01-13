import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("funds.common-conventions")
    id("org.jetbrains.kotlin.jvm")
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xdebug")
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["kotlin"])

            groupId = group.toString()
            artifactId = project.name
            version = rootProject.version.toString()
        }
    }
}
