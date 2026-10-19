package damu.plugin.oneclickapi.eclipse

import damu.plugin.oneclickapi.core.model.EntityModel
import damu.plugin.oneclickapi.core.model.FieldModel
import damu.plugin.oneclickapi.core.model.OrmType
import damu.plugin.oneclickapi.core.model.RelationKind
import damu.plugin.oneclickapi.core.model.RelationTarget

/** Builds the planner's [EntityModel] from what JDT reported. Same rules as the IntelliJ plugin's PSI reader. */
object ModelFactory {

    private val PERSISTENCE = setOf("jakarta.persistence", "javax.persistence")
    private val VALIDATION = listOf("jakarta.validation.constraints.", "javax.validation.constraints.")
    private val NOT_NULL = setOf("NotNull", "NotBlank", "NotEmpty", "NonNull")

    @JvmStatic
    fun isEntity(c: RawClass): Boolean = c.annotations.any { it.qualifiedName in ENTITY }

    @JvmStatic
    fun build(c: RawClass): EntityModel {
        val entity = c.annotations.firstOrNull { it.qualifiedName in ENTITY }
        val orm = if (entity != null) OrmType.JPA else OrmType.MYBATIS
        val fields = c.fields
            .filter { !it.isStatic && !it.isTransient }
            .filter { orm != OrmType.JPA || it.persistence("Transient") == null }
            .map(::field)
        val lombok = c.annotations.map { it.qualifiedName }.toSet()
        return EntityModel(
            name = c.name,
            packageName = c.packageName,
            orm = orm,
            fields = fields,
            sourcePath = c.sourcePath,
            tableName = c.persistence("Table")?.attributes?.get("name") as? String,
            lombokAccessors = "lombok.Data" in lombok || ("lombok.Getter" in lombok && "lombok.Setter" in lombok),
            jakartaPersistence = entity?.qualifiedName?.startsWith("jakarta.")
        )
    }

    private fun field(f: RawField): FieldModel {
        val relation = when {
            f.persistence("ManyToOne") != null -> RelationKind.MANY_TO_ONE
            f.persistence("OneToOne") != null -> RelationKind.ONE_TO_ONE
            f.persistence("OneToMany") != null -> RelationKind.ONE_TO_MANY
            f.persistence("ManyToMany") != null -> RelationKind.MANY_TO_MANY
            else -> RelationKind.NONE
        }
        val relationAnn = f.persistence("ManyToOne", "OneToOne", "OneToMany", "ManyToMany")
        val target = if (relation != RelationKind.NONE && f.relationTargetName != null && f.relationTargetFqn != null) {
            RelationTarget(f.relationTargetName!!, f.relationTargetFqn!!, f.relationTargetIdName, f.relationTargetIdType)
        } else null

        val column = f.persistence("Column")
        val joinColumn = f.persistence("JoinColumn")
        val notNullAnnotation = f.annotations.any { it.qualifiedName.substringAfterLast('.') in NOT_NULL }
        val nullable = !(
            f.primitive || notNullAnnotation ||
                f.persistence("Id", "EmbeddedId") != null ||
                column.bool("nullable") == false ||
                joinColumn.bool("nullable") == false ||
                f.persistence("Basic").bool("optional") == false ||
                relationAnn.bool("optional") == false
            )

        val validation = f.annotations.filter { a -> VALIDATION.any { a.qualifiedName.startsWith(it) } }
        return FieldModel(
            name = f.name,
            type = f.type,
            imports = f.typeImports.toSortedSet(),
            isId = f.persistence("Id", "EmbeddedId") != null,
            generated = f.persistence("GeneratedValue") != null,
            nullable = nullable,
            length = column?.attributes?.get("length") as? Int,
            columnName = (column?.attributes?.get("name") as? String) ?: (joinColumn?.attributes?.get("name") as? String),
            validationAnnotations = validation.map { it.source },
            validationImports = validation.map { it.qualifiedName }.toSet(),
            relation = relation,
            relationTarget = target,
            inverse = relationAnn?.attributes?.containsKey("mappedBy") == true,
            isEnum = f.enumFqn != null,
            enumFqn = f.enumFqn,
            enumFirstConstant = f.enumConstants.firstOrNull(),
            enumConstants = f.enumConstants.toList()
        )
    }

    private val ENTITY = setOf("jakarta.persistence.Entity", "javax.persistence.Entity")

    private fun List<RawAnnotation>.persistence(vararg names: String): RawAnnotation? = firstOrNull { a ->
        a.qualifiedName.substringBeforeLast('.') in PERSISTENCE && a.qualifiedName.substringAfterLast('.') in names
    }

    private fun RawField.persistence(vararg names: String) = annotations.persistence(*names)
    private fun RawClass.persistence(vararg names: String) = annotations.persistence(*names)
    private fun RawAnnotation?.bool(name: String): Boolean? = this?.attributes?.get(name) as? Boolean
}
