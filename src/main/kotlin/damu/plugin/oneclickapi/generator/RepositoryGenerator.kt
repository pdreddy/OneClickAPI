// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator

import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiUtil

object RepositoryGenerator {

    fun generateRepositoryContent(
        psiClass: PsiClass,
        repositoryPackage: String,
        repoType: String
    ): String {
        val className = psiClass.name ?: return ""
        val classPackageName = repositoryPackage
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
