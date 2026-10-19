package damu.plugin.oneclickapi.core

import damu.plugin.oneclickapi.core.options.*
import damu.plugin.oneclickapi.core.plan.DuplicateClasses
import damu.plugin.oneclickapi.core.plan.FileKind
import damu.plugin.oneclickapi.core.plan.Planner
import damu.plugin.oneclickapi.core.preset.BuiltInPresets
import damu.plugin.oneclickapi.core.preset.PresetCodec
import damu.plugin.oneclickapi.core.project.BootVersion
import damu.plugin.oneclickapi.core.project.ProjectProfile
import damu.plugin.oneclickapi.core.write.PlanExecutor
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File

/**
 * Not an assertion test: when -Dgenerated.dir is set it writes every sample configuration to disk so that the
 * output can be compiled and its tests run against real Spring jars (see the `verify-generated` script in the PR
 * notes). Without the property it only checks that rendering does not throw.
 */
class PlanDumpTest {

    private val everything = Standards(
        validation = true, pagination = true, standardResponse = true, softDelete = true, auditFields = true,
        versioning = ApiVersioning.PATH
    )
    private val allTests = TestOptions(
        serviceTests = true, controllerTests = true, httpFile = true, mapperTests = true, integrationTests = true
    )

    /** The harness adds `com.acme.shop.ShopApplication` to every scenario; the API tests name it. */
    private fun profile(major: Int, minor: Int = 0, patch: Int = 0) =
        ProjectProfile(bootVersion = BootVersion(major, minor, patch), applicationClass = "com.acme.shop.ShopApplication")
    private val allDocs = OpenApiOptions(true, true, true)

    private fun opts(
        pkg: String = "com.acme.shop",
        layout: PackageLayout = PackageLayout(),
        lombok: Boolean = false,
        converter: ConverterType = ConverterType.MAPSTRUCT,
        standards: Standards = Standards(),
        tests: TestOptions = TestOptions(),
        docs: OpenApiOptions = OpenApiOptions(),
        liquibase: LiquibaseOptions? = null,
        exceptionHandling: Boolean = false
    ) = GenerationOptions(
        basePackage = pkg, layout = layout, lombok = lombok, converter = converter, standards = standards,
        tests = tests, openApi = docs, liquibase = liquibase, exceptionHandling = exceptionHandling,
        conflictPolicy = ConflictPolicy.MERGE
    )

    val variants: Map<String, Pair<List<damu.plugin.oneclickapi.core.model.EntityModel>, GenerationOptions>> = mapOf(
        "jpa-basic" to (listOf(SampleEntities.book, SampleEntities.author) to opts()),
        "jpa-basic-handler" to (listOf(SampleEntities.book, SampleEntities.author) to opts(exceptionHandling = true, lombok = true)),
        "jpa-everything-lombok" to (listOf(SampleEntities.book, SampleEntities.author) to opts(
            lombok = true, standards = everything, tests = allTests, docs = allDocs,
            liquibase = LiquibaseOptions("db/changelog", "init", LiquibaseFormat.YAML)
        )),
        "jpa-everything-manual-header" to (listOf(SampleEntities.book, SampleEntities.author) to opts(
            converter = ConverterType.MANUAL, standards = everything.copy(versioning = ApiVersioning.HEADER),
            tests = allTests, docs = allDocs,
            liquibase = LiquibaseOptions("db/changelog", "init", LiquibaseFormat.XML)
        )),
        "jpa-tests-only" to (listOf(SampleEntities.book) to opts(tests = allTests, docs = OpenApiOptions(annotations = true))),
        "jpa-clean-layout" to (listOf(SampleEntities.book, SampleEntities.author) to opts(
            layout = PackageLayout(
                controller = "web.controller", service = "application.service", repository = "infrastructure.persistence",
                mapper = "web.mapper", persistence = "infrastructure.persistence", dto = "web.dto"
            ),
            standards = everything.copy(versioning = ApiVersioning.NONE), tests = allTests, lombok = true
        )),
        "mybatis-basic" to (listOf(SampleEntities.customer) to opts(tests = allTests)),
        "mybatis-everything" to (listOf(SampleEntities.customer) to opts(
            lombok = true, converter = ConverterType.MANUAL, standards = everything, tests = allTests, docs = allDocs
        )),
        "jpa-uploads-crudrepo" to (listOf(SampleEntities.book, SampleEntities.author) to opts(exceptionHandling = true, tests = allTests).copy(
            imageUpload = true, pdfUpload = true, repositoryType = RepositoryType.CRUD_REPOSITORY,
            liquibase = LiquibaseOptions("db/changelog", "init", LiquibaseFormat.XML)
        )),
        "jpa-crudrepo-paged-soft" to (listOf(SampleEntities.book, SampleEntities.author) to opts(
            standards = everything, tests = allTests, exceptionHandling = true
        ).copy(repositoryType = RepositoryType.CRUD_REPOSITORY)),
        "jpa-crudrepo-soft-unpaged" to (listOf(SampleEntities.book, SampleEntities.author) to opts(
            standards = everything.copy(pagination = false), tests = allTests, exceptionHandling = true
        ).copy(repositoryType = RepositoryType.CRUD_REPOSITORY)),
        "mybatis-uploads" to (listOf(SampleEntities.customer) to opts(exceptionHandling = true, tests = allTests).copy(
            imageUpload = true, pdfUpload = true
        ))
    ) + BuiltInPresets.all.flatMap { preset ->
        val key = "preset-" + preset.name.lowercase().replace(Regex("[^a-z]+"), "-").trim('-')
        val options = PresetCodec.apply(opts(), preset.values).copy(conflictPolicy = ConflictPolicy.MERGE)
        listOf(
            "$key-jpa" to (listOf(SampleEntities.book, SampleEntities.author) to options),
            "$key-mybatis" to (listOf(SampleEntities.customer) to options)
        )
    }

