# Options and Presets

## Code options

| Option | What it does |
|---|---|
| **Repository** | `JpaRepository` or `CrudRepository` |
| **Converter** | MapStruct mapper or a hand-written (manual) mapper |
| **Lombok** | `@Data`, `@Builder`, `@RequiredArgsConstructor` instead of getters, setters and constructors |
| **Global exception handling** | A `@RestControllerAdvice` that turns errors into proper HTTP status codes |
| **Liquibase** | A changelog per entity, in XML or YAML |
| **Image / PDF upload API** | Upload and download endpoints with a file service |

## API standards

| Option | What it does |
|---|---|
| **Bean Validation** | `@NotNull`, `@NotBlank`, `@Size`, `@Email` derived from your entity; invalid requests get 400 |
| **Pagination, sorting, filtering, search** | `?page=0&size=20&sort=title,desc`, a filter per field, and free-text search with `?q=` |
| **Standard response and error format** | Results wrapped in `ApiResult`, errors returned as `ApiError` with status, message and field errors |
| **Soft delete** | Adds a `deleted` flag to the entity; DELETE marks the row instead of removing it |
| **Audit fields** | Adds `createdAt` / `updatedAt` filled in automatically |
| **API versioning** | `/api/v1/...` in the path, an `X-API-Version` header, or none |
| **Relations as ids** | `authorId` instead of a nested `author` object, so DTOs never become circular |

## Documentation

- **Springdoc / OpenAPI annotations** on controllers and DTOs
- **OpenAPI 3 spec file** per entity in `src/main/resources/openapi/`
- **OpenApiConfig bean** with your API title and version

## Package layout

Choose where each layer goes, for example a flat `controller / service / repository` layout or a clean-architecture `web / application / infrastructure` layout. Presets set this for you.

## Presets

| Preset | Sets |
|---|---|
| **Standard Spring Boot CRUD** | Lombok, JPA repository, MapStruct, Bean Validation, service and controller tests |
| **Clean Architecture** | `web`, `application`, `infrastructure` packages, standard response format, validation, tests |
| **Enterprise API** | Everything: pagination and search, standard responses, soft delete, audit fields, OpenAPI, all tests, coverage report and `.http` files |
| **MyBatis Backend** | MyBatis mapper and XML, pagination with LIMIT/OFFSET, standard responses, tests. Use with plain POJO classes |

### Team presets

Save your settings with **Save as Preset...** to a `.oneclickapi.yml` file in the repository root and commit it. Everyone on the team then generates code the same way.

```yaml
version: 1
defaultPreset: Team Standard
presets:
  - name: Team Standard
    extends: Clean Architecture
    values:
      standards:
        pagination: true
      tests:
        mapper: true
        integration: true
        coverage: true
```

## Re-generating safely

When a file already exists, choose per file in the preview:

- **Skip**: keep your file as it is
- **Replace**: overwrite it with the new version
- **Merge**: add what is new and keep your changes

Code between `// region custom code` and `// endregion` is always kept when you regenerate.
