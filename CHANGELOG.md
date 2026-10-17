# Changelog

## 2.0.0
New generator engine.
- New: preview before generate, with a diff per file.
- New: safe re-generation (Skip / Replace / Merge, custom-code regions kept) and a generation report.
- New: service, controller, mapper and full-stack API tests (H2), exception-handler tests and `.http` files.
- New: optional JaCoCo coverage report with per-class coverage after `./gradlew test`.
- New: OpenAPI annotations, spec and config; pagination, filtering, search, sorting; soft delete; audit fields; API versioning; standard response and error format.
- New: team presets in `.oneclickapi.yml`.
- New: project detection and automatic dependency additions; Spring Boot 4 support.
- New: *Generate API* in the Project view and editor right-click menus.
- Fixed: duplicate Spring beans when generating again with another package layout.
- Fixed: by-id endpoints failing when the project is not compiled with `-parameters`.
- Fixed: date filters rejected with 400 on Spring Boot 2.
- Fixed: CRUD Repository option not compiling.
- Changed: Liquibase changelogs in XML and YAML only (SQL and JSON are no longer offered).

## 1.0.1
- Compatibility: since-build 242 with no upper limit.
- Fixes: Spring-bean MapStruct mapper, JPA service template, jakarta fallback.
- New: ORM Type selector, base path field, Liquibase JSON and SQL.

## 1.0.0
- First public release as OneClickAPI (free).
- Generate DTOs, Mapper, Repository, Service and Controller from `@Entity` or POJO classes.
- JPA/Hibernate and MyBatis support; multi-class selection.
- Liquibase changelogs (XML, YAML), image/PDF upload APIs, global exception handling.
