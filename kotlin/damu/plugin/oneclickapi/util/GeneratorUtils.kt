// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.util

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.*

object GeneratorUtils {

    fun getBaseDirectory(psiClass: PsiClass): PsiDirectory? {
        val module = ModuleUtilCore.findModuleForPsiElement(psiClass) ?: return null
        val baseDir = ModuleRootManager.getInstance(module).sourceRoots
            .find { it.path.contains("src/main/java") }
            ?: return null
        return PsiManager.getInstance(module.project).findDirectory(baseDir)
    }

    fun getResourcesDirectory(psiClass: PsiClass): PsiDirectory? {
        val module = ModuleUtilCore.findModuleForPsiElement(psiClass) ?: return null
        val resourcesDir = ModuleRootManager.getInstance(module).sourceRoots
            .find { it.path.contains("src/main/resources") }
            ?: return null
        return PsiManager.getInstance(module.project).findDirectory(resourcesDir)
    }

    fun getPackageNameFromDirectory(directory: PsiDirectory): String {
        val module = ModuleUtilCore.findModuleForPsiElement(directory) ?: return ""
        val sourceRoot = ModuleRootManager.getInstance(module).sourceRoots
            .find { it.path.contains("src/main/java") } ?: return ""
        val sourceRootPath = sourceRoot.path
        val directoryPath = directory.virtualFile.path
        if (!directoryPath.startsWith(sourceRootPath)) return ""
        val relativePath = directoryPath.removePrefix(sourceRootPath).removePrefix("/")
        return relativePath.replace("/", ".").replace("\\", ".")
    }

}