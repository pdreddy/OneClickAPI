# OneClickAPI — Technical Documentation

OneClickAPI is an IntelliJ IDEA plugin that generates a REST API layer (DTOs, mapper, repository, service, controller, Liquibase changelogs, tests, `.http` requests, OpenAPI documentation, file-upload endpoints, global exception handling) from an existing Entity or POJO class, using FreeMarker templates.

The code is split in two halves so that the part that matters most can be tested without an IDE:

```
damu.plugin.oneclickapi
├── core/            no com.intellij imports - compiled and unit-tested on its own
│   ├── model/       EntityModel, FieldModel: the plain data the generator works on
│   ├── options/     GenerationOptions and everything the dialog can choose
│   ├── plan/        Planner + EntityContext + templates -> list of PlannedFile (nothing is written yet)
│   ├── write/       ConflictResolver, JavaMerger, CustomRegions, PlanExecutor, GenerationReport
│   ├── project/     ProjectDetector, DependencyAdvisor, BuildFileEditor
│   └── preset/      MiniYaml, PresetStore (.oneclickapi.yml), PresetCodec, built-in presets
├── ide/             the thin IntelliJ layer
│   ├── GenerateApiDialog, PreviewDialog, ReportDialog
│   ├── GenerationService   scan -> plan -> preview -> write -> report
│   ├── PsiModelReader      PsiClass -> EntityModel
│   ├── ProjectScanner      build file + resolved libraries -> ProjectProfile
│   ├── IdeProjectFiles     the VFS behind core's ProjectFiles interface
│   └── ModuleLocator
├── util/PsiUtils    isEntityClass / isPojoClass
├── licensing/       JetBrains paid-plugin licence check
└── GenerateApiAction
```

## 1. Generation flow

```
GenerateApiAction            collects the selected classes
        │
        ▼
GenerateApiDialog            options + presets (.oneclickapi.yml) + detected project info
        │  GenerationOptions
        ▼
GenerationService            per module:
  PsiModelReader             PsiClass -> EntityModel          (read action)
  ProjectScanner             -> ProjectProfile                (read action)
  Planner.plan(...)          -> Plan(files, warnings)         (pure, nothing on disk changes)
  DependencyAdvisor          -> missing libraries -> BuildFileEditor -> pom.xml / build.gradle edit as one more PlannedFile
        │
        ▼
PreviewDialog (optional)     file list + diff, per-file Skip / Replace / Merge, untick files
        │
        ▼
PlanExecutor.execute(...)    one WriteCommandAction ("Generate API"): ConflictResolver decides per file
        │
        ▼
ReportDialog                 created / replaced / merged / skipped / unchanged / failed, plus warnings
```

Because the plan is computed first and written second, the preview shows exactly what will be written, and a run is one undo step.

## 2. `core.plan`

| Class | Role |
|---|---|
| `Planner` | Turns `List<EntityModel>` + `GenerationOptions` into a `Plan`. Decides which files exist for the chosen options, renders each with FreeMarker and gives it a module-relative path (`src/main/java/...`, `src/test/java/...`, `src/main/resources/...`, `http-requests/...`). |
| `EntityContext` | Everything derived from one entity: the DTO fields (relations become ids), the filter fields, sortable columns, soft-delete / audit fields that have to be added to the entity, validation annotations, the URL. Templates contain almost no logic because of this class. |
| `TemplateEngine` | FreeMarker. Templates are in `src/main/resources/templates`. A project can override any of them by putting a file of the same name in `.oneclickapi/templates/`. |
| `Samples` | Realistic values per Java type (and constraint-aware: `@Size(min=10)`, `@Min`, `@Email`, `@Future`) used by the generated tests, `.http` files and OpenAPI examples. |
| `LiquibaseWriter` | Changelog XML/YAML from the entity model (primary key, auto-increment, relation columns, `VARCHAR(n)` from `@Column(length)`). |
| `UploadSources` | Source text of the image/PDF upload endpoints. |
| `PlannedFile` | `path`, `content`, `kind`, optional `forcedPolicy` (an entity patch can only be merged; the HTTP environment file is never overwritten). |

### Generated output by option

