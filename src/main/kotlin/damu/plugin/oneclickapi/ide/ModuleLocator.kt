package damu.plugin.oneclickapi.ide

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass

/** Finds the module directory (the one holding `src/main/java`) that a class belongs to. */
object ModuleLocator {

    fun baseDir(psiClass: PsiClass): VirtualFile? {
        val module = ModuleUtilCore.findModuleForPsiElement(psiClass) ?: return null
        val javaRoot = ModuleRootManager.getInstance(module).sourceRoots
            .firstOrNull { it.path.endsWith("src/main/java") || it.path.contains("src/main/java/") }
            ?: return null
        // .../<module>/src/main/java -> <module>
        return javaRoot.parent?.parent?.parent
    }
}
