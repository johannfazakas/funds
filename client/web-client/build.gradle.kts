tasks.register<Exec>("npmInstall") {
    group = "build"
    description = "Install npm dependencies"
    workingDir(projectDir)
    commandLine("npm", "install")
    inputs.file("package.json")
    outputs.dir("node_modules")
}

tasks.register<Exec>("jsBrowserDevelopmentRun") {
    group = "application"
    description = "Start development webpack dev server"
    dependsOn("npmInstall")
    workingDir(projectDir)
    commandLine("npm", "run", "dev")
}

tasks.register<Exec>("build") {
    group = "build"
    description = "Build webpack production bundle"
    dependsOn("npmInstall")
    workingDir(projectDir)
    commandLine("npm", "run", "build")
    inputs.dir("src")
    outputs.dir("dist")
}

tasks.register<Exec>("buildDockerImage") {
    group = "docker"
    description = "Build the Docker image for the web application"
    dependsOn("build")
    workingDir(projectDir)
    commandLine("docker", "build", "-t", "funds/web-client:latest", ".")
}

tasks.register("installLocal") {
    group = "build"
    description = "Build the web application and create a Docker image"
    dependsOn("build", "buildDockerImage")
}
