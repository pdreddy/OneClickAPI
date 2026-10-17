// Copyright 2026 Damohara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator.upload

import damu.plugin.oneclickapi.generator.ClassGenerator
import damu.plugin.oneclickapi.util.GeneratorUtils
import damu.plugin.oneclickapi.util.PackageUtils
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElementFactory

object FileDtoGenerator {

    fun generateFileDto(
        factory: PsiElementFactory,
        directory: PsiDirectory,
        packageName: String
    ) {
        val dtoPackage = PackageUtils.createPackageIfNotExists(directory, "$packageName.dto")
        val content = generateFileDtoContent(dtoPackage)
        ClassGenerator.createClass(factory, dtoPackage, "FileDto", content)
    }

    private fun generateFileDtoContent(packageName: PsiDirectory): String {
        val classPackageName = GeneratorUtils.getPackageNameFromDirectory(packageName)
        return """
            package $classPackageName;

            public class FileDto {
                private String id;
                private String contentType;
                private String name;

                public FileDto(String id, String contentType, String name) {
                    this.id = id;
                    this.contentType = contentType;
                    this.name = name;
                }

                public String getId() {
                    return id;
                }

                public void setId(String id) {
                    this.id = id;
                }

                public String getContentType() {
                    return contentType;
                }

                public void setContentType(String contentType) {
                    this.contentType = contentType;
                }

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }
            }
        """.trimIndent()
    }
}