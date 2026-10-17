package damu.plugin.oneclickapi.core.model

enum class OrmType { JPA, MYBATIS }

enum class RelationKind { NONE, MANY_TO_ONE, ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY }

/** The entity a relation field points at. [idName]/[idType] describe that entity's identifier. */
data class RelationTarget(
    val simpleName: String,
    val fqn: String,
    val idName: String = "id",
    val idType: String = "Long"
)

/**
 * One instance field of an entity/POJO, already reduced to plain data so that nothing below the IDE layer
 * needs PSI.
 */
data class FieldModel(
    val name: String,
    /** Type as it is written in source, with simple names: `String`, `int`, `List<Book>`. */
    val type: String,
    /** Imports needed to write [type] (java.lang excluded). */
    val imports: Set<String> = emptySet(),
    val isId: Boolean = false,
    val nullable: Boolean = true,
    /** Explicit `@Column(length = n)`. */
    val length: Int? = null,
    /** Explicit `@Column(name = ...)`. */
    val columnName: String? = null,
    /** Existing Bean Validation annotations as source text, e.g. `@Size(max = 5)`. */
    val validationAnnotations: List<String> = emptyList(),
    val validationImports: Set<String> = emptySet(),
    val relation: RelationKind = RelationKind.NONE,
    val relationTarget: RelationTarget? = null,
    /** `mappedBy` present: this side does not own the relation. */
    val inverse: Boolean = false,
    val isEnum: Boolean = false,
    val enumFqn: String? = null,
    val enumFirstConstant: String? = null,
    val enumConstants: List<String> = emptyList(),
    /** Added by the generator (audit / soft delete), not yet present in the entity source. */
    val synthetic: Boolean = false
) {
    val primitive: Boolean get() = type in PRIMITIVES
    val boxedType: String get() = boxed(type)

    /** Type with generics and array brackets removed: `List<Book>` -> `List`. */
    val rawType: String get() = type.substringBefore('<').trim()

    val isCollection: Boolean
        get() = rawType in setOf("List", "Set", "Collection", "Iterable")

    val getter: String get() = (if (type == "boolean") "is" else "get") + name.capFirst()
    val setter: String get() = "set" + name.capFirst()

    companion object {
        val PRIMITIVES = setOf("int", "long", "boolean", "double", "float", "short", "byte", "char")
    }
}

/** An `@Entity` (JPA) or plain POJO (MyBatis) to generate an API for. */
data class EntityModel(
    val name: String,
    val packageName: String,
    val orm: OrmType,
    /** All instance fields, inherited ones included. */
    val fields: List<FieldModel>,
    /** Path of the source file relative to the module root; null if unknown (entity cannot be patched). */
    val sourcePath: String? = null,
    /** `@Table(name = ...)` if present. */
    val tableName: String? = null,
    /** The class carries Lombok accessors (`@Data`, `@Getter`+`@Setter`), so generated fields need none. */
    val lombokAccessors: Boolean = false,
    /** `true` = jakarta.persistence, `false` = javax.persistence, `null` = not a JPA entity. */
    val jakartaPersistence: Boolean? = null
) {
    val fqn: String get() = if (packageName.isEmpty()) name else "$packageName.$name"

    val idField: FieldModel?
        get() = fields.firstOrNull { it.isId } ?: fields.firstOrNull { it.name.equals("id", ignoreCase = true) }

    val idName: String get() = idField?.name ?: "id"
    val idType: String get() = idField?.boxedType ?: "Long"
    val idGetter: String get() = "get" + idName.capFirst()
    val idSetter: String get() = "set" + idName.capFirst()
}

fun String.capFirst(): String = replaceFirstChar { it.uppercase() }
fun String.decapFirst(): String = replaceFirstChar { it.lowercase() }

fun boxed(type: String): String = when (type) {
    "int" -> "Integer"
    "long" -> "Long"
    "boolean" -> "Boolean"
    "double" -> "Double"
    "float" -> "Float"
    "short" -> "Short"
    "byte" -> "Byte"
    "char" -> "Character"
    else -> type
}
