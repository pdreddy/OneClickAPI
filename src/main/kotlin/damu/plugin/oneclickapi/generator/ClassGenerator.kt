// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator

import damu.plugin.oneclickapi.generator.MapperGenerator.generateMapperContent
import damu.plugin.oneclickapi.util.PackageUtils
import damu.plugin.oneclickapi.generator.ControllerGenerator.generateControllerContent
import damu.plugin.oneclickapi.generator.DtoGenerator.generateDtoContent
import damu.plugin.oneclickapi.generator.ExceptionHandlingGenerator.generateGlobalExceptionHandler
import damu.plugin.oneclickapi.generator.RepositoryGenerator.generateRepositoryContent
import damu.plugin.oneclickapi.generator.ServiceGenerator.generateServiceContent
import damu.plugin.oneclickapi.generator.LiquibaseGenerator.generateLiquibaseContent
import damu.plugin.oneclickapi.generator.myBatis.MyBatisMapperGenerator
import damu.plugin.oneclickapi.generator.myBatis.MyBatisServiceGenerator
import damu.plugin.oneclickapi.generator.myBatis.MyBatisXmlGenerator
import damu.plugin.oneclickapi.generator.upload.FileDtoGenerator
import damu.plugin.oneclickapi.generator.upload.FileServiceGenerator
import damu.plugin.oneclickapi.generator.upload.ImageUploadApiGenerator
import damu.plugin.oneclickapi.generator.upload.PdfUploadApiGenerator
import damu.plugin.oneclickapi.util.GeneratorUtils
import com.intellij.psi.*
import com.intellij.openapi.application.runWriteAction

object ClassGenerator {

    /**
     * Generate API components based on the selected ORM type and other configurations.
     */
    fun generateApi(
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
        ormType: String, // "JPA/Hibernate" or "MyBatis"
        generateCrudApi: Boolean = true, // NEW PARAMETER - controls CRUD generation
        basePath: String = "/api/v1"
    ) {
        val project = psiClass.project
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        val directory = GeneratorUtils.getBaseDirectory(psiClass) ?: return
        val resourcesDirectory = GeneratorUtils.getResourcesDirectory(psiClass) ?: return

        runWriteAction {
            // CRUD API components - only if generateCrudApi is true
            if (generateCrudApi) {
                createDtoClasses(psiClass, factory, directory, path, useLombok)
                createMapperClass(psiClass, factory, directory, path, ormType)
                if (ormType == "JPA/Hibernate") {
                    createRepositoryClass(psiClass, factory, directory, repoType, path)
                }
                createServiceClass(psiClass, factory, directory, path, useLombok, ormType)
                createControllerClass(psiClass, factory, directory, path, useLombok, basePath)
            }

            // File upload APIs - independent
            if (generateImageUploadApi || generatePdfUploadApi) {
                FileDtoGenerator.generateFileDto(factory, directory, path)
                FileServiceGenerator.generateFileService(psiClass, factory, directory, path)
            }
            if (generateImageUploadApi) {
                ImageUploadApiGenerator.generateImageUploadApi(psiClass, factory, directory, path, useLombok)
            }
            if (generatePdfUploadApi) {
                PdfUploadApiGenerator.generatePdfUploadApi(psiClass, factory, directory, path, useLombok)
            }

            // Liquibase scripts - independent (but needs class)
            if (useLiquibase && liquibaseFormat != null) {
                createLiquibaseFiles(
                    psiClass,
                    factory,
                    directory,
                    resourcesDirectory,
                    liquibasePackagePath,
                    liquibaseFileName,
                    liquibaseFormat
                )
            }

            // Global Exception Handling - independent
            if (useExceptionHandling) {
                createGlobalExceptionHandler(psiClass, factory, directory, path)
            }
        }
    }

    /**
     * Create DTO classes (Request and Response).
     */
    private fun createDtoClasses(
        psiClass: PsiClass,
        factory: PsiElementFactory,
        directory: PsiDirectory,
        packageName: String,
        useLombok: Boolean
    ) {
        val className = psiClass.name ?: return
        val dtoPackage = PackageUtils.createPackageIfNotExists(directory, "$packageName.dto")
        val requestPackage = PackageUtils.createPackageIfNotExists(dtoPackage, "${className.lowercase()}.request")
        val responsePackage = PackageUtils.createPackageIfNotExists(dtoPackage, "${className.lowercase()}.response")

        createClass(
            factory,
            requestPackage,
            "${className}Request",
            generateDtoContent(psiClass, requestPackage, "Request", useLombok)
        )

        createClass(
            factory,
            responsePackage,
            "${className}Response",
            generateDtoContent(psiClass, responsePackage, "Response", useLombok)
        )
    }

    /**
     * Create Mapper interface (MapStruct for Hibernate, MyBatis Mapper for MyBatis).
     */
    private fun createMapperClass(
        psiClass: PsiClass,
        factory: PsiElementFactory,
        directory: PsiDirectory,
        packageName: String,
        ormType: String
    ) {
        val className = psiClass.name ?: return
        val mapperPackage = PackageUtils.createPackageIfNotExists(directory, "$packageName.mapper")

        val content = if (ormType == "JPA/Hibernate") {
            generateMapperContent(psiClass, mapperPackage)
        } else {
            MyBatisMapperGenerator.generateMapperContent(psiClass, mapperPackage, true)
        }

        createClass(factory, mapperPackage, "${className}Mapper", content)

        // For MyBatis, generate XML configuration file
        if (ormType == "MyBatis") {
            val resourcesDirectory = GeneratorUtils.getResourcesDirectory(psiClass) ?: return
            val myBatisMappersDirectory = createOrFindDirectory(resourcesDirectory, "mybatis/mappers")
            val xmlContent = MyBatisXmlGenerator.generateMapperXmlContent(psiClass, mapperPackage)
            createFile(factory, myBatisMappersDirectory, "${className}Mapper.xml", xmlContent)
        }
    }

