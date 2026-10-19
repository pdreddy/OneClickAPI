package damu.plugin.oneclickapi.eclipse

import damu.plugin.oneclickapi.core.model.EntityModel
import damu.plugin.oneclickapi.core.options.ConflictPolicy
import damu.plugin.oneclickapi.core.options.GenerationOptions
import damu.plugin.oneclickapi.core.options.TestOptions
import damu.plugin.oneclickapi.core.plan.DuplicateClasses
import damu.plugin.oneclickapi.core.plan.FileKind
import damu.plugin.oneclickapi.core.plan.PlannedFile
import damu.plugin.oneclickapi.core.plan.Plan
import damu.plugin.oneclickapi.core.plan.Planner
import damu.plugin.oneclickapi.core.plan.TemplateEngine
import damu.plugin.oneclickapi.core.preset.Preset
import damu.plugin.oneclickapi.core.preset.PresetCodec
import damu.plugin.oneclickapi.core.preset.PresetStore
import damu.plugin.oneclickapi.core.project.BuildFileEditor
import damu.plugin.oneclickapi.core.project.BuildTool
import damu.plugin.oneclickapi.core.project.DependencyAdvisor
import damu.plugin.oneclickapi.core.project.ProjectDetector
import damu.plugin.oneclickapi.core.project.ProjectProfile
import damu.plugin.oneclickapi.core.write.GenerationReport
import damu.plugin.oneclickapi.core.write.PlanExecutor
import damu.plugin.oneclickapi.core.write.ProjectFiles
import damu.plugin.oneclickapi.core.write.ResolvedFile
import java.io.File

/** Files the generator is about to write, with the warnings found while planning them. */
class Prepared(val files: List<PlannedFile>, val warnings: List<String>)

/**
 * The Eclipse plugin's single entry into the generator: scan, plan, preview, write. It is the Eclipse
 * counterpart of the IntelliJ plugin's GenerationService and holds no UI or workspace types, so it is unit-testable.
 *
 * @param files the module (the Eclipse project that holds `src/main/java`); paths are module-relative
 * @param buildFiles text of the build files that exist in the module (`pom.xml`, `build.gradle(.kts)`)
 * @param libraryCoordinates `group:artifact[:version]` of the libraries on the project's classpath
 * @param existingJavaPaths module-relative `.java` files under `src/main/java` and `src/test/java`
 * @param templateDir optional `.oneclickapi/templates` folder whose files override the built-in templates
 */
class ApiGenerator @JvmOverloads constructor(
    private val files: ProjectFiles,
    buildFiles: Map<String, String>,
    libraryCoordinates: List<String>,
    applicationClass: String?,
    private val existingJavaPaths: List<String>,
    private val templateDir: File? = null
) {
    val profile: ProjectProfile = ProjectDetector.detect(buildFiles, libraryCoordinates).copy(applicationClass = applicationClass)

    /** Presets of `.oneclickapi.yml` first, then the built-in ones. */
    fun presets(): List<Preset> = PresetStore(files).available()

    fun problems(): List<String> = PresetStore(files).load().problems

    /** [GenerationOptions] for [basePackage] with the keys of [values] (the `.oneclickapi.yml` keys) applied. */
    fun options(basePackage: String, values: Map<String, Any?>): GenerationOptions =
        PresetCodec.apply(GenerationOptions(basePackage = basePackage), values)

    /** The `.oneclickapi.yml` keys of [options]; the dialog shows these and hands changed ones back to [refine]. */
    fun values(options: GenerationOptions): Map<String, Any?> = PresetCodec.toValues(options)

    /** [options] with the keys of [values] applied on top. */
    fun refine(options: GenerationOptions, values: Map<String, Any?>): GenerationOptions = PresetCodec.apply(options, values)

    /** Options that generate only the chosen test classes: no CRUD code, the test types ticked. */
    fun testsOnlyOptions(
        basePackage: String,
        service: Boolean,
        controller: Boolean,
        mapper: Boolean,
        integration: Boolean,
        http: Boolean
    ): GenerationOptions = GenerationOptions(
        basePackage = basePackage,
        crud = false,
        tests = TestOptions(
            serviceTests = service,
            controllerTests = controller,
            mapperTests = mapper,
            integrationTests = integration,
            httpFile = http
        )
    )

    fun prepare(models: List<EntityModel>, options: GenerationOptions): Prepared =
        prepare(models, options) { planner -> planner.plan(models, options) }

    /** The test classes only; see [Planner.planTests]. */
    fun prepareTests(models: List<EntityModel>, options: GenerationOptions): Prepared =
        prepare(models, options) { planner -> planner.planTests(models, options) }

    private fun prepare(models: List<EntityModel>, options: GenerationOptions, plan: (Planner) -> Plan): Prepared {
        val planned = plan(Planner(profile, TemplateEngine(templateDir), files::exists))
        val warnings = planned.warnings.toMutableList()
        val duplicates = DuplicateClasses.check(planned.files, existingJavaPaths)
        warnings += duplicates.warnings
        val all = duplicates.files.toMutableList()
        addDependencyEdit(models, options, all, warnings)
        return Prepared(all, warnings)
    }

    /** Dry run for the preview: what each file would become. */
    fun resolve(prepared: Prepared, options: GenerationOptions): List<ResolvedFile> =
        PlanExecutor(files).resolve(prepared.files) { options.conflictPolicy }

    fun execute(prepared: Prepared, options: GenerationOptions, excluded: Set<String>): GenerationReport =
        PlanExecutor(files).execute(prepared.files, { options.conflictPolicy }, excluded, prepared.warnings)

    private fun addDependencyEdit(models: List<EntityModel>, options: GenerationOptions, out: MutableList<PlannedFile>, warnings: MutableList<String>) {
        val missing = DependencyAdvisor.missing(DependencyAdvisor.required(models, options, profile.bootMajor), profile)
        val coverage = options.tests.coverage
        if (missing.isEmpty() && !coverage) return
        val names = (missing.map { it.lib.display } + if (coverage) listOf("JaCoCo coverage report") else emptyList()).joinToString()
        val buildFile = profile.buildFile
        if (buildFile == null) {
            warnings += "Missing in the build: $names. No pom.xml or build.gradle(.kts) found in the project, add them by hand."
            return
        }
        try {
            val text = files.read(buildFile) ?: return
            var updated = BuildFileEditor.addDependencies(profile.buildTool, text, missing.flatMap { it.dependencies })
            if (coverage) updated = BuildFileEditor.addJacoco(profile.buildTool, updated)
            if (updated != text) {
                out.add(0, PlannedFile(buildFile, updated, FileKind.BUILD, "Adds to the build: $names", "build", ConflictPolicy.REPLACE))
            }
            if (coverage) {
                val maven = profile.buildTool == BuildTool.MAVEN
                val html = if (maven) "target/site/jacoco/index.html" else "build/reports/jacoco/test/html/index.html"
                warnings += "Coverage: run ${if (maven) "mvn test" else "./gradlew test"} and open $html for the whole module."
            }
        } catch (e: BuildFileEditor.EditException) {
            warnings += "Missing in the build: $names. Could not edit $buildFile (${e.message}); add them by hand."
        }
    }
}
