// Copyright 2026 Damohara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator.myBatis

import damu.plugin.oneclickapi.generator.ClassGenerator
import damu.plugin.oneclickapi.util.GeneratorUtils
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.util.PsiUtil

object MyBatisServiceGenerator {
    fun generateServiceContent(
        psiClass: PsiClass,
        servicePackage: PsiDirectory,
        useLombok: Boolean
    ): String {
        val className = psiClass.name ?: return ""
        val classPackageName = GeneratorUtils.getPackageNameFromDirectory(servicePackage)
        val packageName = servicePackage.parentDirectory?.let { GeneratorUtils.getPackageNameFromDirectory(it) }
        val entityPackageName = PsiUtil.getPackageName(psiClass) ?: return ""
        val idType = ClassGenerator.getIdType(psiClass)
        val mapperPackage = "${packageName}.mapper"
        val dtoPackage = "${packageName}.dto.${className.lowercase()}"

        val annotations = if (useLombok) {
            """
            @Service
            @RequiredArgsConstructor
            """
        } else {
            "@Service"
        }

        val constructorInjection = if (useLombok) {
            ""
        } else {
            """
            private final ${className}Mapper mapper;

            public ${className}Service(${className}Mapper mapper) {
                this.mapper = mapper;
            }
            """
        }

        val lombokImports = if (useLombok) {
            "import lombok.RequiredArgsConstructor;"
        } else {
            ""
        }

        return """
            package ${classPackageName};
            import org.springframework.stereotype.Service;
            import ${mapperPackage}.${className}Mapper;
            import ${entityPackageName}.${className};
            import ${dtoPackage}.request.${className}Request;
            import ${dtoPackage}.response.${className}Response;
            import java.util.List;
            import java.util.Optional;
            import java.util.stream.Collectors;
            $lombokImports

            $annotations
            public class ${className}Service {
                private final ${className}Mapper mapper;

                $constructorInjection

                public List<${className}Response> findAll() {
                    return mapper.findAll()
                                 .stream()
                                 .map(this::toResponse)
                                 .collect(Collectors.toList());
                }

                public Optional<${className}Response> findById($idType id) {
                    return Optional.ofNullable(mapper.findById(id)).map(this::toResponse);
                }

                public ${className}Response save(${className}Request request) {
                    ${className} entity = toEntity(request);
                    mapper.insert(entity);
                    return toResponse(entity);
                }

                public ${className}Response update($idType id, ${className}Request request) {
                    ${className} existingEntity = mapper.findById(id);
                    if (existingEntity == null) {
                        throw new RuntimeException("${className} not found with id " + id);
                    }
                    updateEntity(existingEntity, request);
                    mapper.update(existingEntity);
                    return toResponse(existingEntity);
                }

                public void delete($idType id) {
                    mapper.delete(id);
                }

                private ${className}Response toResponse(${className} entity) {
                    // Implement mapping logic here
                    return new ${className}Response();
                }

                private ${className} toEntity(${className}Request request) {
                    // Implement mapping logic here
                    return new ${className}();
                }

                private void updateEntity(${className} entity, ${className}Request request) {
                    // Implement update logic here
                }
            }
        """.trimIndent()
    }
}