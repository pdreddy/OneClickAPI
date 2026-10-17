package damu.plugin.oneclickapi.core.plan

import damu.plugin.oneclickapi.core.model.EntityModel
import damu.plugin.oneclickapi.core.model.FieldModel
import damu.plugin.oneclickapi.core.model.OrmType
import damu.plugin.oneclickapi.core.model.RelationKind
import damu.plugin.oneclickapi.core.model.boxed
import damu.plugin.oneclickapi.core.model.capFirst
import damu.plugin.oneclickapi.core.options.ApiVersioning
import damu.plugin.oneclickapi.core.options.GenerationOptions
import damu.plugin.oneclickapi.core.project.ProjectProfile

internal enum class DtoKind { PLAIN, REL_ID, REL_IDS }

/** One property of a Request/Response DTO. */
internal data class DtoField(
    val name: String,
    val type: String,
    val imports: Set<String>,
    val annotations: List<String>,
    val source: FieldModel,
    val kind: DtoKind,
    val sample: Sample?,
    val required: Boolean
) {
    val getter: String get() = (if (type == "boolean") "is" else "get") + name.capFirst()
    val setter: String get() = "set" + name.capFirst()
}

/** A property of the generated `XFilter` and the Specification / SQL built from it. */
internal data class FilterField(
    val name: String,
    val type: String,
    val imports: Set<String>,
    /** STRING = case-insensitive contains, EXACT = equals, REL = equals on `relation.id`. */
    val mode: String,
    val column: String,
    val relationProperty: String? = null,
    val relationIdName: String? = null
) {
    val getter: String get() = "get" + name.capFirst()
    val setter: String get() = "set" + name.capFirst()
}

/** A ManyToOne / OneToOne field resolved from an id in the request. */
internal data class RelationRef(val source: FieldModel, val dto: DtoField) {
    val targetSimple: String get() = source.relationTarget!!.simpleName
    val targetFqn: String get() = source.relationTarget!!.fqn
}

/**
 * Everything the templates need to know about one entity, derived once from the entity, the options and the
 * project. Keeping the derivation here means the templates stay almost free of logic.
 */
