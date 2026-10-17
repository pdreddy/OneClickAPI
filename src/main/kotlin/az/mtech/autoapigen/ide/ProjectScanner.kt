package az.mtech.autoapigen.ide

import az.mtech.autoapigen.core.project.ProjectDetector
import az.mtech.autoapigen.core.project.ProjectProfile
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass

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
        return ProjectDetector.detect(files, coordinates)
    }
}
