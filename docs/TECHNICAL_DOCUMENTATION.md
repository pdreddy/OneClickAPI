# OneClickAPI — Technical Documentation

OneClickAPI is an IntelliJ IDEA plugin that generates a full REST API layer (DTO, Mapper, Repository, Service, Controller, Liquibase changelogs, file-upload endpoints, global exception handling) from an existing Entity or POJO class, using FreeMarker templates.

---

## 1. `GenerateApiAction`

**Type:** IntelliJ Action (`DumbAwareAction`)
**Purpose:** Entry point of the plugin — this is what runs when the user clicks "Generate API" in the IDE menu.

| Method | What it does |
|---|---|
| `actionPerformed(e)` | Detects which class(es) the user selected (single class in editor, or multiple classes in the project tree), opens `GenerateApiDialog` with them, and — if the user confirms the dialog — calls `ClassGenerator.generateApi(...)` once per selected class with the options chosen in the dialog. Also triggers independent features (Exception Handling, Image/PDF upload, Liquibase-only) that don't require CRUD to be enabled. |
| `createLiquibaseFileName(psiClass, index)` | Builds a unique changelog file name (`timestamp_classname`) when Liquibase scripts are generated for multiple classes at once. |

---

## 2. `GenerateApiDialog`

**Type:** IntelliJ modal dialog (`DialogWrapper`)
**Purpose:** The UI where the user configures what should be generated (CRUD, Lombok, Repository type, Converter type, Liquibase, Exception Handling, File Upload APIs) and which classes to include.

Key responsibilities, grouped by concern:

- **Class list management**
    - `addClassToList(psiClass)` — adds a class to the dialog's list, but only if it is an Entity or POJO (anything else is ignored).
    - `showClassChooser()` — opens IntelliJ's class picker so the user can add more classes manually.
    - Remove button — deletes the selected class(es) from the list.

- **ORM detection**
    - `updateOrmTypeLabel()` — inspects the selected classes and shows whether the project uses JPA/Hibernate, MyBatis, or a mix of both.
    - `updateRepositoryAndConverterVisibility()` — shows/hides the Repository and Converter dropdowns depending on whether an Entity class is present.

- **CRUD toggle**
    - `updateCrudApiFieldsVisibility()` — shows or hides all CRUD-related fields (Repository type, Converter type, Lombok, Add/Remove buttons) based on the "Generate CRUD API" checkbox.
    - `updateAddClassButtonVisibility()` — the "Add Class" button is only enabled when CRUD generation is on.

- **Liquibase**
    - `updateLiquibaseFileName()` — auto-fills a changelog file name when exactly one class is selected; shows a placeholder message when several classes are selected (since one file per class will be generated).
    - Package path defaults to `db/changelog/{year}/{month}`.

- **Validation feedback**
    - `updateWarningLabels()` — shows a red warning ("Entity or POJO class required") if CRUD or Liquibase is enabled but no valid class has been added yet.

- **File upload options**
    - `showBusinessLogicPopup(apiType)` — when the user enables Image or PDF upload, shows an info popup reminding them that they still need to implement the actual business logic in the generated service.

- **Getters** (`getSelectedClasses()`, `getPath()`, `useLombok()`, `useLiquibase()`, `getRepoType()`, `getConverterType()`, `generateCrudApi()`, etc.) — expose the final user choices to `GenerateApiAction` once the dialog is confirmed.

`ClassItem` is a small wrapper (`psiClass` + `displayName` + `classType`) used to render entries nicely inside the class list.

---

## 3. `ClassGenerator`

**Type:** Core generation orchestrator (`object`)
**Purpose:** Given a class and the chosen options, decides *what* to generate and *where* to write the files. All the actual template rendering is delegated to the specialized generators (`DtoGenerator`, `ControllerGenerator`, `ServiceGenerator`, etc.).

