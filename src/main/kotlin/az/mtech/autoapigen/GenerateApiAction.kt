package az.mtech.autoapigen

import az.mtech.autoapigen.core.preset.PresetStore
import az.mtech.autoapigen.core.project.ProjectProfile
import az.mtech.autoapigen.core.write.MemoryFiles
import az.mtech.autoapigen.ide.GenerateApiDialog
import az.mtech.autoapigen.ide.GenerationService
import az.mtech.autoapigen.ide.IdeProjectFiles
import az.mtech.autoapigen.ide.ModuleLocator
import az.mtech.autoapigen.ide.ProjectScanner
import az.mtech.autoapigen.util.PsiUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil

class GenerateApiAction : DumbAwareAction("Generate API") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val psiElements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY)
        val editor = e.getData(LangDataKeys.EDITOR)
        val psiFile = e.getData(LangDataKeys.PSI_FILE)

        val psiClasses = mutableListOf<PsiClass>()
        if (psiElements != null && psiElements.isNotEmpty()) {
            for (element in psiElements) {
                val psiClass = element as? PsiClass ?: PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
                if (psiClass != null) psiClasses.add(psiClass)
            }
        } else if (editor != null && psiFile != null) {
            val element = psiFile.findElementAt(editor.caretModel.offset)
            val psiClass = element as? PsiClass ?: PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
            if (psiClass != null) psiClasses.add(psiClass)
        }

        val projectRoot = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val presetStore = PresetStore(projectRoot?.let { IdeProjectFiles(it) } ?: MemoryFiles())

        // The first class that can be generated for decides which module the info line describes.
        val firstClass = ReadAction.compute<PsiClass?, RuntimeException> {
            psiClasses.firstOrNull { PsiUtils.isEntityClass(it) || PsiUtils.isPojoClass(it) } ?: psiClasses.firstOrNull()
        }
        val profile = firstClass?.let { cls ->
            ReadAction.compute<ProjectProfile?, RuntimeException> {
                ModuleLocator.baseDir(cls)?.let { dir -> ProjectScanner.scan(cls, dir) }
            }
        }

        // Always open the dialog, even if no class is selected: classes can be added there.
        val dialog = GenerateApiDialog(psiClasses, project, presetStore, profile)
        if (!dialog.showAndGet()) return

        GenerationService.run(
            project = project,
            selected = dialog.getSelectedClasses(),
            contextClass = firstClass,
            options = dialog.buildOptions(),
            preview = dialog.wantsPreview(),
            projectRoot = projectRoot
        )
    }
}
