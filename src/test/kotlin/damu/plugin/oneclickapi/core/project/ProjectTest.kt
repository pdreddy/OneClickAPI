package damu.plugin.oneclickapi.core.project

import damu.plugin.oneclickapi.core.SampleEntities
import damu.plugin.oneclickapi.core.options.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTest {

    private val pom = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0">
            <modelVersion>4.0.0</modelVersion>
            <parent>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-parent</artifactId>
                <version>3.2.4</version>
            </parent>
            <dependencyManagement>
                <dependencies>
                    <dependency><groupId>x</groupId><artifactId>bom</artifactId><version>1</version></dependency>
                </dependencies>
            </dependencyManagement>
            <dependencies>
                <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-web</artifactId>
                </dependency>
                <!-- </dependencies> in a comment must not confuse us -->
                <dependency>
                    <groupId>org.projectlombok</groupId>
                    <artifactId>lombok</artifactId>
                    <optional>true</optional>
                </dependency>
            </dependencies>
            <build>
                <plugins>
                    <plugin>
                        <artifactId>p</artifactId>
                        <dependencies>
                            <dependency><groupId>q</groupId><artifactId>r</artifactId></dependency>
                        </dependencies>
                    </plugin>
                </plugins>
            </build>
        </project>
    """.trimIndent()

    @Test
    fun `detects maven project`() {
        val p = ProjectDetector.detect(mapOf("pom.xml" to pom))
        assertEquals(BuildTool.MAVEN, p.buildTool)
        assertEquals("pom.xml", p.buildFile)
        assertEquals(3, p.bootVersion?.major)
        assertEquals(2, p.bootVersion?.minor)
        assertTrue(p.has(Lib.WEB))
        assertTrue(p.has(Lib.LOMBOK))
        assertFalse(p.has(Lib.JPA))
        assertTrue(p.jakarta)
    }

    @Test
    fun `detects gradle kotlin and groovy with boot 2`() {
        val kts = """
            plugins {
                id("org.springframework.boot") version "2.7.18"
                id("io.spring.dependency-management") version "1.1.4"
            }
            dependencies {
                implementation("org.springframework.boot:spring-boot-starter-data-jpa")
                implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:2.3.2")
                implementation("org.mapstruct:mapstruct:1.5.5.Final")
                implementation("org.liquibase:liquibase-core")
            }
        """.trimIndent()
        val p = ProjectDetector.detect(mapOf("build.gradle.kts" to kts))
        assertEquals(BuildTool.GRADLE_KOTLIN, p.buildTool)
        assertEquals(2, p.bootVersion?.major)
        assertFalse(p.jakarta)
        assertEquals(setOf(Lib.JPA, Lib.MYBATIS, Lib.MAPSTRUCT, Lib.LIQUIBASE), p.libs)

        val groovy = "plugins { id 'org.springframework.boot' version '3.4.1' }\ndependencies {\n    implementation 'org.flywaydb:flyway-core'\n}\n"
        val g = ProjectDetector.detect(mapOf("build.gradle" to groovy))
        assertEquals(BuildTool.GRADLE_GROOVY, g.buildTool)
        assertEquals(3, g.bootVersion?.major)
        assertTrue(g.has(Lib.FLYWAY))
    }

    @Test
    fun `resolved libraries fill the gaps left by version catalogs`() {
        val kts = "plugins { alias(libs.plugins.spring.boot) }\ndependencies { implementation(libs.bundles.web) }\n"
        val p = ProjectDetector.detect(
            mapOf("build.gradle.kts" to kts),
            listOf("org.springframework.boot:spring-boot:3.5.7", "org.mapstruct:mapstruct:1.6.3", "org.springframework.boot:spring-boot-starter-validation:3.5.7")
        )
        assertEquals(3, p.bootVersion?.major)
        assertEquals(5, p.bootVersion?.minor)
        assertTrue(p.has(Lib.MAPSTRUCT))
        assertTrue(p.has(Lib.VALIDATION))
    }

    @Test
    fun `unknown project is not an error`() {
        val p = ProjectDetector.detect(emptyMap())
        assertEquals(BuildTool.UNKNOWN, p.buildTool)
        assertNull(p.bootVersion)
        assertEquals(3, p.bootMajor)
    }

    @Test
    fun `requirements follow the options and only missing ones are offered`() {
        val o = GenerationOptions(
            "p", lombok = true, liquibase = LiquibaseOptions("db", "x"),
            tests = TestOptions(serviceTests = true), openApi = OpenApiOptions(annotations = true)
        )
        val required = DependencyAdvisor.required(listOf(SampleEntities.book), o, 3)
        assertEquals(
            setOf(Lib.WEB, Lib.VALIDATION, Lib.JPA, Lib.MAPSTRUCT, Lib.LOMBOK, Lib.LIQUIBASE, Lib.SPRINGDOC, Lib.TEST),
            required.map { it.lib }.toSet()
        )
        val missing = DependencyAdvisor.missing(required, ProjectDetector.detect(mapOf("pom.xml" to pom)))
        assertFalse(missing.any { it.lib == Lib.WEB || it.lib == Lib.LOMBOK })
        assertTrue(missing.any { it.lib == Lib.JPA })
        // lombok + mapstruct needs the binding on the processor path
        assertTrue(required.first { it.lib == Lib.MAPSTRUCT }.dependencies.any { it.artifact == "lombok-mapstruct-binding" })
    }

    @Test
    fun `mybatis entities need mybatis not jpa`() {
        val r = DependencyAdvisor.required(listOf(SampleEntities.customer), GenerationOptions("p"), 3)
        assertTrue(r.any { it.lib == Lib.MYBATIS })
        assertFalse(r.any { it.lib == Lib.JPA })
    }

    private val deps = listOf(
        Dependency("org.springframework.boot", "spring-boot-starter-validation", null, Scope.IMPLEMENTATION, "@Valid"),
        Dependency("org.mapstruct", "mapstruct", "1.6.3", Scope.IMPLEMENTATION, "MapStruct"),
        Dependency("org.mapstruct", "mapstruct-processor", "1.6.3", Scope.ANNOTATION_PROCESSOR, "MapStruct"),
        Dependency("org.springframework.boot", "spring-boot-starter-test", null, Scope.TEST, "tests")
    )

    @Test
    fun `maven edit goes into the project dependencies block only`() {
        val out = BuildFileEditor.addDependencies(BuildTool.MAVEN, pom, deps)
        val projectDeps = out.substringAfter("</dependencyManagement>").substringBefore("<build>")
        assertTrue(projectDeps.contains("spring-boot-starter-validation"))
        assertTrue(projectDeps.contains("<scope>provided</scope>"))
        assertTrue(projectDeps.contains("<scope>test</scope>"))
        assertFalse(out.substringAfter("<build>").contains("spring-boot-starter-validation"))
        assertFalse(out.substringBefore("</dependencyManagement>").contains("mapstruct"))
        // untouched elsewhere and well-formed
        assertTrue(out.contains("<!-- </dependencies> in a comment must not confuse us -->"))
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(out.byteInputStream())
        // indentation follows the existing entries
        assertTrue(out.contains("        <dependency>\n            <groupId>org.mapstruct</groupId>"))
    }

    @Test
    fun `maven without a dependencies block gets one`() {
        val bare = "<project>\n    <modelVersion>4.0.0</modelVersion>\n</project>\n"
        val out = BuildFileEditor.addDependencies(BuildTool.MAVEN, bare, deps.take(1))
        assertTrue(out, out.contains("<dependencies>"))
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(out.byteInputStream())
    }

    @Test
    fun `gradle kotlin and groovy edits use the right syntax`() {
        val kts = "plugins { java }\nbuildscript {\n    dependencies {\n        classpath(\"x:y:1\")\n    }\n}\ndependencies {\n    implementation(\"a:b\")\n}\n"
        val out = BuildFileEditor.addDependencies(BuildTool.GRADLE_KOTLIN, kts, deps)
        val tail = out.substringAfterLast("dependencies {")
        assertTrue(tail, tail.contains("implementation(\"org.springframework.boot:spring-boot-starter-validation\")"))
        assertTrue(tail, tail.contains("annotationProcessor(\"org.mapstruct:mapstruct-processor:1.6.3\")"))
        assertTrue(tail, tail.contains("testImplementation(\"org.springframework.boot:spring-boot-starter-test\")"))
        assertFalse(out.substringBefore("\ndependencies {").contains("mapstruct"))

        val groovy = "dependencies {\n    implementation 'a:b'\n}\n"
        val g = BuildFileEditor.addDependencies(BuildTool.GRADLE_GROOVY, groovy, deps)
        assertTrue(g, g.contains("implementation 'org.mapstruct:mapstruct:1.6.3'"))
        assertTrue(g, g.contains("testImplementation 'org.springframework.boot:spring-boot-starter-test'"))
    }

    @Test
    fun `lombok is added to compileOnly and annotationProcessor`() {
        val lombok = Dependency("org.projectlombok", "lombok", null, Scope.COMPILE_ONLY_PROCESSOR, "Lombok")
        val out = BuildFileEditor.addDependencies(BuildTool.GRADLE_KOTLIN, "dependencies {\n}\n", listOf(lombok))
        assertTrue(out, out.contains("compileOnly(\"org.projectlombok:lombok\")"))
        assertTrue(out, out.contains("annotationProcessor(\"org.projectlombok:lombok\")"))
    }

    @Test
    fun `gradle file without dependencies block gets one and editing is repeatable`() {
        val out = BuildFileEditor.addDependencies(BuildTool.GRADLE_KOTLIN, "plugins { java }", deps.take(1))
        assertTrue(out.contains("dependencies {"))
        assertNotNull(out)
    }

    @Test(expected = BuildFileEditor.EditException::class)
    fun `unknown build tool cannot be edited`() {
        BuildFileEditor.addDependencies(BuildTool.UNKNOWN, "", deps)
    }
}
