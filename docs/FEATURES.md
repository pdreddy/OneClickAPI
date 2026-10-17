#  OneClickAPI - Features

##  Core Features
- **Automated CRUD Operations**: Instantly generate complete API endpoints.
- **Full Package & Class Creation**: Generates all necessary layers including:
    - DTO (Data Transfer Objects)
    - Controller
    - Repository
    - Service
    - Mapper
    - Exception
- **DTO Auto-Generation**: Automatically creates DTOs for request and response handling.
- **Liquibase Support**: Seamlessly generates Liquibase changelogs for database migrations.
- **File Upload API Support**: Easily generate APIs for handling image and PDF uploads.

---

##  New: Control and Quality
- **Preview Before Generate**: see every file, its package and a diff before anything is written.
- **Safe re-generation**: per file *Skip / Replace / Merge*, custom-code regions that survive regeneration, and a report of what happened.
- **Tests with the backend**: JUnit 5 + Mockito service tests, MockMvc controller tests, mapper tests (every field and relation id between entity and DTOs), full-stack API tests that run the real controller, service and repository against an in-memory H2 database, and ready-to-run IntelliJ `.http` request files.
- **Coverage report**: optionally adds JaCoCo to `build.gradle`, `build.gradle.kts` or `pom.xml` (shown in the preview), so `./gradlew test` writes `build/reports/jacoco/test/html/index.html` and `mvn test` writes `target/site/jacoco/index.html`. With all test options on, the generated tests cover about 95% of the lines of the generated code.
- **OpenAPI**: Springdoc annotations, an OpenAPI 3 spec file and an `OpenApiConfig` bean.
- **API standards**: Bean Validation, pagination/sorting/filtering/search, standard response and error format, soft delete, audit fields, API versioning, and relations as ids (no circular DTOs).
- **Presets**: *Standard Spring Boot CRUD*, *Clean Architecture*, *Enterprise API*, *MyBatis Backend*, or your own in `.oneclickapi.yml` in the repository.
- **Project detection**: Maven/Gradle, Spring Boot version, JPA/MyBatis, MapStruct, Lombok, Liquibase/Flyway; missing dependencies can be added from the preview.

---

##  User Benefits
- **Bulk API Generation**: Select **hundreds of classes** at once and generate **thousands of APIs** within seconds.
- **Time & Cost Efficiency**: Drastically reduce development time from **months to minutes**.
- **Clean Code & Best Practices**: Ensures a structured, maintainable, and scalable architecture.
- **Boost Business Growth**: Accelerates startup and enterprise development processes.

---

##  Integrations
OneClickAPI seamlessly integrates with multiple frameworks and technologies:
- **Spring Boot**
- **Lombok**
- **MyBatis**
- **Liquibase**
- **Hibernate**
- **And more...**

---

##  Ease of Use
- **Intuitive UI**: User-friendly interface with configurable settings.
- **Flexible Configuration**: Customize generated APIs according to your project needs.
- **One-Click API Generation**: Generate APIs effortlessly with a single click.

---

## Performance & Optimization
- **Optimized for Large Projects**: Supports **multi-module (microservices) and monolithic architectures**.
- **High Performance**: Efficient and lightweight, ensuring smooth development for large-scale applications.
- **Scalability**: Designed to handle high-demand enterprise projects seamlessly.

---

**With OneClickAPI, speed up development, ensure best practices, and build scalable applications with ease!**

