# Changelog

## Unreleased
- Preview generated files in a selectable file tree with read-only current/generated diffs.
- Create only selected files; replace existing files only when explicitly checked.
- Include unsaved edits in previews, reject stale previews and conflicting output paths, and group applied changes into one undo action.

## 1.0.1
- Compatibility: since-build 242 with no upper limit.
- Fixes: Spring-bean MapStruct mapper, JPA service template, jakarta fallback.
- New: ORM Type selector, base path field, Liquibase JSON and SQL.

## 1.0.0
- First public release as OneClickAPI (free).
- Generate DTOs, Mapper, Repository, Service and Controller from `@Entity` or POJO classes.
- JPA/Hibernate and MyBatis support; multi-class selection.
- Liquibase changelogs (XML, YAML), image/PDF upload APIs, global exception handling.
