# Usage Guide for OneClickAPI

This guide will walk you through how to use **OneClickAPI** in **IntelliJ IDEA** to quickly generate APIs for your **Spring Boot** projects.

---

## Getting Started

After installing the **OneClickAPI** plugin, follow these steps to generate APIs in your project.

### ** Generating API for a Single Class**
You can generate APIs for a single `@Entity` or **POJO** class by following these steps:

1. Open your **Spring Boot** project in **IntelliJ IDEA**.
2. Navigate to the class file you want to generate an API for.
3. **Right-click** on the class in the **Edit view**.
4. Select **Generate > Generate API** from the context menu.
5. A configuration window will appear where you can customize the API generation settings.
6. Click **Generate**, and the necessary files (Controller, Service, Repository, DTOs, etc.) will be created automatically.

---

### ** Generating API for Multiple Classes**
If you want to generate APIs for multiple classes at once:

1. In the **Project Explorer**, select multiple `@Entity` or **POJO** classes by holding `Ctrl` (Windows/Linux) or `Cmd` (macOS).
2. **Right-click** on the selected classes.
3. Choose **New > Generate API** from the context menu.
4. Configure the API settings in the popup window.
5. Click **Generate** to create APIs for all selected classes simultaneously.

---

## Customization Options
When generating APIs, you will be able to configure several options, including:

- **Repository Type:** Choose between JPA, MyBatis, or a custom repository.
- **DTO Generation:** Enable or disable automatic DTO creation.
- **Lombok Support:** Use **@Data**, **@Getter**, or **@Setter** to simplify model classes.
- **Liquibase Integration:** Generate Liquibase changelogs for database migrations.
- **File Upload API:** Automatically generate API endpoints for image or PDF file uploads.
- **Exception Handling:** Add global exception handling for better error management.

---

## Preview Before Generate

With *Preview files and changes before generating* ticked (the default) the **Generate** button first opens a preview:

- every file that would be created, with its package or folder;
- for files that already exist, what *Skip*, *Replace* and *Merge* would do, shown as a diff against the file on disk;
- untick a file to leave it out, or change the action of a single file;
- entity updates (soft delete / audit fields) and build-file changes (missing dependencies) appear in the same list.

Nothing is written until you press **Generate**. The whole run is one undo step, and afterwards a report lists what was created, replaced, merged, skipped and left unchanged.

## When files already exist

| Choice | Behaviour |
|---|---|
| **Skip** | Keep your file. |
| **Replace** | Overwrite it, but keep everything you wrote between `// region custom code` and `// endregion` (every generated class ends with that empty region). |
| **Merge** | Keep your file and add only the methods, fields and imports that it does not have yet. Methods you edited are never touched. |

## API standards

Under *API standards* you can switch on: Bean Validation annotations; pagination, sorting, filtering and search; a standard success envelope and error body; soft delete; audit fields; API versioning (path or header); and relations exposed as ids so DTOs never become circular. See [TECHNICAL_DOCUMENTATION.md](./TECHNICAL_DOCUMENTATION.md#generated-output-by-option) for exactly what each one generates.

## Tests and documentation

Under *Tests and documentation*: JUnit 5 + Mockito service tests, JUnit 5 + MockMvc controller tests, mapper tests, full-stack API tests (JPA only: `@SpringBootTest` with MockMvc on an in-memory H2 database that replaces your datasource for the test; related rows a request needs are created first, and the test names your `@SpringBootApplication` class), an IntelliJ HTTP Client `.http` file per entity (run it straight from the editor; the first request stores the created id for the others), Springdoc/OpenAPI annotations, an OpenAPI 3 YAML file, and an `OpenApiConfig` bean.

The generated tests need `spring-boot-starter-test`; the OpenAPI options need Springdoc. The dialog detects what your project already has (Maven or Gradle, Spring Boot version, JPA, MyBatis, MapStruct, Lombok, Liquibase, Flyway, ...) and, in the preview, offers to add what is missing to your `pom.xml` / `build.gradle(.kts)` as one more diff you can accept or untick.

## Presets

Settings can be saved as a named preset and shared through `.oneclickapi.yml`; see [PRESETS.md](./PRESETS.md).
