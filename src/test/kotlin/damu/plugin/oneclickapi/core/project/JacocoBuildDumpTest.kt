package damu.plugin.oneclickapi.core.project

import org.junit.Test
import java.io.File

/**
 * Not an assertion test: when -Djacoco.dir is set it writes one tiny project per build tool whose build file is
 * exactly what [BuildFileEditor.addJacoco] produces, so `verify.sh` can run the real build and check that the
 * coverage report appears.
 */
class JacocoBuildDumpTest {

    private val gradleGroovy = """
        plugins {
            id 'java'
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
            testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.11.4'
        }

        test {
            useJUnitPlatform()
        }
    """.trimIndent() + "\n"

    private val gradleKotlin = """
        plugins {
            java
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
            testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
        }

        tasks.test {
            useJUnitPlatform()
        }
    """.trimIndent() + "\n"

    private val maven = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0">
            <modelVersion>4.0.0</modelVersion>
            <groupId>com.acme</groupId>
            <artifactId>jacoco-check</artifactId>
            <version>1.0</version>
            <properties>
                <maven.compiler.release>21</maven.compiler.release>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            </properties>
            <dependencies>
                <dependency>
                    <groupId>org.junit.jupiter</groupId>
                    <artifactId>junit-jupiter</artifactId>
                    <version>5.11.4</version>
                    <scope>test</scope>
                </dependency>
            </dependencies>
            <build>
                <plugins>
                    <plugin>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <version>3.5.2</version>
                    </plugin>
                </plugins>
            </build>
        </project>
    """.trimIndent() + "\n"

    private val main = "package com.acme;\n\npublic class Calc {\n    public int add(int a, int b) {\n        return a + b;\n    }\n}\n"
    private val test = "package com.acme;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.assertEquals;\n\n" +
        "class CalcTest {\n    @Test\n    void adds() {\n        assertEquals(3, new Calc().add(1, 2));\n    }\n}\n"

    @Test
    fun `writes one project per build tool`() {
        val dir = System.getProperty("jacoco.dir")?.let(::File) ?: return
        dir.deleteRecursively()
        for ((name, file, tool, text) in listOf(
            Build("gradle-groovy", "build.gradle", BuildTool.GRADLE_GROOVY, gradleGroovy),
            Build("gradle-kotlin", "build.gradle.kts", BuildTool.GRADLE_KOTLIN, gradleKotlin),
            Build("maven", "pom.xml", BuildTool.MAVEN, maven)
        )) {
            val root = File(dir, name)
            File(root, file).apply { parentFile.mkdirs() }.writeText(BuildFileEditor.addJacoco(tool, text))
            File(root, "settings.gradle").takeIf { tool != BuildTool.MAVEN }?.writeText("rootProject.name = 'jacoco-check'\n")
            File(root, "src/main/java/com/acme/Calc.java").apply { parentFile.mkdirs() }.writeText(main)
            File(root, "src/test/java/com/acme/CalcTest.java").apply { parentFile.mkdirs() }.writeText(test)
        }
    }

    private data class Build(val name: String, val file: String, val tool: BuildTool, val text: String)
}
