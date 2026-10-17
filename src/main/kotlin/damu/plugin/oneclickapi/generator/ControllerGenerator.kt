// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator

import damu.plugin.oneclickapi.util.TemplateEngine
import com.intellij.psi.PsiClass

object ControllerGenerator {

    fun generateControllerContent(
        psiClass: PsiClass,
        controllerPackage: String,
        useLombok: Boolean,
        basePath: String = "/api/v1"
    ): String {
        val className = psiClass.name ?: return ""
        val idType = ClassGenerator.getIdType(psiClass)

        val classPackageName = controllerPackage
        val packageName = controllerPackage.substringBeforeLast(".", "")

        val servicePackage = "${packageName}.service"
        val dtoPackage = "${packageName}.dto.${className.lowercase()}"

        val dataModel = mapOf<String, Any>(
            "className" to className,
            "idType" to idType,
            "classPackageName" to classPackageName,
            "servicePackage" to servicePackage,
            "dtoPackage" to dtoPackage,
            "useLombok" to useLombok,
            "basePath" to normalizeBasePath(basePath)
        )

        return TemplateEngine.processTemplate("Controller.java.ft", dataModel)
    }

    // Ensure a single leading slash and no trailing slash; blank means no prefix
    private fun normalizeBasePath(basePath: String): String {
        val trimmed = basePath.trim().trim('/')
        return if (trimmed.isEmpty()) "" else "/$trimmed"
    }
}
