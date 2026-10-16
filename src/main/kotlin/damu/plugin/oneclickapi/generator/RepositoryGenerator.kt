// Copyright 2026 Damohara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator

import damu.plugin.oneclickapi.util.GeneratorUtils
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.util.PsiUtil

object RepositoryGenerator {

    fun generateRepositoryContent(
        psiClass: PsiClass,
        repositoryPackage: PsiDirectory,
        repoType: String
    ): String {
        val className = psiClass.name ?: return ""
        val classPackageName = GeneratorUtils.getPackageNameFromDirectory(repositoryPackage)
        val entityPackageName = PsiUtil.getPackageName(psiClass) ?: return ""

        val (repositoryType, repositoryImport) = when (repoType) {
            "JPA Repository" -> "JpaRepository" to "org.springframework.data.jpa.repository.JpaRepository"
            "CRUD Repository" -> "CrudRepository" to "org.springframework.data.repository.CrudRepository"
            else -> throw IllegalArgumentException("Unknown repository type: $repoType")
        }

        val idType = ClassGenerator.getIdType(psiClass)

        return """
            package ${classPackageName};

            import $repositoryImport;
            import ${entityPackageName}.${className};

            public interface ${className}Repository extends $repositoryType<${className}, $idType> {
            }
        """.trimIndent()
    }
}
