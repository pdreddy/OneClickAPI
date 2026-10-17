// Copyright 2026 Damohara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier

object LiquibaseGenerator {

    private const val INDENT_SIZE = 4

    fun generateLiquibaseContent(psiClass: PsiClass, packageName: String, format: String): String {
        return when (format.lowercase()) {
            "yaml" -> generateYamlContent(psiClass)
            "xml" -> generateXmlContent(psiClass)
            "json" -> generateJsonContent(psiClass)
            "sql" -> generateSqlContent(psiClass)
            else -> throw IllegalArgumentException("Unknown format: $format")
        }
    }

    private fun generateYamlContent(psiClass: PsiClass): String {
        val columns = generateColumnsYaml(psiClass)
        return """
        |databaseChangeLog:
        |${indent(1)}- changeSet:
        |${indent(2)}id: ${psiClass.name}-01
        |${indent(2)}author: autoapigen
        |${indent(2)}changes:
        |${indent(3)}- createTable:
        |${indent(4)}tableName: ${psiClass.name?.lowercase()}
        |${indent(4)}columns:
        |$columns
        """.trimMargin().replaceIndent()
    }

    private fun generateJsonContent(psiClass: PsiClass): String {
        val tableName = psiClass.name?.lowercase()
        val columns = psiClass.fields.joinToString(",\n") { field ->
            """
            |${indent(5)}{
            |${indent(6)}"column": {
            |${indent(7)}"name": "${field.name}",
            |${indent(7)}"type": "${mapJavaTypeToDbType(field.type.canonicalText)}",
            |${indent(7)}"constraints": { "nullable": ${isNullable(field)} }
            |${indent(6)}}
            |${indent(5)}}
            """.trimMargin()
        }
        return """
        |{
        |${indent(1)}"databaseChangeLog": [
        |${indent(2)}{
        |${indent(3)}"changeSet": {
        |${indent(4)}"id": "${psiClass.name}-01",
        |${indent(4)}"author": "autoapigen",
        |${indent(4)}"changes": [
        |${indent(5)}{
        |${indent(6)}"createTable": {
        |${indent(7)}"tableName": "$tableName",
        |${indent(7)}"columns": [
        |$columns
        |${indent(7)}]
        |${indent(6)}}
        |${indent(5)}}
        |${indent(4)}]
        |${indent(3)}}
        |${indent(2)}}
        |${indent(1)}]
        |}
        """.trimMargin()
    }

    private fun generateSqlContent(psiClass: PsiClass): String {
        val tableName = psiClass.name?.lowercase()
        val columns = psiClass.fields.joinToString(",\n") { field ->
            val notNull = if (isNullable(field)) "" else " NOT NULL"
            "${indent(1)}${field.name} ${mapJavaTypeToDbType(field.type.canonicalText)}$notNull"
        }
        return """
        |--liquibase formatted sql
        |
        |--changeset autoapigen:${psiClass.name}-01
        |CREATE TABLE $tableName (
        |$columns
        |);
        |--rollback DROP TABLE $tableName;
        """.trimMargin()
    }

    private fun generateXmlContent(psiClass: PsiClass): String {
        val columns = generateColumnsXml(psiClass)
        return """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.9.xsd">
        |${indent(1)}<changeSet id="${psiClass.name}-01" author="autoapigen">
        |${indent(2)}<createTable tableName="${psiClass.name?.lowercase()}">
        |$columns
        |${indent(2)}</createTable>
        |${indent(1)}</changeSet>
        |</databaseChangeLog>
        """.trimMargin().replaceIndent()
    }


    // Helper functions for generating columns with proper formatting
    private fun generateColumnsYaml(psiClass: PsiClass): String {
        return psiClass.fields.joinToString("\n") { field ->
            generateColumnYaml(field)
        }.prependIndent(indent(5))
    }

    private fun generateColumnsXml(psiClass: PsiClass): String {
        return psiClass.fields.joinToString("\n") { field ->
            generateColumnXml(field)
        }.prependIndent(indent(3))
    }

    // Column generators with proper indentation
    private fun generateColumnYaml(field: PsiField): String {
        return """
            |- column:
            |${indent(1)}name: ${field.name}
            |${indent(1)}type: ${mapJavaTypeToDbType(field.type.canonicalText)}
            |${indent(1)}constraints:
            |${indent(2)}nullable: ${isNullable(field)}
        """.trimMargin()
    }

    private fun generateColumnXml(field: PsiField): String {
        return """
            |<column name="${field.name}" 
            |${indent(1)}type="${mapJavaTypeToDbType(field.type.canonicalText)}">
            |${indent(1)}<constraints nullable="${isNullable(field)}"/>
            |</column>
        """.trimMargin().replace("\n", " ")
    }


    // Indentation helper
    private fun indent(level: Int): String = " ".repeat(INDENT_SIZE * level)

    // Existing type mapping and nullable check functions remain same
    private fun mapJavaTypeToDbType(javaType: String): String {
        return when (javaType) {
            "java.lang.String" -> "VARCHAR(255)"
            "int", "java.lang.Integer" -> "INT"
            "long", "java.lang.Long" -> "BIGINT"
            "short", "java.lang.Short" -> "SMALLINT"
            "boolean", "java.lang.Boolean" -> "BOOLEAN"
            "float", "java.lang.Float" -> "FLOAT"
            "double", "java.lang.Double" -> "DOUBLE"
            "java.math.BigDecimal" -> "DECIMAL(19,2)"
            "java.time.LocalDate" -> "DATE"
            "java.time.LocalDateTime", "java.time.Instant", "java.time.OffsetDateTime" -> "TIMESTAMP"
            else -> "VARCHAR(255)"
        }
    }

    private fun isNullable(field: PsiField): Boolean {
        return field.type.canonicalText !in setOf("int", "long", "boolean", "float", "double", "short", "byte", "char")
                && !field.hasModifierProperty(PsiModifier.FINAL)
    }
}