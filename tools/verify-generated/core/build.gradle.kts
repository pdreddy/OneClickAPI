plugins {
    kotlin("jvm") version "2.3.20"
    jacoco
}
repositories { mavenCentral() }
val repo = rootDir.resolve("../../../src").canonicalPath
sourceSets {
    main { kotlin.srcDirs("$repo/main/kotlin/damu/plugin/oneclickapi/core"); resources.srcDirs("$repo/main/resources") }
    test { kotlin.srcDirs("$repo/test/kotlin"); resources.srcDirs("$repo/test/resources") }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation("org.freemarker:freemarker:2.3.32")
    testImplementation("junit:junit:4.13.2")
}
tasks.test {
    systemProperty("generated.dir", rootDir.resolve("../build/generated").canonicalPath)
    systemProperty("jacoco.dir", rootDir.resolve("../build/jacoco-builds").canonicalPath)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required = true; html.required = true; csv.required = true }
}
tasks.test { finalizedBy(tasks.jacocoTestReport) }
