import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("funds.kotlin-common-conventions")
    application
}

tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        // this enables debugging suspend method variables before the last suspension point
        freeCompilerArgs.add("-Xdebug")
    }
}