internal class EntityContext(
    val entity: EntityModel,
    val o: GenerationOptions,
    val profile: ProjectProfile
) {
    val std = o.standards
    val name = entity.name
    val isJpa = entity.orm == OrmType.JPA
    val jakarta: Boolean = entity.jakartaPersistence ?: profile.jakarta
    val persistencePkg = if (jakarta) "jakarta.persistence" else "javax.persistence"
    val validationPkg = if (jakarta) "jakarta.validation" else "javax.validation"

    val warnings = mutableListOf<String>()

    // ----------------------------------------------------------------------------- managed fields

    private fun existing(n: String) = entity.fields.firstOrNull { it.name == n }

    private val softField: FieldModel? = run {
        if (!std.softDelete) return@run null
        val f = existing("deleted")
        when {
            f != null && f.type in setOf("boolean", "Boolean") -> f
            f != null -> {
                warnings += "${entity.name}: soft delete skipped - field 'deleted' exists but is ${f.type}, not boolean."
                null
            }

            entity.sourcePath == null -> {
                warnings += "${entity.name}: soft delete skipped - the entity source file is unknown, so the 'deleted' field cannot be added."
                null
            }

            else -> FieldModel("deleted", "boolean", nullable = false, synthetic = true)
        }
    }

    private val createdField: FieldModel? = auditField("createdAt")
    private val updatedField: FieldModel? = auditField("updatedAt")

    private fun auditField(n: String): FieldModel? {
        if (!std.auditFields) return null
        existing(n)?.let { return it }
        if (entity.sourcePath == null) {
            warnings += "${entity.name}: audit field '$n' skipped - the entity source file is unknown."
            return null
        }
        return FieldModel(n, "LocalDateTime", imports = setOf("java.time.LocalDateTime"), synthetic = true)
    }

    val softDelete: Boolean get() = softField != null
    val audit: Boolean get() = createdField != null || updatedField != null

    /** Fields that must be added to the entity source. */
    val patchFields: List<FieldModel> = listOfNotNull(softField, createdField, updatedField).filter { it.synthetic }

    /** The entity's fields as they will be once patched. */
    val fields: List<FieldModel> = entity.fields + patchFields

    private val managedNames: Set<String> =
        listOfNotNull(softField?.name, createdField?.name, updatedField?.name).toSet()

    private fun isManaged(f: FieldModel) = f.name in managedNames
    private fun isAudit(f: FieldModel) = f.name == createdField?.name || f.name == updatedField?.name

    val deletedPrimitive: Boolean get() = softField?.type == "boolean"
    val deletedGetter: String get() = if (deletedPrimitive) "isDeleted" else "getDeleted"

    // ----------------------------------------------------------------------------- relations

    private val relationsAsIds = std.relationDtos

    private fun isRelation(f: FieldModel) = relationsAsIds && f.relation != RelationKind.NONE && f.relationTarget != null

    // ----------------------------------------------------------------------------- DTO fields

    private fun validationFor(f: FieldModel, type: String, nullable: Boolean, plain: Boolean): Pair<List<String>, Set<String>> {
        val annotations = mutableListOf<String>()
        val imports = mutableSetOf<String>()
        if (plain) {
            annotations += f.validationAnnotations
            imports += f.validationImports
        }
        if (std.validation) {
            fun has(vararg n: String) = annotations.any { a -> n.any { a.startsWith("@$it") } }
            fun add(simple: String, text: String = "@$simple") {
                annotations += text
                imports += "$validationPkg.constraints.$simple"
            }
            if (!nullable && type !in FieldModel.PRIMITIVES && !has("NotNull", "NotBlank", "NotEmpty")) {
                if (type == "String") add("NotBlank") else add("NotNull")
            }
            if (plain && type == "String") {
                f.length?.let { if (!has("Size")) add("Size", "@Size(max = $it)") }
                if (f.name.contains("email", ignoreCase = true) && !has("Email")) add("Email")
            }
        }
        return annotations to imports
    }

    private fun plainDto(f: FieldModel, request: Boolean): DtoField {
        val (ann, imp) = if (request) validationFor(f, f.type, f.nullable, true) else (emptyList<String>() to emptySet())
        return DtoField(
            f.name, f.type, f.imports + imp, ann, f, DtoKind.PLAIN,
            Samples.constrain(
                Samples.of(f.type, f.name, f.length, f.enumFqn, f.enumFirstConstant), f.type,
                f.validationAnnotations
            ),
            required = !f.nullable && !f.primitive
        )
    }

    private fun relationDto(f: FieldModel, request: Boolean): DtoField? {
        val t = f.relationTarget ?: return null
        val idType = boxed(t.idType)
        return when (f.relation) {
            RelationKind.MANY_TO_ONE, RelationKind.ONE_TO_ONE -> {
                if (request && f.inverse) return null
                val n = f.name + "Id"
                val (ann, imp) = if (request) validationFor(f, idType, f.nullable, false) else (emptyList<String>() to emptySet())
                DtoField(
                    n, idType, imp, ann, f, DtoKind.REL_ID, Samples.of(idType),
                    required = request && !f.nullable
                )
            }

            RelationKind.ONE_TO_MANY, RelationKind.MANY_TO_MANY ->
                if (request) null
                else DtoField(
                    f.name.removeSuffix("s") + "Ids", "List<$idType>", setOf("java.util.List"), emptyList(),
                    f, DtoKind.REL_IDS, null, false
                )

            RelationKind.NONE -> null
        }
    }

    val requestFields: List<DtoField> = fields.mapNotNull { f ->
        when {
            f.isId || isManaged(f) -> null
            isRelation(f) -> relationDto(f, request = true)
            else -> plainDto(f, request = true)
        }
    }

    val responseFields: List<DtoField> = fields.mapNotNull { f ->
        when {
            f.name == softField?.name -> null
            isRelation(f) -> relationDto(f, request = false)
            else -> plainDto(f, request = false)
        }
    }

    val relations: List<RelationRef> = requestFields.filter { it.kind == DtoKind.REL_ID }.map { RelationRef(it.source, it) }

    /** Relation fields that MapStruct / the manual mapper must not touch when mapping a request. */
    val ignoredOnRequest: List<FieldModel> = fields.filter { isRelation(it) }

    // ----------------------------------------------------------------------------- list endpoint

    private fun columnOf(f: FieldModel): String = f.columnName ?: if (isJpa) snake(f.name) else f.name

    val sortable: List<Pair<String, String>> = (
            listOfNotNull(fields.firstOrNull { it.isId } ?: fields.firstOrNull { it.name.equals("id", true) }) +
                    fields.filter { f ->
                        !f.isId && !isRelation(f) && f.relation == RelationKind.NONE && !f.isCollection &&
                                f.name != softField?.name &&
                                (f.rawFilterType in Samples.FILTERABLE || f.isEnum)
                    }
            ).map { it.name to columnOf(it) }

    val filterFields: List<FilterField> = fields.mapNotNull { f ->
        when {
            f.isId || f.name == softField?.name || isAudit(f) -> null
            isRelation(f) && f.relation in setOf(RelationKind.MANY_TO_ONE, RelationKind.ONE_TO_ONE) && !f.inverse -> {
                val t = f.relationTarget!!
                FilterField(f.name + "Id", boxed(t.idType), emptySet(), "REL", columnOf(f) + "_id", f.name, t.idName)
            }

            f.relation != RelationKind.NONE || f.isCollection -> null
            f.rawFilterType == "String" -> FilterField(f.name, "String", emptySet(), "STRING", columnOf(f))
            f.rawFilterType in Samples.FILTERABLE || f.isEnum ->
                FilterField(f.name, boxed(f.type), f.imports, "EXACT", columnOf(f))

            else -> null
        }
    }

    val searchable: List<FilterField> = filterFields.filter { it.mode == "STRING" }

    // ----------------------------------------------------------------------------- misc

    val table: String
        get() = entity.tableName ?: if (isJpa) snake(name) else name.lowercase()

    /** Path segment in the URL, `/api/v1/<segment>`. */
    val pathSegment: String get() = name.lowercase()

    val basePath: String
        get() = when (std.versioning) {
            ApiVersioning.PATH -> "/api/v${std.apiVersion}/$pathSegment"
            else -> "/api/$pathSegment"
        }

    val versionHeader: String? get() = if (std.versioning == ApiVersioning.HEADER) "X-API-Version" else null

    fun pkg(sub: String): String = listOf(o.basePackage, sub).filter { it.isNotEmpty() }.joinToString(".")

    val dtoRootPkg get() = pkg(o.layout.dto)
    val requestPkg get() = pkg(o.layout.dto) + ".${name.lowercase()}.request"
    val responsePkg get() = pkg(o.layout.dto) + ".${name.lowercase()}.response"

    /** Name of the entity <-> DTO converter; MyBatis keeps `XMapper` for the persistence interface. */
    val dtoMapperName: String get() = if (isJpa) "${name}Mapper" else "${name}DtoMapper"

    fun usesIdGeneratedKey(): Boolean = entity.idType in setOf("Long", "Integer")

    companion object {
        fun snake(s: String): String =
            s.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2").lowercase()
    }
}

/** `List<String>` -> `List`, `int` -> `int`. */
internal val FieldModel.rawFilterType: String get() = type.substringBefore('<').trim()
