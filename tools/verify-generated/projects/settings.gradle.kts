pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
rootProject.name = "verify"
rootDir.resolve("../build/generated").listFiles()!!.filter { it.isDirectory }.sorted().forEach {
    include(it.name); project(":${it.name}").projectDir = it
}