    /**
     * Create Repository interface (Only for JPA/Hibernate).
     */
    private fun createRepositoryClass(
        psiClass: PsiClass,
        factory: PsiElementFactory,
        directory: PsiDirectory,
        option: String,
        packageName: String
    ) {
        val className = psiClass.name ?: return
        val repositoryPackage = PackageUtils.createPackageIfNotExists(directory, "$packageName.repository")

        createClass(
            factory,
            repositoryPackage,
            "${className}Repository",
            generateRepositoryContent(psiClass, repositoryPackage, option)
        )
    }

    /**
     * Create Service class.
     */
    private fun createServiceClass(
        psiClass: PsiClass,
        factory: PsiElementFactory,
        directory: PsiDirectory,
        packageName: String,
        useLombok: Boolean,
        ormType: String
    ) {
        val className = psiClass.name ?: return
        val servicePackage = PackageUtils.createPackageIfNotExists(directory, "$packageName.service")

        val content = if (ormType == "JPA/Hibernate") {
            generateServiceContent(psiClass, servicePackage, useLombok, "JPA Repository")
        } else {
            MyBatisServiceGenerator.generateServiceContent(psiClass, servicePackage, useLombok)
        }

        createClass(factory, servicePackage, "${className}Service", content)
    }

    /**
     * Create Controller class.
     */
    private fun createControllerClass(
        psiClass: PsiClass,
        factory: PsiElementFactory,
        directory: PsiDirectory,
        packageName: String,
        useLombok: Boolean,
        basePath: String
    ) {
        val className = psiClass.name ?: return
        val controllerPackage = PackageUtils.createPackageIfNotExists(directory, "$packageName.controller")

        createClass(
            factory,
            controllerPackage,
            "${className}Controller",
            generateControllerContent(psiClass, controllerPackage, useLombok, basePath)
        )
    }

    /**
     * Create Liquibase files.
     */
    private fun createLiquibaseFiles(
        psiClass: PsiClass,
        factory: PsiElementFactory,
        directory: PsiDirectory,
        resourcesDirectory: PsiDirectory,
        packageName: String,
        fileName: String,
        format: String
    ) {
        val className = psiClass.name ?: return
        val liquibaseDirPath = packageName
        val liquibaseDirectory = createOrFindDirectory(resourcesDirectory, liquibaseDirPath)
        val liquibasePackageName = GeneratorUtils.getPackageNameFromDirectory(liquibaseDirectory)

        val liquibaseFileName = getUniqueLiquibaseFileName(resourcesDirectory, packageName, fileName, format)
        val liquibaseContent = generateLiquibaseContent(psiClass, liquibasePackageName, format)

        createFile(factory, liquibaseDirectory, liquibaseFileName, liquibaseContent)
    }

    /**
     * Create Global Exception Handler.
     */
    private fun createGlobalExceptionHandler(
        psiClass: PsiClass,
        factory: PsiElementFactory,
        directory: PsiDirectory,
        packageName: String
    ) {
        val exceptionPackage = PackageUtils.createPackageIfNotExists(directory, "$packageName.exception")
        val className = "GlobalExceptionHandler"
        val content = generateGlobalExceptionHandler(exceptionPackage)
        createClass(factory, exceptionPackage, className, content)
    }

    /**
     * Utility function to create or find a directory.
     */
    private fun createOrFindDirectory(baseDir: PsiDirectory, path: String): PsiDirectory {
        var currentDir = baseDir
        val pathSegments = path.split("/")
        for (segment in pathSegments) {
            var subDir = currentDir.findSubdirectory(segment)
            if (subDir == null) {
                subDir = currentDir.createSubdirectory(segment)
            }
            currentDir = subDir
        }
        return currentDir
    }

    /**
     * Utility function to create a file.
     */
    private fun createFile(
        factory: PsiElementFactory,
        directory: PsiDirectory,
        fileName: String,
        content: String
    ) {
        val psiFile = directory.createFile(fileName)
        psiFile.viewProvider.document?.setText(content)
    }

    /**
     * Utility function to create a class.
     */
    internal fun createClass(
        factory: PsiElementFactory,
        directory: PsiDirectory,
        className: String,
        content: String
    ) {
        val fileName = "$className.java"
        val existingFile = directory.findFile(fileName)
        if (existingFile != null) {
            return
        }
        runWriteAction {
            val psiFile = directory.createFile(fileName) as PsiFile
            val psiDocument = PsiDocumentManager.getInstance(directory.project).getDocument(psiFile)
            val normalizedContent = content.replace("\r\n", "\n")
                .replace("\r", "\n")

            psiDocument?.setText(normalizedContent)
            PsiDocumentManager.getInstance(directory.project).commitDocument(psiDocument!!)
        }
    }

    /**
     * Get the ID type of the entity.
     */
    fun getIdType(psiClass: PsiClass): String {
        val idField = psiClass.fields.find { it.name.equals("id", ignoreCase = true) } ?: return "Long"
        return idField.type.presentableText
    }


    /**
     * Get the unique Liquibase file name.
     */
    private fun getUniqueLiquibaseFileName(
        resourcesDirectory: PsiDirectory,
        packageName: String,
        baseFileName: String,
        format: String
    ): String {
        val changelogDirectory = createOrFindDirectory(resourcesDirectory, packageName)
        val files = changelogDirectory.files.map { it.name }
        var version = 1
        var uniqueFileName: String
        do {
            uniqueFileName = "$baseFileName-${version.toString().padStart(2, '0')}.${format.lowercase()}"
            version++
        } while (files.contains(uniqueFileName))
        return uniqueFileName
    }
}