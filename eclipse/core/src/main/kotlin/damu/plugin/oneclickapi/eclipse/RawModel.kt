package damu.plugin.oneclickapi.eclipse

/**
 * What the Eclipse (JDT) layer reads from a class, as plain mutable beans so that Java can fill them without
 * Kotlin default arguments. [ModelFactory] turns them into the model the platform-independent planner uses.
 */
class RawAnnotation {
    /** Fully qualified name, e.g. `jakarta.persistence.Column`. */
    var qualifiedName: String = ""

    /** The annotation as written in source, e.g. `@Size(max = 5)`. */
    var source: String = ""

    /** Literal attribute values (String, Integer, Boolean). An attribute that is not a literal is present with a null value. */
    var attributes: MutableMap<String, Any?> = linkedMapOf()
}

class RawField {
    var name: String = ""

    /** Type as written in source with simple names: `String`, `int`, `List<Book>`. */
    var type: String = ""
    var primitive: Boolean = false

    /** Fully qualified names needed to write [type] (java.lang excluded). */
    var typeImports: MutableList<String> = mutableListOf()
    var isStatic: Boolean = false
    var isTransient: Boolean = false
    var annotations: MutableList<RawAnnotation> = mutableListOf()

    /** For a relation: the entity on the other side. */
    var relationTargetName: String? = null
    var relationTargetFqn: String? = null
    var relationTargetIdName: String = "id"
    var relationTargetIdType: String = "Long"

    var enumFqn: String? = null
    var enumConstants: MutableList<String> = mutableListOf()
}

class RawClass {
    var name: String = ""
    var packageName: String = ""
    var annotations: MutableList<RawAnnotation> = mutableListOf()

    /** Own and inherited fields. */
    var fields: MutableList<RawField> = mutableListOf()

    /** Source file path relative to the project root, `/` separated. */
    var sourcePath: String? = null
}
