package damu.plugin.oneclickapi.core.project

/**
 * Works out what a module already has from its build file text and from the coordinates of the libraries the
 * IDE resolved (`group:artifact:version`). The IDE list is what makes this reliable: it sees transitive
 * dependencies and version-catalog aliases, which the build file text alone does not show.
 */
object ProjectDetector {

    private val BUILD_FILES = listOf(
        "pom.xml" to BuildTool.MAVEN,
        "build.gradle.kts" to BuildTool.GRADLE_KOTLIN,
        "build.gradle" to BuildTool.GRADLE_GROOVY
    )

    /** `group:artifact` -> library, matched as a prefix on the artifact so starters and variants are all caught. */
    private val ARTIFACTS: List<Triple<Lib, String, String>> = listOf(
        Triple(Lib.WEB, "org.springframework.boot", "spring-boot-starter-web"),
        Triple(Lib.WEB, "org.springframework.boot", "spring-boot-starter-webmvc"),
        Triple(Lib.WEB, "org.springframework", "spring-webmvc"),
        Triple(Lib.JPA, "org.springframework.boot", "spring-boot-starter-data-jpa"),
        Triple(Lib.JPA, "org.springframework.data", "spring-data-jpa"),
        Triple(Lib.MYBATIS, "org.mybatis.spring.boot", "mybatis-spring-boot-starter"),
        Triple(Lib.MYBATIS, "org.mybatis", "mybatis"),
        Triple(Lib.MAPSTRUCT, "org.mapstruct", "mapstruct"),
        Triple(Lib.LOMBOK, "org.projectlombok", "lombok"),
        Triple(Lib.VALIDATION, "org.springframework.boot", "spring-boot-starter-validation"),
        Triple(Lib.VALIDATION, "org.hibernate.validator", "hibernate-validator"),
        Triple(Lib.VALIDATION, "jakarta.validation", "jakarta.validation-api"),
        Triple(Lib.LIQUIBASE, "org.liquibase", "liquibase-core"),
        Triple(Lib.FLYWAY, "org.flywaydb", "flyway-core"),
        Triple(Lib.SPRINGDOC, "org.springdoc", "springdoc-openapi"),
        Triple(Lib.TEST, "org.springframework.boot", "spring-boot-starter-test"),
        Triple(Lib.TEST, "org.springframework.boot", "spring-boot-test"),
        Triple(Lib.TEST, "org.springframework", "spring-test")
    )

    /**
     * @param files module-relative build file name -> text (only those that exist)
     * @param libraryCoordinates `group:artifact[:version]` of every library on the module's classpath
     */
    fun detect(files: Map<String, String>, libraryCoordinates: Collection<String> = emptyList()): ProjectProfile {
        val (buildFile, tool) = BUILD_FILES.firstOrNull { it.first in files } ?: (null to BuildTool.UNKNOWN)
        val text = buildFile?.let(files::get).orEmpty()

        val libs = mutableSetOf<Lib>()
        // 1. build file text: group and artifact, in any of the usual notations
        for ((lib, group, artifact) in ARTIFACTS) {
            if (Regex("""(?<![\w.-])${Regex.escape(artifact)}[\w-]*""").containsMatchIn(text)) libs += lib
        }
        // 2. what the IDE resolved
        for (c in libraryCoordinates) {
            val parts = c.split(':')
            if (parts.size < 2) continue
            for ((lib, group, artifact) in ARTIFACTS) {
                if (parts[0] == group && parts[1].startsWith(artifact)) libs += lib
            }
        }
        // validation and Mockito arrive with their starters
        if (Lib.WEB in libs && Lib.JPA in libs && Lib.TEST !in libs && libraryCoordinates.any { it.contains("mockito") }) libs += Lib.TEST

        return ProjectProfile(tool, buildFile, bootVersion(text, libraryCoordinates), libs)
    }

    fun bootVersion(buildText: String, libraryCoordinates: Collection<String>): BootVersion? {
        // Maven: <parent> ... spring-boot-starter-parent ... <version>
        Regex("""<parent>[\s\S]*?spring-boot-starter-parent[\s\S]*?<version>\s*([^<\s]+)\s*</version>""").find(buildText)
            ?.let { BootVersion.parse(it.groupValues[1]) }?.let { return it }
        // Maven: spring-boot-dependencies BOM
        Regex("""spring-boot-dependencies[\s\S]*?<version>\s*([^<\s]+)\s*</version>""").find(buildText)
            ?.let { BootVersion.parse(it.groupValues[1]) }?.let { return it }
        // Gradle plugin DSL
        Regex("""org\.springframework\.boot["']\)?\s*(?:version\s*)?\(?["']([\d.]+[\w.-]*)["']""").find(buildText)
            ?.let { BootVersion.parse(it.groupValues[1]) }?.let { return it }
        Regex("""org\.springframework\.boot:spring-boot-gradle-plugin:([\d.]+[\w.-]*)""").find(buildText)
            ?.let { BootVersion.parse(it.groupValues[1]) }?.let { return it }
        // Resolved library, e.g. "Gradle: org.springframework.boot:spring-boot:3.5.7" - works with version catalogs
        libraryCoordinates.firstNotNullOfOrNull { c ->
            val p = c.split(':')
            if (p.size >= 3 && p[0] == "org.springframework.boot" && p[1] == "spring-boot") BootVersion.parse(p[2]) else null
        }?.let { return it }
        return null
    }
}
