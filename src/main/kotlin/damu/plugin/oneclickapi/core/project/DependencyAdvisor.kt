package damu.plugin.oneclickapi.core.project

import damu.plugin.oneclickapi.core.model.EntityModel
import damu.plugin.oneclickapi.core.model.OrmType
import damu.plugin.oneclickapi.core.options.ConverterType
import damu.plugin.oneclickapi.core.options.GenerationOptions

enum class Scope { IMPLEMENTATION, COMPILE_ONLY_PROCESSOR, ANNOTATION_PROCESSOR, TEST, RUNTIME }

data class Dependency(
    val group: String,
    val artifact: String,
    /** null = managed by the Spring Boot BOM. */
    val version: String?,
    val scope: Scope,
    val reason: String
) {
    val coordinates: String get() = "$group:$artifact" + (version?.let { ":$it" } ?: "")
}

/** One logical thing the generated code needs, and the build entries that provide it. */
data class Requirement(val lib: Lib, val reason: String, val dependencies: List<Dependency>)

object DependencyAdvisor {

    // Versions are only used for artifacts that the Spring Boot BOM does not manage. Verified to exist on Maven Central.
    private const val MAPSTRUCT = "1.6.3"
    private const val LOMBOK_MAPSTRUCT_BINDING = "0.2.0"

    private fun springdoc(boot: Int) = if (boot >= 4) Dependency("org.springdoc", "springdoc-openapi-starter-webmvc-ui", "3.0.1", Scope.IMPLEMENTATION, "OpenAPI annotations")
    else if (boot == 3) Dependency("org.springdoc", "springdoc-openapi-starter-webmvc-ui", "2.8.13", Scope.IMPLEMENTATION, "OpenAPI annotations")
    else Dependency("org.springdoc", "springdoc-openapi-ui", "1.8.0", Scope.IMPLEMENTATION, "OpenAPI annotations")

    private fun mybatis(boot: Int) = Dependency(
        "org.mybatis.spring.boot", "mybatis-spring-boot-starter",
        when { boot >= 4 -> "4.0.1"; boot == 3 -> "3.0.5"; else -> "2.3.2" }, Scope.IMPLEMENTATION, "MyBatis mappers"
    )

    /** What the selected options need on the classpath. */
    fun required(entities: List<EntityModel>, o: GenerationOptions, boot: Int): List<Requirement> {
        val out = mutableListOf<Requirement>()
        if (o.crud || o.imageUpload || o.pdfUpload) {
            val web = if (boot >= 4) "spring-boot-starter-webmvc" else "spring-boot-starter-web"
            out += Requirement(Lib.WEB, "REST controllers", listOf(Dependency("org.springframework.boot", web, null, Scope.IMPLEMENTATION, "REST controllers")))
        }
        if (o.crud) {
            out += Requirement(Lib.VALIDATION, "@Valid on request bodies", listOf(Dependency("org.springframework.boot", "spring-boot-starter-validation", null, Scope.IMPLEMENTATION, "@Valid on request bodies")))
            if (entities.any { it.orm == OrmType.JPA }) {
                out += Requirement(Lib.JPA, "JPA repositories", listOf(Dependency("org.springframework.boot", "spring-boot-starter-data-jpa", null, Scope.IMPLEMENTATION, "JPA repositories")))
            }
            if (entities.any { it.orm == OrmType.MYBATIS }) {
                out += Requirement(Lib.MYBATIS, "MyBatis mappers", listOf(mybatis(boot)))
            }
            if (o.converter == ConverterType.MAPSTRUCT) {
                out += Requirement(
                    Lib.MAPSTRUCT, "MapStruct converter",
                    buildList {
                        add(Dependency("org.mapstruct", "mapstruct", MAPSTRUCT, Scope.IMPLEMENTATION, "MapStruct converter"))
                        add(Dependency("org.mapstruct", "mapstruct-processor", MAPSTRUCT, Scope.ANNOTATION_PROCESSOR, "MapStruct converter"))
                        if (o.lombok) add(Dependency("org.projectlombok", "lombok-mapstruct-binding", LOMBOK_MAPSTRUCT_BINDING, Scope.ANNOTATION_PROCESSOR, "Lombok together with MapStruct"))
                    }
                )
            }
        }
        if (o.lombok && o.crud) {
            out += Requirement(Lib.LOMBOK, "Lombok annotations", listOf(Dependency("org.projectlombok", "lombok", null, Scope.COMPILE_ONLY_PROCESSOR, "Lombok annotations")))
        }
        if (o.liquibase != null) {
            out += Requirement(Lib.LIQUIBASE, "Liquibase changelogs", listOf(Dependency("org.liquibase", "liquibase-core", null, Scope.IMPLEMENTATION, "Liquibase changelogs")))
        }
        if (o.openApi.annotations || o.openApi.config) {
            out += Requirement(Lib.SPRINGDOC, "OpenAPI annotations", listOf(springdoc(boot)))
        }
        if (o.tests.serviceTests || o.tests.controllerTests) {
            out += Requirement(Lib.TEST, "JUnit 5, Mockito, MockMvc", listOf(Dependency("org.springframework.boot", "spring-boot-starter-test", null, Scope.TEST, "JUnit 5, Mockito, MockMvc")))
        }
        return out
    }

    /** The requirements the project does not meet yet. */
    fun missing(required: List<Requirement>, profile: ProjectProfile): List<Requirement> =
        required.filter { it.lib !in profile.libs }
}
