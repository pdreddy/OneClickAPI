import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "damu.plugin"
version = "1.0.1"

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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

        testFramework(
            org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform
        )
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }

    implementation("org.freemarker:freemarker:2.3.32")
    testImplementation("junit:junit:4.13.2")
}

tasks {
    test {
        // Keep Java plugin fixtures isolated from unrelated bundled IDE plugins (for example Vue).
        systemProperty("idea.load.plugins.id", "damu.plugin.oneclickapi,com.intellij.java,org.jetbrains.kotlin")
    }

    withType<JavaCompile> {
        options.release = 21
    }

    patchPluginXml {
        sinceBuild.set("242")
        // No upper bound: JetBrains recommends omitting until-build so the plugin stays installable on new IDEs
        untilBuild.set(provider { null })
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
