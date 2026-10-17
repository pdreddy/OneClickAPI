# Presets and `.oneclickapi.yml`

A preset is a named set of generator options. Put presets in a file called `.oneclickapi.yml` in the **repository root** and commit it: everybody who opens *Generate API* on that project then starts from the same settings, so the generated code looks the same no matter who generated it.

## Built-in presets

| Name | What it sets |
|---|---|
| **Standard Spring Boot CRUD** | Lombok, JPA repository, MapStruct, Bean Validation, service and controller tests. |
| **Clean Architecture** | `web.controller`, `web.dto`, `web.mapper`, `application.service`, `infrastructure.persistence` packages; standard response and error format; validation; tests. |
| **Enterprise API** | Everything: pagination and search, standard response and errors, soft delete, audit fields, validation, OpenAPI (annotations, spec, config), service, controller, mapper and full-stack API tests, JaCoCo coverage report, `.http` files, exception handler, conflict policy *Merge*. |
| **MyBatis Backend** | MyBatis-oriented defaults: pagination with `LIMIT`/`OFFSET`, standard response, validation, tests. Use it with plain POJO classes. |

Pick one in the dialog (or use *Save as Preset...* to store your current settings under a name).

## File format

```yaml
version: 1
defaultPreset: Team Standard        # applied automatically when the dialog opens

presets:
  Team Standard:
    description: Our house rules
    extends: Enterprise API         # a built-in preset or another preset in this file
    basePackage: com.acme.orders    # optional: pre-fills "Class Path"
    lombok: true
    repository: JPA Repository      # JPA Repository | CRUD Repository
    converter: MapStruct            # MapStruct | Manual Conversion
    exceptionHandling: true
    layout:                         # sub-packages below the target package
      controller: web.controller
      service: application.service
      repository: infrastructure.persistence
      mapper: web.mapper
      persistence: infrastructure.persistence   # MyBatis mapper interface
      dto: web.dto
      exception: web.exception
      config: infrastructure.config
    standards:
      validation: true
      pagination: true
      standardResponse: true
      softDelete: false
      auditFields: true
      relationDtos: true            # relations as ids, no circular DTOs
      versioning: path              # path | header | none
      apiVersion: 1
    tests:
      service: true
      controller: true
      http: true
      mapper: true                  # entity <-> DTO mapper test
      integration: true             # full-stack API test on in-memory H2 (JPA)
      coverage: true                # add JaCoCo to the build: the test task writes a coverage report
    openApi:
      annotations: true
      spec: true
      config: true
    liquibase:
      enabled: false
      path: db/changelog
      format: yaml                  # xml | yaml
    onConflict: merge               # skip | replace | merge
```

Every key is optional; a preset only changes what it mentions. Unknown keys and bad values are ignored and shown in the dialog instead of failing. The ORM (JPA or MyBatis) is not a preset setting: it follows the selected class (`@Entity` means JPA, a plain POJO means MyBatis).

## Project-specific templates

Any generated file can be changed for the whole team by copying its template from the plugin (`src/main/resources/templates/*.ft`) to `.oneclickapi/templates/` in the repository root. A template with the same name there wins over the built-in one.

## Saving from the dialog

*Save as Preset...* rewrites only that preset's block in `.oneclickapi.yml` (comments and other presets are kept) and creates the file if it does not exist.
