package az.mtech.autoapigen.core

import az.mtech.autoapigen.core.options.*
import az.mtech.autoapigen.core.plan.Planner
import az.mtech.autoapigen.core.project.BootVersion
import az.mtech.autoapigen.core.project.ProjectProfile
import az.mtech.autoapigen.core.write.PlanExecutor
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
    private val allTests = TestOptions(true, true, true)
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

    val variants: Map<String, Pair<List<az.mtech.autoapigen.core.model.EntityModel>, GenerationOptions>> = mapOf(
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
        ))
    )

    private val boot2 = ProjectProfile(bootVersion = BootVersion(2, 7, 18))

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
    fun `every sample configuration renders`() {
        val dir = System.getProperty("generated.dir")?.let(::File)
        for ((name, v) in variants) {
            val (entities, options) = v
            val project = SampleEntities.project()
            val plan = Planner(ProjectProfile(bootVersion = BootVersion(3, 5))).plan(entities, options)
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
