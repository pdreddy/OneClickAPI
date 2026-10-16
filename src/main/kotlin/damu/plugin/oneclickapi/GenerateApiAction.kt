// Copyright 2026 Damohara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi

import damu.plugin.oneclickapi.generator.ClassGenerator
import damu.plugin.oneclickapi.util.PsiUtils
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GenerateApiAction : DumbAwareAction("Generate API") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            println("Project is null")
            return
        }

        val psiElements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY)
        val editor = e.getData(LangDataKeys.EDITOR)
        val psiFile = e.getData(LangDataKeys.PSI_FILE)

        val psiClasses = mutableListOf<PsiClass>()
        if (psiElements != null && psiElements.isNotEmpty()) {
            // Multi-selection support
            for (element in psiElements) {
                val psiClass = when (element) {
                    is PsiClass -> element
                    else -> PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
                }
                if (psiClass != null) {
                    psiClasses.add(psiClass)
                }
            }
        } else if (editor != null && psiFile != null) {
            // Single selection in editor
            val offset = editor.caretModel.offset
            val element = psiFile.findElementAt(offset)
            val psiClass = when (element) {
                is PsiClass -> element
                else -> PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
            }
            if (psiClass != null) {
                psiClasses.add(psiClass)
            }
        }

        // Determine default ORM type (for initial display)
        val entityClasses = psiClasses.filter { PsiUtils.isEntityClass(it) }
        val pojoClasses = psiClasses.filter { PsiUtils.isPojoClass(it) }
        val defaultOrmType = if (entityClasses.isNotEmpty()) "JPA/Hibernate" else "MyBatis"

        // Show dialog - ALWAYS open, even if no classes selected
        val dialog = GenerateApiDialog(psiClasses, project, defaultOrmType)
        if (dialog.showAndGet()) {
            val selectedClasses = dialog.getSelectedClasses()
            val path = dialog.getPath()
            val generateCrudApi = dialog.generateCrudApi()
            val useLiquibase = dialog.useLiquibase()
            val useExceptionHandling = dialog.useExceptionHandling()
            val generateImageUploadApi = dialog.generateImageUploadApi()
            val generatePdfUploadApi = dialog.generatePdfUploadApi()

            // Get all parameters
            val repoType = dialog.getRepoType()
            val useLombok = dialog.useLombok()
            val converterType = dialog.getConverterType()
            val liquibasePackagePath = dialog.getLiquibasePackagePath()
            val liquibaseFormat = if (useLiquibase) dialog.getLiquibaseFormat() else null

            // Process each selected class for CRUD and/or Liquibase
            if (selectedClasses.isNotEmpty()) {
                for ((index, psiClass) in selectedClasses.withIndex()) {
                    // Determine ORM type for each class individually
                    val classOrmType = if (PsiUtils.isEntityClass(psiClass)) {
                        "JPA/Hibernate"
                    } else if (PsiUtils.isPojoClass(psiClass)) {
                        "MyBatis"
                    } else {
                        "JPA/Hibernate" // default
                    }

                    val liquibaseFileName = if (selectedClasses.size == 1) {
                        dialog.getLiquibaseFileName()
                    } else {
                        createLiquibaseFileName(psiClass, index + 1)
                    }

                    // Call generateApi with all options, but only selected ones will be generated
                    ClassGenerator.generateApi(
                        psiClass,
                        path,
                        repoType,
                        converterType,
                        useLombok,
                        useLiquibase && generateCrudApi, // Liquibase only if CRUD is also enabled for this class
                        liquibasePackagePath,
                        liquibaseFileName,
                        liquibaseFormat,
                        false, // Exception handling is independent
                        false, // Image upload is independent
                        false, // PDF upload is independent
                        classOrmType,
                        generateCrudApi, // Pass CRUD flag
                        dialog.getBasePath()
                    )
                }
            }

            // Independent features (no class needed)
            // Use first class or any class just for context if available
            val contextClass = selectedClasses.firstOrNull() ?: psiClasses.firstOrNull()

            if (contextClass != null) {
                // Exception Handling (independent)
                if (useExceptionHandling) {
                    ClassGenerator.generateApi(
                        contextClass,
                        path,
                        repoType,
                        converterType,
                        useLombok,
                        false, // no liquibase
                        liquibasePackagePath,
                        "",
                        null,
                        true, // exception handling
                        false,
                        false,
                        "JPA/Hibernate",
                        false // no CRUD
                    )
                }

                // Image Upload API (independent)
                if (generateImageUploadApi) {
                    ClassGenerator.generateApi(
                        contextClass,
                        path,
                        repoType,
                        converterType,
                        useLombok,
                        false,
                        liquibasePackagePath,
                        "",
                        null,
                        false,
                        true, // image upload
                        false,
                        "JPA/Hibernate",
                        false
                    )
                }

                // PDF Upload API (independent)
                if (generatePdfUploadApi) {
                    ClassGenerator.generateApi(
                        contextClass,
                        path,
                        repoType,
                        converterType,
                        useLombok,
                        false,
                        liquibasePackagePath,
                        "",
                        null,
                        false,
                        false,
                        true, // pdf upload
                        "JPA/Hibernate",
                        false
                    )
                }

                // Liquibase without CRUD (independent)
                if (useLiquibase && !generateCrudApi && selectedClasses.isNotEmpty()) {
                    for ((index, psiClass) in selectedClasses.withIndex()) {
                        val liquibaseFileName = if (selectedClasses.size == 1) {
                            dialog.getLiquibaseFileName()
                        } else {
                            createLiquibaseFileName(psiClass, index + 1)
                        }

                        ClassGenerator.generateApi(
                            psiClass,
                            path,
                            repoType,
                            converterType,
                            useLombok,
                            true, // liquibase
                            liquibasePackagePath,
                            liquibaseFileName,
                            liquibaseFormat,
                            false,
                            false,
                            false,
                            "JPA/Hibernate",
                            false // no CRUD
                        )
                    }
                }
            }
        }
    }

    private fun createLiquibaseFileName(psiClass: PsiClass, index: Int): String {
        val now = LocalDateTime.now()
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now)
        val description = psiClass.name?.lowercase()?.replace("entity", "")?.trim() ?: "changelog"
        return "${timestamp}_${description}"
    }
}