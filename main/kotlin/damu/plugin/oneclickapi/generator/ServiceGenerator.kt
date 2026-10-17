// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator

import damu.plugin.oneclickapi.util.GeneratorUtils
import damu.plugin.oneclickapi.util.TemplateEngine
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.util.PsiUtil

object ServiceGenerator {

    fun generateServiceContent(
        psiClass: PsiClass,
        servicePackage: PsiDirectory,
        useLombok: Boolean,
        repoType: String
    ): String {
        val className = psiClass.name ?: return ""

        val classPackageName = GeneratorUtils.getPackageNameFromDirectory(servicePackage)
        val parentDirectory = servicePackage.parentDirectory
        val packageName =
            if (parentDirectory != null) GeneratorUtils.getPackageNameFromDirectory(parentDirectory) else ""
        val entityPackageName = PsiUtil.getPackageName(psiClass) ?: ""

        val idType = ClassGenerator.getIdType(psiClass)

        val repositoryPackage = "${packageName}.repository"
        val mapperPackage = "${packageName}.mapper"
        val dtoPackage = "${packageName}.dto.${className.lowercase()}"

        val dataModel = mutableMapOf<String, Any>(
            "className" to className,
            "classPackageName" to classPackageName,
            "repositoryPackage" to repositoryPackage,
            "mapperPackage" to mapperPackage,
            "entityPackageName" to entityPackageName,
            "dtoPackage" to dtoPackage,
            "idType" to idType,
            "useLombok" to useLombok,
            // The template only distinguishes "JPA" (Spring Data repository) from MyBatis mapper access
            "repoType" to if (repoType.startsWith("JPA") || repoType.startsWith("CRUD")) "JPA" else repoType
        )

        return TemplateEngine.processTemplate("Service.java.ft", dataModel)
    }
}