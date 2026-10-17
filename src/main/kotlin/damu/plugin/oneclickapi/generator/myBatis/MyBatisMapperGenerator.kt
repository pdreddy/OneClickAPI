// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator.myBatis

import damu.plugin.oneclickapi.generator.ClassGenerator
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiUtil

object MyBatisMapperGenerator {

    fun generateMapperContent(psiClass: PsiClass, mapperPackage: String, useXmlMapper: Boolean): String {
        val className = psiClass.name ?: return ""
        val classPackageName = mapperPackage
        val entityPackageName = PsiUtil.getPackageName(psiClass) ?: return ""
        val idType = ClassGenerator.getIdType(psiClass)

        return if (useXmlMapper) {
            generateXmlBasedMapperContent(className, classPackageName, entityPackageName, idType)
        } else {
            generateAnnotationBasedMapperContent(className, classPackageName, entityPackageName, idType, psiClass)
        }
    }

    private fun generateXmlBasedMapperContent(
        className: String,
        classPackageName: String,
        entityPackageName: String,
        idType: String
    ): String {
        return """
            package ${classPackageName};
            import org.apache.ibatis.annotations.Mapper;
            import java.util.List;
            import ${entityPackageName}.${className};
            @Mapper
            public interface ${className}Mapper {
                ${className} findById($idType id);
                List<${className}> findAll();
                void insert(${className} entity);
                void update(${className} entity);
                void delete($idType id);
            }
        """.trimIndent()
    }

    private fun generateAnnotationBasedMapperContent(
        className: String,
        classPackageName: String,
        entityPackageName: String,
        idType: String,
        psiClass: PsiClass
    ): String {
        return """
            package ${classPackageName};
            import org.apache.ibatis.annotations.*;
            import java.util.List;
            import ${entityPackageName}.${className};
            @Mapper
            public interface ${className}Mapper {
                @Select("SELECT * FROM ${className.lowercase()} WHERE id = #{id}")
                ${className} findById($idType id);
                @Select("SELECT * FROM ${className.lowercase()}")
                List<${className}> findAll();
                @Insert("INSERT INTO ${className.lowercase()} (${getFieldsForInsert(psiClass)}) VALUES (${
            getValuesForInsert(
                psiClass
            )
        })")
                @Options(useGeneratedKeys = true, keyProperty = "id")
                void insert(${className} entity);
                @Update("UPDATE ${className.lowercase()} SET ${getFieldsForUpdate(psiClass)} WHERE id = #{id}")
                void update(${className} entity);
                @Delete("DELETE FROM ${className.lowercase()} WHERE id = #{id}")
                void delete($idType id);
            }
        """.trimIndent()
    }

    private fun getFieldsForInsert(psiClass: PsiClass): String {
        return psiClass.fields.filter { it.name != "id" }.joinToString(", ") { it.name }
    }

    private fun getValuesForInsert(psiClass: PsiClass): String {
        return psiClass.fields.filter { it.name != "id" }.joinToString(", ") { "#${it.name}" }
    }

    private fun getFieldsForUpdate(psiClass: PsiClass): String {
        return psiClass.fields.filter { it.name != "id" }.joinToString(", ") { "${it.name} = #${it.name}" }
    }
}