// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator

import damu.plugin.oneclickapi.util.GeneratorUtils
import damu.plugin.oneclickapi.util.TemplateEngine
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.PsiClassReferenceType

object DtoGenerator {

    fun generateDtoContent(
        psiClass: PsiClass,
        packagePath: PsiDirectory,
        classType: String,
        useLombok: Boolean
    ): String {
        val className = psiClass.name ?: return ""
        val classPackageName = GeneratorUtils.getPackageNameFromDirectory(packagePath)

        val imports = mutableSetOf<String>()
        val fieldsList = mutableListOf<Map<String, Any>>()

        psiClass.allFields.forEach { field ->
            val fieldAnnotations = mutableListOf<String>()

            field.annotations.forEach { annotation ->
                val qualifiedName = annotation.qualifiedName

                if (qualifiedName != null && (qualifiedName.startsWith("jakarta.validation.constraints") ||
                            qualifiedName.startsWith("javax.validation.constraints"))
                ) {

                    fieldAnnotations.add("@${annotation.nameReferenceElement?.text}${annotation.parameterList.text}")

                    imports.add(qualifiedName)
                }
            }

            fieldsList.add(
                mapOf(
                    "type" to field.type.presentableText,
                    "name" to field.name,
                    "annotations" to fieldAnnotations
                )
            )

            collectTypeImports(field, imports)
        }

        val dataModel = mapOf(
            "classPackageName" to classPackageName,
            "className" to className,
            "classType" to classType,
            "useLombok" to useLombok,
            "fields" to fieldsList,
            "importList" to imports.sorted()
        )

        return TemplateEngine.processTemplate("Dto.java.ft", dataModel)
    }

    private fun collectTypeImports(field: PsiField, imports: MutableSet<String>) {
        val psiType = field.type
        if (psiType is PsiPrimitiveType) return
        val qualifiedName = (psiType as? PsiClassReferenceType)?.resolve()?.qualifiedName
        if (qualifiedName != null && !qualifiedName.startsWith("java.lang")) {
            imports.add(qualifiedName)
        }
    }
}
