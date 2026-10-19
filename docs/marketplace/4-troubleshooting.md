# Troubleshooting and FAQ

### I don't see "Generate API"

Right-click a `.java` class in the Project view or in the editor. It is also in the **New** and **Generate** menus (Alt+Insert / ⌘N). Check that OneClickAPI is enabled under **Settings → Plugins**.

### Imports show red after generating, but the build works

The IDE has not reloaded the build yet. OneClickAPI reloads it automatically; if the imports stay red, click the Gradle/Maven **Reload** button.

### `ConflictingBeanDefinitionException` or "cannot find symbol" after generating again

This happens when you generate the same entity again with a different package layout: the classes of the earlier run are still in their old packages. OneClickAPI reuses shared classes (`GlobalExceptionHandler`, `ApiResult`, ...) and lists the old per-entity classes and tests in the preview. Delete the files it lists, then build again.

### The full-stack API test was not generated

The preview says why. Common reasons:

- a required relation points to an entity you did not select: select both entities
- the entity id has no `@GeneratedValue`
- the class is a MyBatis POJO (full-stack tests are generated for JPA entities)

### No coverage report

Make sure **Coverage report (JaCoCo)** was ticked and that `build.gradle` now contains `id 'jacoco'` (or `pom.xml` contains `jacoco-maven-plugin`). The report is only written when the tests pass. Look in `build/reports/jacoco/test/html/index.html`, not in `build/reports/tests`, which lists test results.

### Which IDE and Spring Boot versions are supported?

IntelliJ IDEA 2024.2 and every newer release. Spring Boot 2.7, 3.x and 4.x, with Maven or Gradle (Groovy or Kotlin DSL).

### Does it change my existing code?

Only what you see in the preview. Existing files are skipped, replaced or merged as you choose, and code inside custom-code regions is always kept. Entity changes (soft delete, audit fields) are merged into your entity class.

### Where do I report a problem?

Open an issue at https://github.com/pdreddy/OneClickAPI/issues
