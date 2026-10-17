// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator.upload


object FileDtoGenerator {

    fun generateFiles(packageName: String): Map<String, String> = mapOf(
        "dto/FileDto.java" to generateFileDtoContent("$packageName.dto")
    )

    private fun generateFileDtoContent(packageName: String): String {
        val classPackageName = packageName
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