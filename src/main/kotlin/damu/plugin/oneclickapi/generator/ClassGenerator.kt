// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator

import com.intellij.psi.PsiClass
import damu.plugin.oneclickapi.generator.myBatis.MyBatisMapperGenerator
import damu.plugin.oneclickapi.generator.myBatis.MyBatisServiceGenerator
import damu.plugin.oneclickapi.generator.myBatis.MyBatisXmlGenerator
import damu.plugin.oneclickapi.generator.upload.*
import damu.plugin.oneclickapi.util.GeneratorUtils

/** Renders a generation plan without creating directories or modifying project files. */
object ClassGenerator {
    fun planApi(
        psiClass: PsiClass,
        path: String,
        repoType: String,
        converterType: String,
        useLombok: Boolean,
        useLiquibase: Boolean,
        liquibasePackagePath: String,
        liquibaseFileName: String,
        liquibaseFormat: String?,
        useExceptionHandling: Boolean,
        generateImageUploadApi: Boolean,
        generatePdfUploadApi: Boolean,
        ormType: String,
        generateCrudApi: Boolean = true,
        basePath: String = "/api/v1"
    ): List<PlannedFile> {
        val files = mutableListOf<PlannedFile>()
        val className = requireNotNull(psiClass.name) { "An unnamed class cannot be generated." }
        require(path.split('.').all { it.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*")) }) {
            "Choose a valid Java target package."
        }
        val needsJava = generateCrudApi || useExceptionHandling || generateImageUploadApi || generatePdfUploadApi
        val javaRoot = if (needsJava) requireNotNull(GeneratorUtils.getBaseDirectory(psiClass)) {
            "No src/main/java source root found for $className."
        }.virtualFile else null
        fun javaFile(relativePath: String, content: String) {
            files += PlannedFile.capture(requireNotNull(javaRoot), path.replace('.', '/') + "/" + relativePath, content)
        }
        if (generateCrudApi) {
            val dtoPackage = "$path.dto.${className.lowercase()}"
            javaFile("dto/${className.lowercase()}/request/${className}Request.java",
                DtoGenerator.generateDtoContent(psiClass, "$dtoPackage.request", "Request", useLombok))
            javaFile("dto/${className.lowercase()}/response/${className}Response.java",
                DtoGenerator.generateDtoContent(psiClass, "$dtoPackage.response", "Response", useLombok))
            if (ormType == "JPA/Hibernate") {
                javaFile("mapper/${className}Mapper.java", MapperGenerator.generateMapperContent(psiClass, "$path.mapper"))
                javaFile("repository/${className}Repository.java", RepositoryGenerator.generateRepositoryContent(psiClass, "$path.repository", repoType))
                javaFile("service/${className}Service.java", ServiceGenerator.generateServiceContent(psiClass, "$path.service", useLombok, "JPA Repository"))
            } else {
                javaFile("mapper/${className}Mapper.java", MyBatisMapperGenerator.generateMapperContent(psiClass, "$path.mapper", true))
                javaFile("service/${className}Service.java", MyBatisServiceGenerator.generateServiceContent(psiClass, "$path.service", useLombok))
                val resources = requireNotNull(GeneratorUtils.getResourcesDirectory(psiClass)) {
                    "No src/main/resources root found for $className's MyBatis mapper."
                }
                files += PlannedFile.capture(resources.virtualFile, "mybatis/mappers/${className}Mapper.xml",
                    MyBatisXmlGenerator.generateMapperXmlContent(psiClass, "$path.mapper"))
            }
            javaFile("controller/${className}Controller.java", ControllerGenerator.generateControllerContent(psiClass, "$path.controller", useLombok, basePath))
        }
        if (generateImageUploadApi || generatePdfUploadApi) {
            FileDtoGenerator.generateFiles(path).forEach { (name, content) -> javaFile(name, content) }
            FileServiceGenerator.generateFiles(psiClass, path).forEach { (name, content) -> javaFile(name, content) }
        }
        if (generateImageUploadApi) ImageUploadApiGenerator.generateFiles(psiClass, path, useLombok)
            .forEach { (name, content) -> javaFile(name, content) }
        if (generatePdfUploadApi) PdfUploadApiGenerator.generateFiles(psiClass, path, useLombok)
            .forEach { (name, content) -> javaFile(name, content) }
        if (useExceptionHandling) javaFile("exception/GlobalExceptionHandler.java",
            ExceptionHandlingGenerator.generateGlobalExceptionHandler("$path.exception"))
        if (useLiquibase && liquibaseFormat != null) {
            val resources = requireNotNull(GeneratorUtils.getResourcesDirectory(psiClass)) {
                "No src/main/resources root found for $className's Liquibase changelog."
            }
            require(liquibaseFileName.isNotBlank() && '/' !in liquibaseFileName && '\\' !in liquibaseFileName) {
                "Choose a Liquibase file name without directory separators."
            }
            val folder = liquibasePackagePath.trim('/')
            var version = 1
            var relativePath: String
            do {
                val name = "$liquibaseFileName-${version.toString().padStart(2, '0')}.${liquibaseFormat.lowercase()}"
                relativePath = if (folder.isEmpty()) name else "$folder/$name"
                version++
            } while (resources.virtualFile.findFileByRelativePath(relativePath) != null)
            files += PlannedFile.capture(resources.virtualFile, relativePath,
                LiquibaseGenerator.generateLiquibaseContent(psiClass, "", liquibaseFormat))
        }
        return files
    }

    fun getIdType(psiClass: PsiClass): String {
        val idField = psiClass.fields.find { it.name.equals("id", ignoreCase = true) } ?: return "Long"
        return idField.type.presentableText
    }
}
