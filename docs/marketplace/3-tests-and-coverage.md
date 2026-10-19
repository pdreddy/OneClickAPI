# Tests and Coverage

OneClickAPI generates tests together with the code, so a new API is covered from the first commit.

## Generated tests

| Test | What it checks |
|---|---|
| **Service test** (JUnit 5 + Mockito) | Find, list, create, update and delete logic, including "not found" |
| **Controller test** (MockMvc) | Every endpoint's status code and JSON, validation errors (400) and not found (404) |
| **Mapper test** | Every field survives the trip between entity and DTOs, relation ids included |
| **Full-stack API test** (JPA) | The real controller, service and repository against an in-memory H2 database: create, read, list with filters, search and sorting, update, delete. Rows that a request depends on (e.g. the `Author` of a `Book`) are created first |
| **Exception handler test** | 404, 409 conflict and 500 responses |
| **`.http` file** | Ready-to-run requests for IntelliJ's HTTP Client |

The full-stack test replaces your datasource with H2 for the test only, and every test runs in a transaction that is rolled back.

If a full-stack test cannot be built, for example because a required relation points to an entity you did not select, OneClickAPI tells you why in the preview instead of generating a test that fails.

## Coverage report

Tick **Coverage report (JaCoCo)**. OneClickAPI adds JaCoCo to your build file (you see the change in the preview). Then run your tests:

**Gradle**

```
./gradlew test
```

The coverage of every class is printed right after the tests:

```
Coverage per class                                         lines branches
com.example.shop.service.ProductService                    94.0%    72.5%
com.example.shop.controller.ProductController             100.0%      -
...
HTML report: build/reports/jacoco/test/html/index.html
```

**Maven**

```
mvn test
```

Open `target/site/jacoco/index.html`.

The HTML report lists every package, class and method, with each source line marked covered or not. The XML report (`build/reports/jacoco/test/jacocoTestReport.xml` or `target/site/jacoco/jacoco.xml`) is what SonarQube reads:

```
sonar.coverage.jacoco.xmlReportPaths=build/reports/jacoco/test/jacocoTestReport.xml
```

## How much do the generated tests cover?

With all test options on, the generated tests cover about **95% of the lines** and **70% of the branches** of the generated code. That figure is measured on every build of OneClickAPI by compiling and running generated projects against real Spring Boot 2, 3 and 4.
