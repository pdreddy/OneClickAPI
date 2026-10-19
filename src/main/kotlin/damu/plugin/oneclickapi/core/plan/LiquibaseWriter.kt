package damu.plugin.oneclickapi.core.plan

import damu.plugin.oneclickapi.core.model.FieldModel
import damu.plugin.oneclickapi.core.model.RelationKind
import damu.plugin.oneclickapi.core.options.LiquibaseFormat

/** One column of the `createTable` change. */
internal data class Column(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val autoIncrement: Boolean,
    /** Only set for the soft-delete flag, so that existing rows stay visible after the column is added. */
    val defaultFalse: Boolean = false
)

internal object LiquibaseWriter {

    fun write(c: EntityContext, format: LiquibaseFormat): String {
        val columns = columns(c)
        return when (format) {
            LiquibaseFormat.XML -> xml(c, columns)
            LiquibaseFormat.YAML -> yaml(c, columns)
        }
    }

    private fun columns(c: EntityContext): List<Column> {
        val idName = c.entity.idField?.name
        val generated = c.usesIdGeneratedKey()
        return c.fields.mapNotNull { f ->
            val isId = f.name == idName
            when {
                f.isCollection -> null
                f.relation == RelationKind.MANY_TO_ONE || f.relation == RelationKind.ONE_TO_ONE -> {
                    if (f.inverse || f.relationTarget == null) null
                    else Column(
                        (f.columnName ?: EntityContext.snake(f.name)) .let { if (f.columnName != null) it else it + "_id" },
                        dbType(f.relationTarget.idType, null),
                        f.nullable, false, false
                    )
                }

                f.relation != RelationKind.NONE -> null
                else -> Column(
                    f.columnName ?: if (c.isJpa) EntityContext.snake(f.name) else f.name,
                    dbType(f.type, f.length, f),
                    nullable = !isId && f.nullable && !f.primitive,
                    primaryKey = isId,
                    autoIncrement = isId && generated,
                    defaultFalse = f.name == "deleted" && f.type in setOf("boolean", "Boolean") && f.synthetic
                )
            }
        }
    }

    private fun dbType(type: String, length: Int?, f: FieldModel? = null): String = when (type) {
        "String" -> "VARCHAR(${length ?: 255})"
        "int", "Integer" -> "INT"
        "long", "Long" -> "BIGINT"
        "short", "Short" -> "SMALLINT"
        "byte", "Byte" -> "TINYINT"
        "boolean", "Boolean" -> "BOOLEAN"
        "double", "Double" -> "DOUBLE"
        "float", "Float" -> "FLOAT"
        "BigDecimal" -> "DECIMAL(19,2)"
        "BigInteger" -> "NUMERIC(38)"
        "LocalDate" -> "DATE"
        "LocalTime" -> "TIME"
        "LocalDateTime", "Instant", "OffsetDateTime", "ZonedDateTime" -> "TIMESTAMP"
        "UUID" -> "UUID"
        else -> if (f?.isEnum == true) "VARCHAR(50)" else "VARCHAR(255)"
    }

    private fun yaml(c: EntityContext, cols: List<Column>): String = buildString {
        appendLine("databaseChangeLog:")
        appendLine("  - changeSet:")
        appendLine("      id: ${c.name}-01")
        appendLine("      author: oneclickapi")
        appendLine("      changes:")
        appendLine("        - createTable:")
        appendLine("            tableName: ${c.table}")
        appendLine("            columns:")
        for (col in cols) {
            appendLine("              - column:")
            appendLine("                  name: ${col.name}")
            appendLine("                  type: ${col.type}")
            if (col.autoIncrement) appendLine("                  autoIncrement: true")
            if (col.defaultFalse) appendLine("                  defaultValueBoolean: false")
            appendLine("                  constraints:")
            if (col.primaryKey) appendLine("                    primaryKey: true")
            appendLine("                    nullable: ${col.nullable}")
        }
    }

    private fun xml(c: EntityContext, cols: List<Column>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.9.xsd">"""
        )
        appendLine("""    <changeSet id="${c.name}-01" author="oneclickapi">""")
        appendLine("""        <createTable tableName="${c.table}">""")
        for (col in cols) {
            val auto = (if (col.autoIncrement) """ autoIncrement="true"""" else "") +
                    (if (col.defaultFalse) """ defaultValueBoolean="false"""" else "")
            appendLine("""            <column name="${col.name}" type="${col.type}"$auto>""")
            val pk = if (col.primaryKey) """ primaryKey="true"""" else ""
            appendLine("""                <constraints$pk nullable="${col.nullable}"/>""")
            appendLine("""            </column>""")
        }
        appendLine("""        </createTable>""")
        appendLine("""    </changeSet>""")
        appendLine("""</databaseChangeLog>""")
    }
}
