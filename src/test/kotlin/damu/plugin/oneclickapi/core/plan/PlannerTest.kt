package damu.plugin.oneclickapi.core.plan

import damu.plugin.oneclickapi.core.SampleEntities
import damu.plugin.oneclickapi.core.model.EntityModel
import damu.plugin.oneclickapi.core.options.*
import damu.plugin.oneclickapi.core.project.BootVersion
import damu.plugin.oneclickapi.core.project.ProjectProfile
import damu.plugin.oneclickapi.core.write.PlanExecutor
import damu.plugin.oneclickapi.core.write.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PlannerTest {

    private val profile = ProjectProfile(bootVersion = BootVersion(3, 5))
    private val clock = { LocalDateTime.of(2026, 10, 17, 9, 30, 15) }

    private fun plan(entities: List<EntityModel>, o: GenerationOptions, exists: (String) -> Boolean = { false }) =
        Planner(profile, TemplateEngine(), exists, clock).plan(entities, o)

    private fun Plan.paths() = files.map { it.path }
    private fun Plan.file(suffix: String) = files.single { it.path.endsWith(suffix) }.content

    @Test
    fun `defaults produce the classic file set`() {
        val p = plan(listOf(SampleEntities.author), GenerationOptions("com.acme.shop"))
        assertEquals(
            setOf(
                "src/main/java/com/acme/shop/controller/AuthorController.java",
                "src/main/java/com/acme/shop/service/AuthorService.java",
                "src/main/java/com/acme/shop/repository/AuthorRepository.java",
                "src/main/java/com/acme/shop/mapper/AuthorMapper.java",
                "src/main/java/com/acme/shop/dto/author/request/AuthorRequest.java",
                "src/main/java/com/acme/shop/dto/author/response/AuthorResponse.java"
            ),
            p.paths().toSet()
        )
    }

    @Test
    fun `nothing optional leaks into a default run`() {
        val p = plan(listOf(SampleEntities.book), GenerationOptions("com.acme.shop"))
        val all = p.files.joinToString("\n") { it.content }
        for (marker in listOf("ApiResult", "PageResponse", "Specification", "@Operation", "@Schema", "deleted", "createdAt", "Filter")) {
            assertFalse("$marker should not appear by default", all.contains(marker))
        }
        assertTrue(p.files.none { it.kind == FileKind.ENTITY_PATCH || it.kind == FileKind.TEST || it.kind == FileKind.HTTP })
    }

    @Test
    fun `relations become ids and do not make DTOs circular`() {
        val p = plan(listOf(SampleEntities.book), GenerationOptions("com.acme.shop"))
        val resp = p.file("BookResponse.java")
        assertTrue(resp, resp.contains("private Long authorId;"))
        assertTrue(resp, resp.contains("private List<Long> reviewIds;"))
        assertFalse(resp, resp.contains("Author author"))
        val req = p.file("BookRequest.java")
        assertTrue(req, req.contains("private Long authorId;"))
        assertFalse(req, req.contains("reviewIds"))
        assertTrue(p.file("BookService.java").contains("getReference(Author.class"))
    }

    @Test
    fun `relationDtos off keeps the entity type`() {
        val p = plan(listOf(SampleEntities.book), GenerationOptions("com.acme.shop", standards = Standards(relationDtos = false)))
        assertTrue(p.file("BookResponse.java").contains("private Author author;"))
    }

    @Test
    fun `versioning strategies`() {
        fun controller(v: ApiVersioning) = plan(
            listOf(SampleEntities.author), GenerationOptions("p", standards = Standards(versioning = v, apiVersion = 2))
        ).file("AuthorController.java")
        assertTrue(controller(ApiVersioning.PATH).contains("@RequestMapping(\"/api/v2/author\")"))
        assertTrue(controller(ApiVersioning.NONE).contains("@RequestMapping(\"/api/author\")"))
        assertTrue(controller(ApiVersioning.HEADER).contains("@RequestMapping(value = \"/api/author\", headers = \"X-API-Version=2\")"))
    }

    @Test
    fun `soft delete and audit patch the entity and are forced to merge`() {
        val p = plan(listOf(SampleEntities.book), GenerationOptions("p", standards = Standards(softDelete = true, auditFields = true)))
        val patch = p.files.single { it.kind == FileKind.ENTITY_PATCH }
        assertEquals(SampleEntities.book.sourcePath, patch.path)
        assertEquals(ConflictPolicy.MERGE, patch.forcedPolicy)
        assertTrue(patch.content.contains("@CreationTimestamp"))
        assertTrue(patch.content.contains("private boolean deleted = false;"))
        assertTrue(patch.content.contains("public boolean isDeleted()")) // Book has no Lombok
        val lombokPatch = plan(listOf(SampleEntities.author), GenerationOptions("p", standards = Standards(softDelete = true)))
            .files.single { it.kind == FileKind.ENTITY_PATCH }
        assertFalse(lombokPatch.content.contains("isDeleted")) // @Data supplies accessors
    }

    @Test
    fun `patching a real entity source adds fields once`() {
        val o = GenerationOptions("com.acme.shop", standards = Standards(softDelete = true, auditFields = true), conflictPolicy = ConflictPolicy.REPLACE)
        val project = SampleEntities.project()
        val p = plan(listOf(SampleEntities.book), o)
        val report = PlanExecutor(project).execute(p.files, { o.conflictPolicy })
        val entry = report.entries.single { it.path == SampleEntities.book.sourcePath }
        assertEquals(Outcome.MERGED, entry.outcome) // Replace was requested, the patch still only merged
        val source = project.files[SampleEntities.book.sourcePath]!!
        assertTrue(source.contains("private String title;"))
        assertTrue(source.contains("@Entity"))
        assertTrue(source.contains("private boolean deleted = false;"))
        assertTrue(source.contains("import org.hibernate.annotations.CreationTimestamp;"))
        // second run: nothing left to add
        val again = PlanExecutor(project).execute(plan(listOf(SampleEntities.book), o).files, { o.conflictPolicy })
        assertEquals(Outcome.UNCHANGED, again.entries.single { it.path == SampleEntities.book.sourcePath }.outcome)
    }

    @Test
    fun `soft delete on a field of the wrong type is skipped with a warning`() {
        val odd = SampleEntities.author.copy(
            fields = SampleEntities.author.fields + damu.plugin.oneclickapi.core.model.FieldModel("deleted", "String")
        )
        val p = plan(listOf(odd), GenerationOptions("p", standards = Standards(softDelete = true)))
        assertTrue(p.warnings.toString(), p.warnings.any { it.contains("soft delete skipped") })
        assertTrue(p.files.none { it.kind == FileKind.ENTITY_PATCH })
    }

    @Test
    fun `unknown source file skips the patch with a warning`() {
        val p = plan(listOf(SampleEntities.book.copy(sourcePath = null)), GenerationOptions("p", standards = Standards(auditFields = true)))
        assertTrue(p.warnings.any { it.contains("audit field") })
    }

    @Test
    fun `standard response generates envelope error body and handler`() {
        val p = plan(listOf(SampleEntities.author), GenerationOptions("p", standards = Standards(standardResponse = true)))
        val paths = p.paths().joinToString()
        for (n in listOf("ApiResult.java", "ApiError.java", "ResourceNotFoundException.java", "GlobalExceptionHandler.java")) {
            assertTrue("$n in $paths", paths.contains(n))
        }
        assertTrue(p.file("GlobalExceptionHandler.java").contains("extends ResponseEntityExceptionHandler"))
    }

    @Test
    fun `boot 2 handler uses HttpStatus`() {
        val boot2 = Planner(ProjectProfile(bootVersion = BootVersion(2, 7)), TemplateEngine(), { false }, clock)
            .plan(listOf(SampleEntities.author), GenerationOptions("p", standards = Standards(standardResponse = true)))
        val h = boot2.files.single { it.path.endsWith("GlobalExceptionHandler.java") }.content
        assertFalse(h.contains("HttpStatusCode"))
    }

    @Test
    fun `plain exception handling keeps the old handler`() {
        val p = plan(listOf(SampleEntities.author), GenerationOptions("p", exceptionHandling = true))
        assertTrue(p.file("GlobalExceptionHandler.java").contains("@ControllerAdvice"))
        assertTrue(p.paths().none { it.endsWith("ApiError.java") })
    }

    @Test
    fun `tests http and openapi files`() {
        val o = GenerationOptions(
            "com.acme.shop", tests = TestOptions(true, true, true), openApi = OpenApiOptions(true, true, true)
        )
        val p = plan(listOf(SampleEntities.author), o)
        val paths = p.paths()
        assertTrue(paths.contains("src/test/java/com/acme/shop/service/AuthorServiceTest.java"))
        assertTrue(paths.contains("src/test/java/com/acme/shop/controller/AuthorControllerTest.java"))
        assertTrue(paths.contains("http-requests/Author.http"))
        assertTrue(paths.contains("src/main/resources/openapi/author.yaml"))
        assertTrue(paths.contains("src/main/java/com/acme/shop/config/OpenApiConfig.java"))
        val env = p.files.single { it.path == "http-requests/http-client.env.json" }
        assertEquals(ConflictPolicy.SKIP, env.forcedPolicy)
        assertTrue(p.file("AuthorController.java").contains("@Tag(name = \"Author\""))
    }

    @Test
    fun `samples respect existing validation`() {
        val p = plan(listOf(SampleEntities.book), GenerationOptions("p", tests = TestOptions(controllerTests = true)))
        // @Size(min = 10, max = 13) on isbn
        assertTrue(p.file("BookControllerTest.java").contains("\\\"isbn\\\":\\\"samplexxxx\\\""))
    }

    @Test
    fun `mybatis files and warning`() {
        val p = plan(listOf(SampleEntities.customer), GenerationOptions("com.acme.shop", standards = Standards(pagination = true)))
        assertTrue(p.paths().contains("src/main/resources/mybatis/mappers/CustomerMapper.xml"))
        assertTrue(p.paths().none { it.endsWith("CustomerRepository.java") })
        assertTrue(p.paths().contains("src/main/java/com/acme/shop/mapper/CustomerDtoMapper.java"))
        assertTrue(p.warnings.any { it.contains("mybatis.mapper-locations") })
        assertTrue(p.file("CustomerMapper.xml").contains("#{name}"))
    }

    @Test
    fun `liquibase picks a free file name`() {
        val o = GenerationOptions("p", liquibase = LiquibaseOptions("db/changelog/2026/10", "init", LiquibaseFormat.YAML))
        val taken = setOf("src/main/resources/db/changelog/2026/10/init-01.yaml")
        val p = plan(listOf(SampleEntities.author), o) { it in taken }
        assertTrue(p.paths().contains("src/main/resources/db/changelog/2026/10/init-02.yaml"))
        val multi = plan(listOf(SampleEntities.author, SampleEntities.book), o)
        assertTrue(multi.paths().contains("src/main/resources/db/changelog/2026/10/20261017093015_author-01.yaml"))
        assertTrue(multi.paths().contains("src/main/resources/db/changelog/2026/10/20261017093015_book-01.yaml"))
    }

    @Test
    fun `liquibase works without crud`() {
        val o = GenerationOptions("p", crud = false, liquibase = LiquibaseOptions("db", "x", LiquibaseFormat.XML))
        val p = plan(listOf(SampleEntities.book), o)
        assertEquals(1, p.files.size)
        assertTrue(p.files[0].content.contains("<createTable tableName=\"book\">"))
    }

    @Test
    fun `upload endpoints need no class details beyond the package`() {
        val p = plan(listOf(SampleEntities.author), GenerationOptions("com.acme.shop", crud = false, imageUpload = true, pdfUpload = true))
        val names = p.files.map { it.fileName }.toSet()
        assertEquals(setOf("FileDto.java", "FileService.java", "ImageUploadController.java", "ImageService.java", "PdfUploadController.java", "FilePdfService.java"), names)
        assertTrue(p.file("ImageUploadController.java").contains("import jakarta.servlet.http.HttpServletResponse;"))
    }

    @Test
    fun `generated classes carry a custom code region`() {
        val p = plan(listOf(SampleEntities.author), GenerationOptions("p"))
        p.files.filter { it.isJava }.forEach { assertTrue(it.path, it.content.contains("// region custom code")) }
    }

    @Test
    fun `project template overrides win`() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        try {
            java.io.File(dir, "Repository.java.ft").writeText("package \${classPackageName};\n// custom template for \${className}\n")
            val p = Planner(profile, TemplateEngine(dir), { false }, clock).plan(listOf(SampleEntities.author), GenerationOptions("p"))
            assertTrue(p.file("AuthorRepository.java").contains("// custom template for Author"))
            assertNull(p.files.firstOrNull { it.path.endsWith("AuthorService.java") && it.content.contains("custom template") })
        } finally {
            dir.deleteRecursively()
        }
    }
}