| Method | What it does |
|---|---|
| `generateApi(...)` | Main orchestration method. Runs inside a `runWriteAction` (required by IntelliJ to modify PSI/files). If `generateCrudApi` is true, creates DTO → Mapper → Repository (JPA only) → Service → Controller, in that order. Independently, also handles file-upload generation, Liquibase changelog creation, and the global exception handler — each guarded by its own flag. |
| `createDtoClasses(...)` | Creates `{Class}Request` and `{Class}Response` DTOs in `dto/{class}/request` and `dto/{class}/response` packages. |
| `createMapperClass(...)` | Creates the mapper interface. Uses MapStruct-style generation for JPA/Hibernate, or a MyBatis mapper + matching XML file for MyBatis. |
| `createRepositoryClass(...)` | Creates `{Class}Repository` — only for JPA/Hibernate entities (MyBatis doesn't need a Spring Data repository). |
| `createServiceClass(...)` | Creates `{Class}Service`, choosing between the JPA-style or MyBatis-style service template. |
| `createControllerClass(...)` | Creates `{Class}Controller` with standard REST endpoints. |
| `createLiquibaseFiles(...)` | Generates a Liquibase changelog file (XML or YAML) in the configured package path, with a version-suffixed unique file name. |
| `createGlobalExceptionHandler(...)` | Creates a single `GlobalExceptionHandler` class, independent of any specific entity. |
| `createOrFindDirectory(...)` | Utility — walks/creates a package path segment by segment. |
| `createFile(...)` / `createClass(...)` | Low-level utilities that create a PSI file and write template content into it. `createClass` skips creation if the file already exists, to avoid overwriting manual edits. |
| `getIdType(psiClass)` | Reads the entity's `id` field and returns its type (defaults to `Long` if no `id` field is found) — used to type the `@PathVariable` in controllers and services. |
| `getUniqueLiquibaseFileName(...)` | Appends an incrementing version suffix (`-01`, `-02`, …) to avoid overwriting existing changelog files. |

---

## 4. `ControllerGenerator`

**Purpose:** Builds the data model for, and renders, the REST controller class.

| Method | What it does |
|---|---|
| `generateControllerContent(psiClass, controllerPackage, useLombok)` | Resolves package names for the service and DTOs, builds a data map (class name, id type, packages, Lombok flag), and renders it through `Controller.java.ft`. |

**Template (`Controller.java.ft`) generates:**
- Standard CRUD endpoints: `POST /`, `GET /{id}`, `GET /`, `PUT /{id}`, `DELETE /{id}`
- Logging via Lombok's `@Slf4j` or a manual `Logger`, depending on `useLombok`
- Constructor injection (manual constructor if Lombok is off, `@RequiredArgsConstructor` if on)

---

## 5. `DtoGenerator`

**Purpose:** Builds the data model for, and renders, the Request/Response DTO classes.

| Method | What it does |
|---|---|
| `generateDtoContent(psiClass, packagePath, classType, useLombok)` | Iterates over all fields of the source class, copies over any `jakarta.validation` / `javax.validation` annotations (e.g. `@NotNull`, `@Size`), collects the necessary imports, and renders `Dto.java.ft` with the field list and annotations. |
| `collectTypeImports(field, imports)` | Adds an import for any non-primitive, non-`java.lang` field type, so the generated DTO compiles without missing imports. |

---

## 6. `ServiceGenerator`

**Purpose:** Builds the data model for, and renders, the service class.

| Method | What it does |
|---|---|
| `generateServiceContent(psiClass, servicePackage, useLombok, repoType)` | Resolves the repository, mapper, entity, and DTO package names, gets the id type, and renders `Service.java.ft`. |

**Template (`Service.java.ft`) generates:**
- `findAll()`, `findById(id)`, `save(request)`, `update(id, request)`, `delete(id)`
- Two code paths depending on `repoType`: calls to a Spring Data `Repository` (JPA) or directly to the `Mapper` (MyBatis style)
- `@Transactional(readOnly = true)` on the class, `@Transactional` on write operations
- Logging and constructor injection follow the same Lombok on/off pattern as the controller

---

## 7. `ExceptionHandlingGenerator`

**Purpose:** Generates the global exception handler class (plain string template, no FreeMarker file needed since the content has no branching logic).

| Method | What it does |
|---|---|
| `generateGlobalExceptionHandler(exceptionPackage)` | Resolves the target package name and returns a ready-made `GlobalExceptionHandler` class annotated with `@ControllerAdvice`. It handles two cases: any generic `Exception` (returns 500) and `MethodArgumentNotValidException` (returns 400 with a field → message map of validation errors). |

---

## 8. `LiquibaseGenerator`

**Purpose:** Builds a Liquibase changelog (table creation script) for an entity, in either YAML or XML format.

| Method | What it does |
|---|---|
| `generateLiquibaseContent(psiClass, packageName, format)` | Dispatches to the YAML or XML generator based on the `format` parameter. |
| `generateYamlContent(psiClass)` | Builds a `databaseChangeLog` YAML block with one `createTable` change, using the class name as the table name (lowercased). |
| `generateXmlContent(psiClass)` | Same idea, but as Liquibase XML (`<databaseChangeLog>` / `<changeSet>` / `<createTable>`). |
| `generateColumnsYaml` / `generateColumnsXml` | Loop over all fields of the class and render one column entry per field, properly indented. |
| `generateColumnYaml` / `generateColumnXml` | Render a single column (`name`, `type`, `nullable` constraint). |
| `indent(level)` | Small helper — returns `level × 4` spaces, used to keep the generated YAML/XML readable. |
| `mapJavaTypeToDbType(javaType)` | Maps Java types to SQL column types (`String → VARCHAR(255)`, `int → INT`, `long → BIGINT`, `boolean → BOOLEAN`, `LocalDate → DATE`, `LocalDateTime → TIMESTAMP`, fallback `VARCHAR(255)`). |
| `isNullable(field)` | A field is considered nullable unless it's a Java primitive or declared `final`. |

---

## 9. `MapperGenerator`

**Purpose:** Generates the MapStruct mapper interface used for JPA/Hibernate entities.

| Method | What it does |
|---|---|
| `generateMapperContent(psiClass, mapperPackage)` | Resolves the entity, DTO, and mapper package names, then returns a `@Mapper` interface with three methods: `toResponse(entity)`, `toEntity(dto)`, and `updateFromDto(dto, @MappingTarget entity)`. The actual field-to-field mapping code is generated by MapStruct itself at compile time — this interface only declares the contract. |

---

## 10. `RepositoryGenerator`

**Purpose:** Generates the Spring Data repository interface (JPA/Hibernate entities only).

| Method | What it does |
|---|---|
| `generateRepositoryContent(psiClass, repositoryPackage, repoType)` | Picks the base interface (`JpaRepository` or `CrudRepository`) based on the user's dropdown choice, resolves the entity's id type via `ClassGenerator.getIdType`, and returns a one-line repository interface extending it, e.g. `interface UserRepository extends JpaRepository<User, Long>`. |

---

## 11. `SwaggerGenerator`

**Purpose:** Generates a Springfox Swagger configuration class (currently not wired into the main generation flow, but available as a standalone utility).

| Method | What it does |
|---|---|
| `generateSwaggerConfigContent(packageName, configPackage)` | Returns a `@Configuration` class with a `Docket` bean that scans the `controller` sub-package and exposes Swagger 2 documentation for all its endpoints. |

---

## 12. `GeneratorUtils`

**Purpose:** Shared helpers for locating source/resource roots and converting a `PsiDirectory` into a dotted Java package name.

| Method | What it does |
|---|---|
| `getBaseDirectory(psiClass)` | Finds the module's `src/main/java` source root as a `PsiDirectory`. Returns `null` if not found. |
| `getResourcesDirectory(psiClass)` | Same idea, but for `src/main/resources` — used for Liquibase and MyBatis XML files. |
| `getPackageNameFromDirectory(directory)` | Converts a directory's file-system path into a package name by stripping the `src/main/java` prefix and replacing `/` with `.`. |

---

## 13. `PackageUtils`

**Purpose:** One utility method used everywhere a new sub-package needs to be created.

| Method | What it does |
|---|---|
| `createPackageIfNotExists(directory, packageName)` | Splits a dotted package name (e.g. `dto.user.request`) into segments and creates each missing subdirectory, returning the final (deepest) directory. |

---

## 14. `TemplateEngine`

**Purpose:** Thin wrapper around FreeMarker, used by every generator that relies on a `.ft` template file (Controller, Service, DTO).

| Member | What it does |
|---|---|
| `config` | A FreeMarker `Configuration` singleton, set up to load templates from the `/templates` resource folder, use UTF-8, and rethrow template errors instead of silently swallowing them. |
| `processTemplate(templateName, dataModel)` | Loads the named template, renders it with the given data map, and returns the resulting text as a `String`. |

---

## 15. `PsiUtils`

**Purpose:** Class-type detection — decides whether a selected class is eligible for generation (Entity or POJO), which drives most of the UI logic in `GenerateApiDialog`.

| Method | What it does |
|---|---|
| `isEntityClass(psiClass)` | Returns `true` if the class carries `@jakarta.persistence.Entity` or `@javax.persistence.Entity`. |
| `isPojoClass(psiClass)` | Returns `true` if the class is **not** an entity/interface/enum/abstract class, has at least one field, and satisfies **at least one** of: has matching getters/setters for every field, carries a Lombok data annotation (`@Data`, `@Getter`, `@Setter`, `@Value`, `@NoArgsConstructor`, `@AllArgsConstructor`), or has a no-arg constructor. |
| `capitalize()` (private extension) | Lower-cases the first letter of a field name — used when building `get`/`set` method names for the getter/setter check. |

---


## 17. `plugin.xml`

**Purpose:** The plugin descriptor — tells the IDE who the plugin is, what it does, and how it integrates into the UI.

| Section | What it defines |
|---|---|
| `<id>`, `<name>`, `<version>`, `<vendor>` | Plugin identity and version shown on the JetBrains Marketplace. |
| `<description>` | Marketing/feature description shown on the plugin's Marketplace page. |
| `<idea-version since-build / until-build>` | Compatible IntelliJ Platform version range. |
| `<depends>` | Declares dependency on the Java and Platform modules (required since the plugin works with `PsiClass`/Java PSI). |
| `<actions>` | Registers `GenerateApiAction` under both the `NewGroup` and `GenerateGroup` menus, with the label "Generate API". |

---

## 18. `MyBatisServiceGenerator`

**Purpose:** MyBatis-flavored counterpart to `ServiceGenerator` — used when the source class is a POJO rather than a JPA `@Entity`.

| Method | What it does |
|---|---|
| `generateServiceContent(psiClass, servicePackage, useLombok)` | Resolves the mapper/entity/DTO packages and id type, then builds the service class as a plain string (no `.ft` template). Calls the `{Class}Mapper` directly instead of a Spring Data repository: `findAll()`, `findById(id)`, `save(request)`, `update(id, request)`, `delete(id)`. The `toResponse`/`toEntity`/`updateEntity` mapping methods are generated as **stubs** (`// Implement mapping logic here`) since MyBatis has no MapStruct-style auto-mapping. |

---

## 19. `MyBatisMapperGenerator`

**Purpose:** Generates the MyBatis mapper interface for a POJO class, in one of two styles.

| Method | What it does |
|---|---|
| `generateMapperContent(psiClass, mapperPackage, useXmlMapper)` | Dispatches to either the XML-based or annotation-based generator depending on the `useXmlMapper` flag. |
| `generateXmlBasedMapperContent(...)` | Generates a plain `@Mapper` interface with unannotated methods (`findById`, `findAll`, `insert`, `update`, `delete`) — the actual SQL is expected to live in a matching MyBatis XML file (see `MyBatisXmlGenerator`). |
| `generateAnnotationBasedMapperContent(...)` | Generates the same methods but with the SQL inlined directly via MyBatis annotations (`@Select`, `@Insert`, `@Update`, `@Delete`), so no separate XML file is needed. |
| `getFieldsForInsert` / `getValuesForInsert` / `getFieldsForUpdate` | Build the comma-separated column list, `#{param}` placeholder list, and `column = #{param}` assignment list (excluding the `id` field) used inside the generated `INSERT`/`UPDATE` SQL. |

---

## 20. `MyBatisXmlGenerator`

**Purpose:** Generates the MyBatis XML mapper file that accompanies the XML-based mapper interface (used together with `createRepositoryClass`/`createMapperClass`'s MyBatis branch in `ClassGenerator`).

| Method | What it does |
|---|---|
| `generateMapperXmlContent(psiClass, mapperPackage)` | Builds the full `<mapper>` XML document by hand with a `StringBuilder`: a `<resultMap>` mapping every field to its column, plus `<select>` (findById, findAll), `<insert>`, `<update>`, and `<delete>` statements. |
| `generateResultMapFields(psiClass, indent)` | Renders one `<result property=".." column=".."/>` line per field. |
| `getFieldsForInsertWithoutId` / `getValuesForInsertWithoutId` / `getFieldsForUpdate` | Same purpose as in `MyBatisMapperGenerator` — build the SQL column/value/assignment lists, excluding `id`. |

> **Note:** This file appears twice in the provided sources (identical duplicate `MyBatisXmlGenerator` object) — worth de-duplicating in the actual codebase to avoid a "redeclaration" compile error.

---

## 21. `FileDtoGenerator`

**Purpose:** Creates the shared `FileDto` class used by both the Image and PDF upload APIs to represent basic file metadata.

| Method | What it does |
|---|---|
| `generateFileDto(factory, directory, packageName)` | Creates the `dto` package if needed and writes the `FileDto` class into it. |
| `generateFileDtoContent(packageName)` | Returns a simple POJO with `id`, `contentType`, and `name` fields plus a constructor and manual getters/setters (no Lombok, since this class is shared regardless of the user's Lombok choice). |

---

## 22. `FileServiceGenerator`

**Purpose:** Creates the low-level `FileService` — a generic file-storage helper shared by both the Image and PDF upload features (independent of any specific entity).

| Method | What it does |
|---|---|
| `generateFileService(psiClass, factory, directory, packageName)` | Resolves the correct `HttpServletResponse` import (`javax.*` vs `jakarta.*`, based on whether the source class uses the old or new `@Entity` annotation) and writes the `FileService` class. |
| `generateFileServiceContent(...)` | Returns a `@Service` class that stores files under the project's working directory (`System.getProperty("user.dir")/{category}/{uuid}.{ext}`), with methods to upload (from `MultipartFile` or raw `byte[]`), read a file back as a `ByteArrayOutputStream`, and a few `saveFile`/`download`/`getAllFiles` methods that are left as `UnsupportedOperationException` **placeholders** for the user to wire up to a real database. |
| `getHttpServletResponseImport(psiClass)` | Picks `jakarta.servlet.http.HttpServletResponse` if the class uses `jakarta.persistence.Entity`, otherwise defaults to the `javax.*` import. |

---

## 23. `ImageUploadApiGenerator`

**Purpose:** Generates a ready-to-use image upload/download REST endpoint, independent of the CRUD generation flow.

| Method | What it does |
|---|---|
| `generateImageUploadApi(...)` | Creates both `ImageUploadController` and `ImageService` in the `controller`/`service` packages. |
| `generateImageUploadControllerContent(...)` | Returns a controller with three endpoints: `POST /api/v1/files/image` (upload a `MultipartFile`), `POST /api/v1/files/image/base64` (upload a list of base64-encoded images), and `GET /api/v1/files/image/{id}` (stream the image back as PNG). |
| `generateImageServiceContent(...)` | Returns a service that delegates actual storage to `FileService`, generates a deterministic UUID from the stored file path (`UUID.nameUUIDFromBytes`), and includes a `downloadImage` method whose file-lookup-by-id logic is left as a **placeholder** (`getFilePathByUUID`) for the user to connect to their own storage/database. |
| `getHttpServletResponseImport(psiClass)` | Same purpose as in `FileServiceGenerator`. |

---

## 24. `PdfUploadApiGenerator`

**Purpose:** Generates a ready-to-use PDF upload/download REST endpoint, independent of the CRUD generation flow.

| Method | What it does |
|---|---|
| `generatePdfUploadApi(...)` | Creates both `PdfUploadController` and `FilePdfService` in the `controller`/`service` packages. |
| `generatePdfUploadControllerContent(...)` | Returns a controller with `GET /api/v1/files/pdf` (paginated list), `GET /api/v1/files/pdf/{id}` (download as `application/pdf`), and `POST /api/v1/files/pdf` (upload). |
| `generatePdfServiceContent(...)` | Returns a service that ensures a `src/main/resources/` storage directory exists, delegates the actual file write to `FileService`, and generates a UUID for the uploaded file. `download`, `getAllPdfs`, and `getFilePathById` are left as **placeholders** for the user's own persistence logic. |
| `getHttpServletResponseImport(psiClass)` | Same purpose as in `FileServiceGenerator` / `ImageUploadApiGenerator`. |

---

## Generation Flow Summary

```
User selects class(es) → clicks "Generate API"
        │
        ▼
GenerateApiAction.actionPerformed()
        │
        ▼
GenerateApiDialog (user configures options)
        │
        ▼
ClassGenerator.generateApi()  ── runs inside runWriteAction
        │
        ├─ DtoGenerator          → {Class}Request / {Class}Response
        ├─ MapperGenerator       → {Class}Mapper (+ XML for MyBatis)
        ├─ RepositoryGenerator   → {Class}Repository (JPA only)
        ├─ ServiceGenerator      → {Class}Service
        ├─ ControllerGenerator   → {Class}Controller
        ├─ LiquibaseGenerator    → changelog file (XML/YAML)
        ├─ ExceptionHandlingGenerator → GlobalExceptionHandler
        └─ ImageUploadApiGenerator / PdfUploadApiGenerator → upload endpoints
```

Every generator follows the same pattern: **build a data model (Map) → render a FreeMarker template (`TemplateEngine.processTemplate`) → write the resulting text into a new PSI file** via `ClassGenerator.createClass` / `createFile`. Some generators (`MapperGenerator`, `RepositoryGenerator`, `ExceptionHandlingGenerator`, `LiquibaseGenerator`, `SwaggerGenerator`, `MyBatisServiceGenerator`, `MyBatisMapperGenerator`, `MyBatisXmlGenerator`, `FileDtoGenerator`, `FileServiceGenerator`, `ImageUploadApiGenerator`, `PdfUploadApiGenerator`) build their output as a plain Kotlin string instead of a `.ft` template, since their content has little to no conditional logic.

---

## Licensing

OneClickAPI is free, open-source software under the Apache License 2.0. See `LICENSE` in the repository root. No license check or `<product-descriptor>` is used.