| Option | Effect |
|---|---|
| Bean Validation | `@NotNull` / `@NotBlank` (from `nullable=false`), `@Size(max)` (from `@Column(length)`), `@Email` (for fields named `*email*`) on the request DTO, in addition to constraints already on the entity. |
| Pagination, sorting, filtering, search | `GET /…?page=&size=&sort=field,desc&q=&<field>=` backed by a generated `XFilter`; JPA uses `JpaSpecificationExecutor`, MyBatis uses `LIMIT/OFFSET` with a whitelisted `ORDER BY`. Returns `PageResponse<T>`. |
| Standard response / error | `ApiResult<T>` envelope, `ApiError` body, `ResourceNotFoundException`, and a `GlobalExceptionHandler` that extends `ResponseEntityExceptionHandler`, so malformed JSON, wrong methods and so on keep their real status codes. |
| Soft delete | `deleted` flag (added to the entity if missing); reads filter it, delete sets it. |
| Audit fields | `createdAt` / `updatedAt` (Hibernate `@CreationTimestamp` / `@UpdateTimestamp` for JPA, set by the service for MyBatis). |
| API versioning | `/api/v1/…`, `/api/…` with an `X-API-Version` header, or unversioned. |
| Relations as ids | `ManyToOne`/`OneToOne` becomes `xId` (resolved with `EntityManager.getReference`), `OneToMany`/`ManyToMany` becomes read-only `xIds`; entities never appear inside DTOs, so there are no cycles. |
| Tests | `XServiceTest` (JUnit 5 + Mockito), `XControllerTest` (standalone MockMvc: no Spring context, works on every Boot version), `XMapperTest` (plain JUnit on the MapStruct or manual mapper), `XApiIntegrationTest` (`@SpringBootTest` + `@AutoConfigureMockMvc` on H2, rolled back per test; built by `GeneratedTests`, which creates the related rows a request needs and skips the test with a warning when it cannot, e.g. a required relation to an entity that is not generated in the same run or an id without `@GeneratedValue`; on Boot 4 it imports `@AutoConfigureMockMvc` from `spring-boot-webmvc-test`), `http-requests/X.http` (IntelliJ HTTP Client, saves the created id for the following requests). |
| OpenAPI | Springdoc annotations (`@Tag`, `@Operation`, `@ApiResponses`, `@Schema`), `src/main/resources/openapi/x.yaml`, and an `OpenApiConfig` bean. |

## 3. `core.write` — safe re-generation

`ConflictResolver.resolve(existing, plannedFile, policy)` decides, per file:

| Policy | Result |
|---|---|
| **Skip** | Existing file is kept (identical files are reported as *unchanged*). |
| **Replace** | The new content is written, but text inside `// region custom code` … `// endregion` is copied over from the old file (named regions, `// region custom code [name]`, are matched by name). |
| **Merge** | Java: `JavaMerger` adds only the members and imports that are missing, matched by field name or by method name + parameter types; edited method bodies are never touched, and a generated constructor is not added next to the user's own (the report says so). `.http`: missing requests are appended. Other file types: kept, with a note. |

Entity updates (soft-delete / audit fields) always use Merge, even if Replace was chosen. Build file edits are applied as a text edit that the preview shows as a diff.

`JavaMerger` is a small tokenizer (comments, strings and text blocks are masked, then braces are matched). It is intentionally not a Java parser.

## 4. `core.project` — detection

`ProjectDetector` reads `pom.xml` / `build.gradle(.kts)` **and** the coordinates of the libraries the IDE resolved for the module (`ModuleRootManager.orderEntries().librariesOnly()`), so version catalogs and transitive dependencies are seen. It reports the build tool, Spring Boot version, and whether Web, JPA, MyBatis, MapStruct, Lombok, Validation, Liquibase, Flyway, Springdoc and Boot Test are present. Boot < 3 selects `javax.*` instead of `jakarta.*`, `HttpStatus` instead of `HttpStatusCode`, and the Springdoc 1.x artifact.

`DependencyAdvisor` maps the chosen options to required libraries; `BuildFileEditor` inserts the missing ones into the project-level `<dependencies>` block (not `dependencyManagement` or plugin dependencies) or the top-level Gradle `dependencies {}` block. Only public IDE APIs are used; the Spring plugin's internal model is not needed.

## 5. `core.preset` — `.oneclickapi.yml`

