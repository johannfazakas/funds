import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("funds.kotlin-common-conventions")
    application
}

// TODO(Johann) what is this doing?
tasks.named("compileKotlin", KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xdebug")
    }
}
