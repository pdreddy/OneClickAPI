// Copyright 2026 Damohara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.util

import com.intellij.psi.PsiDirectory

object PackageUtils {
    fun createPackageIfNotExists(directory: PsiDirectory, packageName: String): PsiDirectory {
        val packageDirs = packageName.split(".")
        var currentDir = directory

        for (dirName in packageDirs) {
            val existingDir = currentDir.findSubdirectory(dirName)
            currentDir = existingDir ?: run {
                currentDir.createSubdirectory(dirName)
            }
        }

        return currentDir
    }
}