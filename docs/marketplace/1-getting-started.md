# Getting Started

OneClickAPI turns a JPA `@Entity` or a plain POJO into a complete, tested Spring Boot REST API in a few clicks.

## Requirements

- IntelliJ IDEA 2024.2 or newer (Community or Ultimate)
- A Spring Boot 2, 3 or 4 project built with Maven or Gradle
- Java entity classes (`@Entity` for JPA, or plain classes for MyBatis)

## Your first API in one minute

1. **Right-click** your entity class in the Project view or in the editor. You can also select several classes at once.
2. Choose **Generate API**. It is also under **New** and **Generate** (Alt+Insert / ⌘N).
3. Pick a **preset**, for example *Standard Spring Boot CRUD*, or tick the options you want.
4. Leave **Preview** on and click **Preview...**. You see every file, its package and a diff before anything is written.
5. Click **Generate**. A report shows what was created, merged or skipped.

For an entity `Product` you get:

| Layer | Generated |
|---|---|
| DTOs | `ProductRequest`, `ProductResponse` (and `ProductFilter` with pagination) |
| Mapper | `ProductMapper` (MapStruct) or a manual mapper |
| Persistence | `ProductRepository` (JPA) or a MyBatis mapper interface and XML |
| Service | `ProductService` |
| API | `ProductController` with create, read, list, update and delete |
| Tests | Service, controller, mapper and full-stack API tests (optional) |

## Run it

```
./gradlew bootRun        # or: mvn spring-boot:run
```

Then try the endpoints with the generated `http-requests/Product.http` file, or open Swagger UI at `/swagger-ui.html` if OpenAPI is on.

## Missing dependencies

OneClickAPI detects what your project already has (Spring Web, JPA, MyBatis, MapStruct, Lombok, springdoc, H2...). Anything the selected options need is added to your `pom.xml` or `build.gradle(.kts)`, shown in the preview, and the Gradle/Maven project is reloaded for you.
