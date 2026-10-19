package damu.plugin.oneclickapi.ide

import damu.plugin.oneclickapi.core.project.ProjectDetector
import damu.plugin.oneclickapi.core.project.ProjectProfile
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

/**
 * Reads the build file of the class's module and the libraries the IDE resolved for it, using only public
 * project-model APIs, and hands both to the platform-independent [ProjectDetector].
 */
object ProjectScanner {

    private val BUILD_FILES = listOf("pom.xml", "build.gradle.kts", "build.gradle")

    fun scan(psiClass: PsiClass, baseDir: VirtualFile): ProjectProfile {
        val files = BUILD_FILES.mapNotNull { name ->
            baseDir.findChild(name)?.takeIf { !it.isDirectory }?.let { name to VfsUtilCore.loadText(it) }
        }.toMap()

        val coordinates = mutableListOf<String>()
        val module = ModuleUtilCore.findModuleForPsiElement(psiClass)
        if (module != null) {
            ModuleRootManager.getInstance(module).orderEntries().librariesOnly().forEachLibrary { library ->
                // Library names look like "Maven: org.mapstruct:mapstruct:1.6.3" or "Gradle: org.mapstruct:mapstruct:1.6.3".
                library.name?.let { coordinates += it.substringAfter(": ", it) }
                true
            }
        }
        val profile = ProjectDetector.detect(files, coordinates)
        return if (module == null) profile else profile.copy(applicationClass = applicationClass(module))
    }

    /** The module's `@SpringBootApplication` class; the full-stack API tests name it in `@SpringBootTest`. */
    private fun applicationClass(module: Module): String? {
        val annotation = JavaPsiFacade.getInstance(module.project).findClass(
            "org.springframework.boot.autoconfigure.SpringBootApplication",
            GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
        ) ?: return null
        return AnnotatedElementsSearch.searchPsiClasses(annotation, GlobalSearchScope.moduleScope(module))
            .findAll().mapNotNull { it.qualifiedName }.minOrNull()
    }
}
