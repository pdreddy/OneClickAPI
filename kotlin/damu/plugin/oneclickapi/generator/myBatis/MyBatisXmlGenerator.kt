// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator.myBatis

import damu.plugin.oneclickapi.util.GeneratorUtils
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.util.PsiUtil

object MyBatisXmlGenerator {

    fun generateMapperXmlContent(psiClass: PsiClass, mapperPackage: PsiDirectory): String {
        val className = psiClass.name ?: return ""
        val classPackageName = GeneratorUtils.getPackageNameFromDirectory(mapperPackage)
        val entityPackageName = PsiUtil.getPackageName(psiClass) ?: return ""

        val xmlBuilder = StringBuilder()

        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n")
        xmlBuilder.append("<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n")
        xmlBuilder.append("<mapper namespace=\"${classPackageName}.${className}Mapper\">\n")

        // ResultMap
        xmlBuilder.append("    <resultMap id=\"${className}ResultMap\" type=\"${entityPackageName}.${className}\">\n")
        xmlBuilder.append(generateResultMapFields(psiClass, "        ")) // 8 boşluq girintisi
        xmlBuilder.append("\n    </resultMap>\n")

        // findById query
        xmlBuilder.append("    <select id=\"findById\" resultMap=\"${className}ResultMap\">\n")
        xmlBuilder.append("        SELECT * FROM ${className.lowercase()} WHERE id = #{id}\n")
        xmlBuilder.append("    </select>\n")

        // findAll query
        xmlBuilder.append("    <select id=\"findAll\" resultMap=\"${className}ResultMap\">\n")
        xmlBuilder.append("        SELECT * FROM ${className.lowercase()}\n")
        xmlBuilder.append("    </select>\n")

        // insert query (id olmadan)
        xmlBuilder.append("    <insert id=\"insert\" parameterType=\"${entityPackageName}.${className}\" useGeneratedKeys=\"true\" keyProperty=\"id\">\n")
        xmlBuilder.append("        INSERT INTO ${className.lowercase()} (${getFieldsForInsertWithoutId(psiClass)})\n")
        xmlBuilder.append("        VALUES (${getValuesForInsertWithoutId(psiClass)})\n")
        xmlBuilder.append("    </insert>\n")

        // update query
        xmlBuilder.append("    <update id=\"update\" parameterType=\"${entityPackageName}.${className}\">\n")
        xmlBuilder.append("        UPDATE ${className.lowercase()}\n")
        xmlBuilder.append("        SET ${getFieldsForUpdate(psiClass)}\n")
        xmlBuilder.append("        WHERE id = #{id}\n")
        xmlBuilder.append("    </update>\n")

        // delete query
        xmlBuilder.append("    <delete id=\"delete\">\n")
        xmlBuilder.append("        DELETE FROM ${className.lowercase()} WHERE id = #{id}\n")
        xmlBuilder.append("    </delete>\n")

        xmlBuilder.append("</mapper>")

        return xmlBuilder.toString()
    }

    private fun generateResultMapFields(psiClass: PsiClass, indent: String): String {
        return psiClass.fields.joinToString("\n") { field ->
            "$indent<result property=\"${field.name}\" column=\"${field.name}\"/>"
        }
    }

    private fun getFieldsForInsertWithoutId(psiClass: PsiClass): String {
        return psiClass.fields.filter { it.name != "id" }.joinToString(", ") { it.name }
    }

    private fun getValuesForInsertWithoutId(psiClass: PsiClass): String {
        return psiClass.fields.filter { it.name != "id" }.joinToString(", ") { "#${it.name}" }
    }

    private fun getFieldsForUpdate(psiClass: PsiClass): String {
        return psiClass.fields.filter { it.name != "id" }.joinToString(", ") { "${it.name} = #${it.name}" }
    }
}