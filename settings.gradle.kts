import kotlin.io.path.listDirectoryEntries

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "progress4j"

for (m in rootProject.projectDir.resolve("modules").toPath().listDirectoryEntries()) {
    val name = m.fileName.toString()
    if (name[0] == '.') continue
    include(name)
    project(":$name").projectDir = file("modules/$name")
    project(":$name").name = "progress4j-$name"
}