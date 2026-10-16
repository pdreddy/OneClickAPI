// Copyright 2026 Damohara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator

import damu.plugin.oneclickapi.util.GeneratorUtils
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil

object MapperGenerator {

    fun generateMapperContent(psiClass: PsiClass, mapperPackage: PsiDirectory): String {
        val className = psiClass.name ?: return ""
        val classPackageName = GeneratorUtils.getPackageNameFromDirectory(mapperPackage)
        val packageName = mapperPackage.parentDirectory?.let { GeneratorUtils.getPackageNameFromDirectory(it) }
        val entityPackageName = PsiUtil.getPackageName(psiClass) ?: return ""

        val dtoPackage = "${packageName}.dto.${className.lowercase()}"

        return """
    package ${classPackageName};

    import org.mapstruct.Mapper;
    import org.mapstruct.MappingTarget;
    import ${dtoPackage}.request.${className}Request;
    import ${dtoPackage}.response.${className}Response;
    import ${entityPackageName}.${className};

    @Mapper
    public interface ${className}Mapper {
        ${className}Response toResponse(${className} entity);
        ${className} toEntity(${className}Request dto);
        void updateFromDto(${className}Request dto, @MappingTarget ${className} entity);
    }
    """.trimIndent()
    }
}
