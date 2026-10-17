package az.mtech.autoapigen.core.options

enum class RepositoryType(val label: String, val simpleName: String, val fqn: String) {
    JPA_REPOSITORY("JPA Repository", "JpaRepository", "org.springframework.data.jpa.repository.JpaRepository"),
    CRUD_REPOSITORY("CRUD Repository", "CrudRepository", "org.springframework.data.repository.CrudRepository");

    companion object {
        fun fromLabel(label: String?): RepositoryType = entries.firstOrNull { it.label == label } ?: JPA_REPOSITORY
    }
}

enum class ConverterType(val label: String) {
    MAPSTRUCT("MapStruct"),
    MANUAL("Manual Conversion");

    companion object {
        fun fromLabel(label: String?): ConverterType = entries.firstOrNull { it.label == label } ?: MAPSTRUCT
    }
}

enum class LiquibaseFormat(val extension: String) { XML("xml"), YAML("yaml") }

enum class ApiVersioning(val label: String) {
    NONE("None (/api/...)"),
    PATH("URL path (/api/v1/...)"),
    HEADER("Header (X-API-Version)")
}

/** What happens when a file that the generator wants to write already exists. */
enum class ConflictPolicy(val label: String) {
    SKIP("Skip"),
    REPLACE("Replace"),
    MERGE("Merge")
}

/**
 * Sub-package of the base package for each kind of class. Presets use this to switch between a flat
 * layout and e.g. a clean-architecture layout.
 */
data class PackageLayout(
    val controller: String = "controller",
    val service: String = "service",
    val repository: String = "repository",
    /** DTO <-> entity mapper (MapStruct / manual). */
    val mapper: String = "mapper",
    /** MyBatis mapper interface. */
    val persistence: String = "mapper",
    val dto: String = "dto",
    val exception: String = "exception",
    val config: String = "config"
)

data class LiquibaseOptions(
    val packagePath: String,
    /** Base file name without version suffix and extension. Only meaningful for a single class. */
    val fileName: String,
    val format: LiquibaseFormat = LiquibaseFormat.XML
)

data class Standards(
    /** Derive Bean Validation constraints (@NotNull, @NotBlank, @Size, @Email) from the entity. */
    val validation: Boolean = false,
    /** Page / size / sort parameters, a filter object and free-text search on the list endpoint. */
    val pagination: Boolean = false,
    /** Wrap results in `ApiResult<T>` and report errors as `ApiError`; generates the exception handler. */
    val standardResponse: Boolean = false,
    /** `deleted` flag instead of physically deleting rows. */
    val softDelete: Boolean = false,
    /** `createdAt` / `updatedAt` columns. */
    val auditFields: Boolean = false,
    val versioning: ApiVersioning = ApiVersioning.PATH,
    val apiVersion: Int = 1,
    /** Expose relations as ids instead of nested entities, which avoids circular DTOs. */
    val relationDtos: Boolean = true
)

data class TestOptions(
    /** JUnit 5 + Mockito service test. */
    val serviceTests: Boolean = false,
    /** JUnit 5 + MockMvc controller test. */
    val controllerTests: Boolean = false,
    /** IntelliJ HTTP Client request file per entity. */
    val httpFile: Boolean = false
) {
    val any: Boolean get() = serviceTests || controllerTests || httpFile
}

data class OpenApiOptions(
    /** Springdoc annotations (@Tag, @Operation, @Schema). */
    val annotations: Boolean = false,
    /** OpenAPI 3 YAML file per entity under src/main/resources/openapi. */
    val spec: Boolean = false,
    /** `OpenApiConfig` bean with API title and version. */
    val config: Boolean = false
) {
    val any: Boolean get() = annotations || spec || config
}

data class GenerationOptions(
    /** Package the generated sub-packages are created in. */
    val basePackage: String,
    val layout: PackageLayout = PackageLayout(),
    val crud: Boolean = true,
    val lombok: Boolean = false,
    val repositoryType: RepositoryType = RepositoryType.JPA_REPOSITORY,
    val converter: ConverterType = ConverterType.MAPSTRUCT,
    val liquibase: LiquibaseOptions? = null,
    val exceptionHandling: Boolean = false,
    val imageUpload: Boolean = false,
    val pdfUpload: Boolean = false,
    val standards: Standards = Standards(),
    val tests: TestOptions = TestOptions(),
    val openApi: OpenApiOptions = OpenApiOptions(),
    val conflictPolicy: ConflictPolicy = ConflictPolicy.SKIP
)
