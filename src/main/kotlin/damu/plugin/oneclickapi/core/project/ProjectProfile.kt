package damu.plugin.oneclickapi.core.project

enum class BuildTool { MAVEN, GRADLE_GROOVY, GRADLE_KOTLIN, UNKNOWN }

/** Libraries the generator cares about. */
enum class Lib(val display: String) {
    WEB("Spring Web"),
    JPA("Spring Data JPA"),
    MYBATIS("MyBatis"),
    MAPSTRUCT("MapStruct"),
    LOMBOK("Lombok"),
    VALIDATION("Bean Validation"),
    LIQUIBASE("Liquibase"),
    FLYWAY("Flyway"),
    SPRINGDOC("Springdoc OpenAPI"),
    TEST("Spring Boot Test (JUnit 5, Mockito, MockMvc)"),
    H2("H2 in-memory database"),
    /** Spring Boot 4 moved the MockMvc test support into its own starter. */
    WEBMVC_TEST("Spring Boot WebMvc test starter")
}

data class BootVersion(val major: Int, val minor: Int = 0, val patch: Int = 0, val raw: String = "$major.$minor.$patch") {
    companion object {
        private val PATTERN = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")

        fun parse(text: String?): BootVersion? {
            val m = PATTERN.find(text ?: return null) ?: return null
            return BootVersion(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt(),
                m.groupValues[3].ifEmpty { "0" }.toInt(),
                text.trim()
            )
        }
    }
}

/** What we learned about the module we are generating into. */
data class ProjectProfile(
    val buildTool: BuildTool = BuildTool.UNKNOWN,
    /** Build file path relative to the module root (`pom.xml`, `build.gradle.kts`, ...). */
    val buildFile: String? = null,
    val bootVersion: BootVersion? = null,
    val libs: Set<Lib> = emptySet(),
    /** Fully qualified name of the module's `@SpringBootApplication` class, if one was found. */
    val applicationClass: String? = null
) {
    /** Boot 3+ moved from javax.* to jakarta.*; unknown versions are assumed to be current. */
    val bootMajor: Int get() = bootVersion?.major ?: 3
    val jakarta: Boolean get() = bootMajor >= 3

    fun has(lib: Lib): Boolean = lib in libs
}