See [PRESETS.md](./PRESETS.md). `MiniYaml` is a 100-line reader/writer for the one shape this file uses (the IDE's bundled YAML library is not a stable plugin API). `PresetStore.save` edits only the affected preset block, so comments and other presets survive.

## 6. `PsiUtils`

| Method | What it does |
|---|---|
| `isEntityClass(psiClass)` | Returns `true` if the class carries `@jakarta.persistence.Entity` or `@javax.persistence.Entity`. |
| `isPojoClass(psiClass)` | Returns `true` if the class is **not** an entity/interface/enum/abstract class, has at least one field, and satisfies **at least one** of: has matching getters/setters for every field, carries a Lombok data annotation, or has a no-arg constructor. |

## 7. Testing

`src/test/kotlin` contains plain JUnit 4 tests for everything in `core` (merge, regions, presets, YAML, detection, build-file edits, planner). `PlanDumpTest` renders eight sample configurations (JPA/MyBatis, Lombok on/off, MapStruct/manual, every standard on, clean layout) and, when `-Dgenerated.dir=<dir>` is given, writes them to disk. That output was compiled and its generated tests run against Spring Boot 3.5 (Maven), including an end-to-end run on H2 for the JPA and MyBatis variants; the sample entities are in `src/test/resources/samples`.

## 8. `CheckLicense` (licensing package)

**Purpose:** Since OneClickAPI is a **paid** plugin (see `<product-descriptor>` in `plugin.xml`), this class verifies that the current user has a valid JetBrains-issued license before the plugin's features are allowed to run.

| Constant | What it's for |
|---|---|
| `PRODUCT_CODE` | Must exactly match the code declared in `plugin.xml`'s `<product-descriptor>` tag — this is how JetBrains' licensing system identifies *this* plugin. |
| `ROOT_CERTIFICATES` | JetBrains' public root CA certificates, used to verify that a license/signature chain is genuinely JetBrains-issued (not forged). |
| `TIMESTAMP_VALIDITY_PERIOD_MS` | A floating-license server "ticket" is only accepted if it was issued within this window (1 hour) — prevents replaying an old, expired ticket. |

| Method | What it does |
|---|---|
| `isLicensed()` | Main entry point. Asks IntelliJ's `LicensingFacade` for the current "confirmation stamp" and routes it to the correct validator based on its prefix: `key:` (an activation code or JetBrains Account license), `stamp:` (a ticket from a Floating License Server), or `eval:` (a time-limited evaluation license). Returns `null` if licensing isn't initialized yet, `true`/`false` otherwise. |
| `requestLicense(message)` | Opens the JetBrains "Register/Buy" dialog on the UI thread, pre-selecting this plugin and showing an explanatory message — used when a feature is blocked due to missing license. |
| `showRegisterDialog(productCode, message)` | Looks up the `RegisterPlugins` (open-source IDEs) or `Register` (commercial IDEs) action and triggers it with the right context. |
| `asDataContext(productCode, message)` | Builds a minimal `DataContext` that feeds the product code and message into the registration dialog action. |
| `isEvaluationValid(expirationTime)` | Parses the evaluation expiration timestamp and checks it hasn't passed yet. |
| `isKeyValid(key)` | Verifies an activation key: splits it into `licenseId-data-signature-certificate`, checks the signature against the embedded certificate (which itself must chain up to a JetBrains root certificate), and confirms the `licenseId` inside the signed payload matches the one in the key. |
| `isLicenseServerStampValid(serverStamp)` | Verifies a Floating License Server ticket: checks the cryptographic signature over `timestamp:machineId`, confirms the machine ID matches, and confirms the ticket is still within the freshness window (`TIMESTAMP_VALIDITY_PERIOD_MS`). |
| `createCertificate(certBytes, intermediateCertsBytes, checkValidityAtCurrentDate)` | Builds an `X509Certificate` from raw bytes and validates its trust chain against the hard-coded JetBrains root certificates using PKIX path validation. Throws an exception if the certificate cannot be traced back to a trusted JetBrains root. |

> **Note:** This class is boilerplate provided by JetBrains for paid-plugin licensing and is not meant to be modified beyond the `PRODUCT_CODE` constant.

---

## 9. `plugin.xml`

**Purpose:** The plugin descriptor — tells the IDE who the plugin is, what it does, and how it integrates into the UI.

| Section | What it defines |
|---|---|
| `<id>`, `<name>`, `<version>`, `<vendor>` | Plugin identity and version shown on the JetBrains Marketplace. |
| `<description>` | Marketing/feature description shown on the plugin's Marketplace page. |
| `<idea-version>` | Not written by hand: `patchPluginXml` (configured in `build.gradle.kts`) writes `since-build` = 242 and **no** `until-build`. |
| `<notificationGroup>` | Balloon group used for short messages (e.g. "cannot find src/main/java"). |
| `<depends>` | Declares dependency on the Java and Platform modules (required since the plugin works with `PsiClass`/Java PSI). |
| `<actions>` | Registers `GenerateApiAction` under both the `NewGroup` and `GenerateGroup` menus, with the label "Generate API". |
| `<product-descriptor>` | Marks the plugin as **paid**, using `PRODUCT_CODE = PAUTOAPIGENERAT` (must match `CheckLicense.PRODUCT_CODE`), with release date/version. This is what makes `LicensingFacade` and `CheckLicense` relevant at all. |

---

---

## Licensing

OneClickAPI is distributed as a **paid** plugin. `plugin.xml` declares a `<product-descriptor>` with code `PAUTOAPIGENERAT`, and `CheckLicense.isLicensed()` should be called before running the generation logic to confirm the user has a valid license (activation key, floating-server ticket, or evaluation period). If the license check fails, `CheckLicense.requestLicense(message)` can be used to prompt the user to purchase/activate.