    private val boot2 = profile(2, 7, 18)

    @Test
    fun `boot 2 javax configuration renders`() {
        val dir = System.getProperty("generated.dir")?.let(::File)
        val options = opts(
            lombok = true, standards = everything, tests = allTests, docs = OpenApiOptions(annotations = true, spec = true)
        ).copy(conflictPolicy = ConflictPolicy.MERGE)
        val project = SampleEntities.project(javax = true)
        val entities = listOf(SampleEntities.book, SampleEntities.author).map(SampleEntities::javaxModel)
        val plan = Planner(boot2).plan(entities, options)
        val report = PlanExecutor(project).execute(plan.files, { options.conflictPolicy })
        if (dir != null) {
            val target = File(dir, "jpa-boot2")
            target.deleteRecursively()
            for ((path, text) in project.files) File(target, path).apply { parentFile.mkdirs() }.writeText(text)
            File(target, "REPORT.md").writeText(report.toMarkdown())
        }
    }

    @Test
    fun `boot 4 configuration renders`() {
        val options = opts(
            lombok = true, standards = everything, tests = allTests, docs = allDocs, exceptionHandling = true
        )
        val project = SampleEntities.project()
        val plan = Planner(profile(4, 1, 1)).plan(listOf(SampleEntities.book, SampleEntities.author), options)
        val report = PlanExecutor(project).execute(plan.files, { options.conflictPolicy })
        assertTrue(plan.files.any { it.content.contains("org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc") })
        val dir = System.getProperty("generated.dir")?.let(::File) ?: return
        val target = File(dir, "jpa-boot4")
        target.deleteRecursively()
        for ((path, text) in project.files) File(target, path).apply { parentFile.mkdirs() }.writeText(text)
        File(target, "REPORT.md").writeText(report.toMarkdown() + "\n" + plan.warnings.joinToString("\n") { "- $it" })
    }

    /**
     * The flow users hit: generate with the default layout, generate again with the layered one over the same
     * project, then delete the old classes the plugin lists. The result must compile and start without duplicate beans.
     */
    @Test
    fun `generating again with another layout`() {
        val flat = opts(lombok = true, standards = everything, tests = allTests, docs = allDocs, exceptionHandling = true)
        val layered = flat.copy(
            layout = PackageLayout(
                controller = "web.controller", service = "application.service", repository = "infrastructure.persistence",
                mapper = "web.mapper", persistence = "infrastructure.persistence", dto = "web.dto",
                exception = "web.exception", config = "infrastructure.config"
            )
        )
        // flat first, then layered: the handler stays in exception/, so its test must be there too
        relayout("jpa-relayout", flat, layered, handlerTestIn = "com/acme/shop/exception/")
        // layered first, then flat: the handler stays in web/exception/
        relayout("jpa-relayout-reverse", layered, flat, handlerTestIn = "com/acme/shop/web/exception/")
    }

    private fun relayout(name: String, first: GenerationOptions, second: GenerationOptions, handlerTestIn: String) {
        val profile = profile(3, 5)
        val entities = listOf(SampleEntities.book, SampleEntities.author)
        val project = SampleEntities.project()
        PlanExecutor(project).execute(Planner(profile).plan(entities, first).files, { first.conflictPolicy })

        val plan = Planner(profile).plan(entities, second)
        val checked = DuplicateClasses.check(plan.files, project.files.keys)
        assertTrue("old per-entity classes are reported", checked.stale.any { it.endsWith("Service.java") && "BookService" in it })
        assertTrue("old tests are reported", checked.stale.any { it.endsWith("/BookServiceTest.java") })
        assertTrue("shared beans are reused", checked.files.none { it.group == "shared" && it.kind == FileKind.SOURCE })
        val handlerTest = checked.files.single { it.fileName == "GlobalExceptionHandlerTest.java" }
        assertTrue("handler test sits next to the reused handler: ${handlerTest.path}", handlerTest.path.contains(handlerTestIn))
        PlanExecutor(project).execute(checked.files, { second.conflictPolicy })
        checked.stale.forEach { project.files.remove(it) }

        val dir = System.getProperty("generated.dir")?.let(::File) ?: return
        val target = File(dir, name)
        target.deleteRecursively()
        for ((path, text) in project.files) File(target, path).apply { parentFile.mkdirs() }.writeText(text)
        File(target, "REPORT.md").writeText(checked.warnings.joinToString("\n") { "- $it" })
    }

    @Test
    fun `every sample configuration renders`() {
        val dir = System.getProperty("generated.dir")?.let(::File)
        for ((name, v) in variants) {
            val (entities, options) = v
            val project = SampleEntities.project()
            val plan = Planner(profile(3, 5)).plan(entities, options)
            val report = PlanExecutor(project).execute(plan.files, { options.conflictPolicy })
            if (dir != null) {
                val target = File(dir, name)
                target.deleteRecursively()
                for ((path, text) in project.files) {
                    File(target, path).apply { parentFile.mkdirs() }.writeText(text)
                }
                File(target, "REPORT.md").writeText(report.toMarkdown() + "\n" + plan.warnings.joinToString("\n") { "- $it" })
            }
            assumeNotNull(plan)
        }
    }
}
