rootProject.name = "platform-build"

dependencyResolutionManagement {
    versionCatalogs {
        register("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}
