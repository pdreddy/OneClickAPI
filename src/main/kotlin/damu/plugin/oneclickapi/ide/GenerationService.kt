package damu.plugin.oneclickapi.ide

import damu.plugin.oneclickapi.core.model.EntityModel
import damu.plugin.oneclickapi.core.options.ConflictPolicy
import damu.plugin.oneclickapi.core.options.GenerationOptions
import damu.plugin.oneclickapi.core.plan.FileKind
import damu.plugin.oneclickapi.core.plan.PlannedFile
import damu.plugin.oneclickapi.core.plan.Planner
import damu.plugin.oneclickapi.core.plan.TemplateEngine
import damu.plugin.oneclickapi.core.preset.PresetStore
import damu.plugin.oneclickapi.core.project.BuildFileEditor
import damu.plugin.oneclickapi.core.project.DependencyAdvisor
import damu.plugin.oneclickapi.core.write.GenerationReport
import damu.plugin.oneclickapi.core.write.PlanExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import java.io.File

/** Glue between the dialog, the platform-independent planner and the IDE: scan, plan, preview, write, report. */
object GenerationService {

    fun run(
        project: Project,
        selected: List<PsiClass>,
        contextClass: PsiClass?,
        options: GenerationOptions,
        preview: Boolean,
        projectRoot: VirtualFile?
    ) {
        // Features that need no class (exception handler, uploads) still need a module to write into.
        val classes = if (selected.isNotEmpty()) selected else listOfNotNull(contextClass)
        val effective = if (selected.isEmpty()) options.copy(crud = false, liquibase = null) else options
        if (classes.isEmpty()) {
            notify(project, "Select a class so the target module can be determined.", NotificationType.WARNING)
            return
        }

        // (module directory or null, classes in it, names for the error message)
        val groups = ReadAction.compute<List<Triple<VirtualFile?, List<PsiClass>, String>>, RuntimeException> {
            classes.groupBy { ModuleLocator.baseDir(it)?.path }.map { (_, group) ->
                Triple(ModuleLocator.baseDir(group.first()), group, group.joinToString { it.name ?: "?" })
            }
        }
        val reports = mutableListOf<GenerationReport>()
        for ((moduleDir, group, names) in groups) {
            if (moduleDir == null) {
                notify(project, "Cannot find src/main/java for $names; skipped.", NotificationType.WARNING)
                continue
            }
            runForModule(project, group, moduleDir, effective, preview, projectRoot)?.let { reports += it }
        }

        if (reports.isEmpty()) return
        val merged = GenerationReport(reports.flatMap { it.entries }, reports.flatMap { it.warnings }.distinct())
        ReportDialog(project, merged).show()
    }

    private fun runForModule(
        project: Project,
        classes: List<PsiClass>,
        moduleDir: VirtualFile,
        options: GenerationOptions,
        preview: Boolean,
        projectRoot: VirtualFile?
    ): GenerationReport? {
        val (models, profile) = ReadAction.compute<Pair<List<EntityModel>, damu.plugin.oneclickapi.core.project.ProjectProfile>, RuntimeException> {
            classes.mapNotNull { PsiModelReader.read(it, moduleDir) } to ProjectScanner.scan(classes.first(), moduleDir)
        }
        val files = IdeProjectFiles(moduleDir)
        val templates = projectRoot?.let { File(it.path, PresetStore.TEMPLATE_DIR) }?.takeIf { it.isDirectory }

        val plan = try {
            Planner(profile, TemplateEngine(templates), files::exists).plan(models, options)
        } catch (e: Exception) {
            notify(project, "Could not prepare the files: ${e.message}", NotificationType.ERROR)
            return null
        }

        val allFiles = plan.files.toMutableList()
        val warnings = plan.warnings.toMutableList()
        addDependencyEdit(models, options, profile, files, preview, allFiles, warnings)

        var policyFor: (PlannedFile) -> ConflictPolicy = { options.conflictPolicy }
        var excluded: Set<String> = emptySet()
        if (preview) {
            val dialog = PreviewDialog(project, allFiles, PlanExecutor(files), options.conflictPolicy, warnings)
            if (!dialog.showAndGet()) return null
            policyFor = dialog::policyFor
            excluded = dialog.excludedPaths()
        }

        val report = WriteCommandAction.writeCommandAction(project)
            .withName("Generate API")
            .compute<GenerationReport, RuntimeException> {
                PlanExecutor(files).execute(allFiles, policyFor, excluded, warnings)
            }
        // New dependencies (e.g. springdoc) were written to pom.xml / build.gradle: reload the build so the IDE
        // resolves their imports instead of showing red until the user syncs by hand.
        if (allFiles.any { it.kind == FileKind.BUILD && it.path !in excluded }) reloadBuild(project)
        return report
    }

    private fun reloadBuild(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            val actions = ActionManager.getInstance()
            val action = listOf("ExternalSystem.RefreshAllProjects", "Maven.ReimportProject")
                .firstNotNullOfOrNull { actions.getAction(it) } ?: return@invokeLater
            try {
                ActionUtil.invokeAction(action, SimpleDataContext.getProjectContext(project), "OneClickAPI", null, null)
            } catch (e: Exception) {
                notify(project, "Dependencies were added; reload the Gradle/Maven project to resolve them.", NotificationType.INFORMATION)
            }
        }
    }

    private fun addDependencyEdit(
        models: List<EntityModel>,
        options: GenerationOptions,
        profile: damu.plugin.oneclickapi.core.project.ProjectProfile,
        files: IdeProjectFiles,
        preview: Boolean,
        out: MutableList<PlannedFile>,
        warnings: MutableList<String>
    ) {
        val missing = DependencyAdvisor.missing(DependencyAdvisor.required(models, options, profile.bootMajor), profile)
        if (missing.isEmpty()) return
        val names = missing.joinToString { it.lib.display }
        val buildFile = profile.buildFile
        if (buildFile == null) {
            warnings += "Missing dependencies: $names. No pom.xml or build.gradle(.kts) found in the module, add them by hand."
            return
        }
        if (!preview) {
            warnings += "Missing dependencies: $names. Turn on the preview to have them added to $buildFile."
            return
        }
        try {
            val text = files.read(buildFile) ?: return
            val updated = BuildFileEditor.addDependencies(profile.buildTool, text, missing.flatMap { it.dependencies })
            if (updated != text) {
                out.add(0, PlannedFile(buildFile, updated, FileKind.BUILD, "Adds missing dependencies: $names", "build", ConflictPolicy.REPLACE))
            }
        } catch (e: BuildFileEditor.EditException) {
            warnings += "Missing dependencies: $names. Could not edit $buildFile (${e.message}); add them by hand."
        }
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup("OneClickAPI")
            .createNotification(message, type).notify(project)
    }
}
