import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "damu.plugin"
version = "2.0.0"

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // The IDE ships its own Kotlin stdlib (kotlin.stdlib.default.dependency=false), and the oldest IDE we
        // support (2024.2) bundles Kotlin 2.0. Compile against that API level so we never call a stdlib
        // function that the host IDE does not have.
        languageVersion = KotlinVersion.KOTLIN_2_0
        apiVersion = KotlinVersion.KOTLIN_2_0
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 242 (2024.2) is the first IDE running on JBR 21, which this plugin's Java 21 bytecode needs.
            sinceBuild = "242"
            // No upper bound: a pinned until-build is what makes Marketplace flag the plugin as
            // incompatible with every IDE release newer than it. `verifyPlugin` below guards against breakage.
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.3.5")

        // Needed by `./gradlew verifyPlugin` to download the IDEs listed in pluginVerification below.
        pluginVerifier()

        testFramework(
            org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform
        )

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }

    // okhttp was removed: nothing used it, and it pulled an old kotlin-stdlib into the plugin zip,
    // which clashes with the stdlib bundled in the IDE.
    implementation("org.freemarker:freemarker:2.3.32")

    testImplementation("junit:junit:4.13.2")
}

tasks {
    withType<JavaCompile> {
        options.release = 21
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}