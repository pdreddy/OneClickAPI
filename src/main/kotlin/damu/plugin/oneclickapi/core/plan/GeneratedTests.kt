package damu.plugin.oneclickapi.core.plan

import damu.plugin.oneclickapi.core.model.EntityModel
import damu.plugin.oneclickapi.core.model.RelationKind
import damu.plugin.oneclickapi.core.model.capFirst
import damu.plugin.oneclickapi.core.model.decapFirst
import damu.plugin.oneclickapi.core.options.ConverterType
import damu.plugin.oneclickapi.core.project.ProjectProfile

/**
 * Template models for the mapper test and the full-stack API test. The interesting part is the data: which fields
 * get sample values, which related rows must exist before a request is valid, and when a test cannot be generated.
 */
internal class GeneratedTests(
    private val profile: ProjectProfile,
    /** Every entity being generated, by fully qualified name: the related rows of an API test are built from these. */
    private val known: Map<String, EntityModel>
) {

    // ------------------------------------------------------------------------------------------ mapper test

    fun mapperModel(c: EntityContext): Map<String, Any?> {
        val imports = sortedSetOf<String>()
        val relIds = c.responseFields.filter { it.kind == DtoKind.REL_ID }.mapNotNull { df ->
            val t = df.source.relationTarget!!
            val id = Samples.of(t.idType) ?: return@mapNotNull null
            imports += t.fqn
            mapOf(
                "target" to t.simpleName, "var" to df.source.name, "idSetter" to "set" + t.idName.capFirst(),
                "id" to id.java, "entitySetter" to df.source.setter, "responseGetter" to df.getter
            )
        }
        val relIdLists = c.responseFields.filter { it.kind == DtoKind.REL_IDS }.mapNotNull { df ->
            val t = df.source.relationTarget!!
            val id = Samples.of(t.idType) ?: return@mapNotNull null
            val factory = when (df.source.rawType) {
                "Set" -> "java.util.Set.of"
                "List", "Collection", "Iterable" -> "java.util.List.of"
                else -> return@mapNotNull null
            }
            imports += t.fqn
            mapOf(
                "target" to t.simpleName, "var" to df.source.name.removeSuffix("s") + "Item", "idSetter" to "set" + t.idName.capFirst(),
                "id" to id.java, "factory" to factory, "entitySetter" to df.source.setter, "responseGetter" to df.getter
            )
        }
        return mapOf(
            "mapperInit" to (if (c.o.converter == ConverterType.MAPSTRUCT) "org.mapstruct.factory.Mappers.getMapper(${c.dtoMapperName}.class)" else "new ${c.dtoMapperName}()"),
            "idSetter" to c.entity.idSetter,
            "idSample" to (Samples.of(c.entity.idType)?.java ?: "null"),
            "requestValues" to c.requestFields.filter { it.kind == DtoKind.PLAIN && it.sample != null }.map {
                mapOf("setter" to it.setter, "value" to it.sample!!.java, "entityGetter" to it.source.getter)
            },
            "responseValues" to c.responseFields.filter { it.kind == DtoKind.PLAIN && it.sample != null && !it.source.isId }.map {
                mapOf("entitySetter" to it.source.setter, "value" to it.sample!!.java, "getter" to it.getter)
            },
            "relIds" to relIds,
            "relIdLists" to relIdLists,
            "relationImports" to imports.filter { it != c.entity.fqn }
        )
    }

    // ------------------------------------------------------------------------------------------ full-stack API test

    /** Result of planning an API test: the template model, or why it cannot be generated. */
    sealed interface Api {
        data class Ok(val model: Map<String, Any?>) : Api
        data class Skipped(val reason: String) : Api
    }

    fun apiModel(c: EntityContext): Api {
        val e = c.entity
        if (e.idField?.generated != true) {
            return Api.Skipped("the id of ${e.name} is not @GeneratedValue, so a POST cannot create one without inventing an id")
        }
        missingId(e.idType) ?: return Api.Skipped("ids of type ${e.idType} are not supported by the API test")

        val setup = mutableListOf<String>()
        val imports = sortedSetOf<String>()
        val parts = mutableListOf<String>() // pieces of the JSON body: "literal text" or a Java expression marked with \u0000
        for (f in c.requestFields) {
            when (f.kind) {
                DtoKind.PLAIN -> {
                    val s = f.sample
                    if (s == null) {
                        if (f.required) return Api.Skipped("no sample value can be made for the required field ${e.name}.${f.name}")
                        continue
                    }
                    parts += "\"${f.name}\":${s.json}"
                }

                DtoKind.REL_ID -> {
                    val target = f.source.relationTarget!!
                    val v = persist(target.fqn, setup, imports, mutableSetOf(e.fqn), 0)
                    if (v == null) {
                        if (f.required) return Api.Skipped("${e.name}.${f.source.name} is required and ${target.simpleName} is not generated in the same run, so the test cannot create one")
                        continue
                    }
                    val quote = Samples.of(target.idType)?.openApiType == "string"
                    parts += "\"${f.name}\":" + (if (quote) "\"" else "") + "\u0000$v.get${target.idName.capFirst()}()\u0000" + (if (quote) "\"" else "")
                }

                DtoKind.REL_IDS -> Unit
            }
        }

        val body = toJavaConcat("{" + parts.joinToString(",") + "}")
        val checks = c.requestFields.filter { it.kind == DtoKind.PLAIN && it.sample != null && checkable(it) }
            .map { mapOf("name" to it.name, "value" to it.sample!!.let { s -> if (s.openApiType == "string") "\"${Samples.javaString(s.text)}\"" else s.text }) }

        val itemPath = if (c.std.standardResponse) "$.data." else "$."
        val listPath = when {
            c.std.pagination -> itemPath + "content[0]."
            c.std.standardResponse -> "$.data[0]."
            else -> "$[0]."
        }
        return Api.Ok(
            mapOf(
                "applicationClass" to profile.applicationClass,
                "applicationSimple" to profile.applicationClass?.substringAfterLast('.'),
                "boot4" to (profile.bootMajor >= 4),
                "setup" to setup,
                "setupImports" to imports.filter { it != e.fqn },
                "persists" to setup.isNotEmpty(),
                "body" to body,
                "checks" to checks,
                "missingId" to missingId(e.idType),
                "itemPath" to itemPath,
                "listPath" to listPath,
                // Only Bean Validation turns an empty body into a 400; a NOT NULL column alone fails later, in the database.
                "hasRequiredFields" to c.requestFields.any { it.annotations.any { a -> a.startsWith("@NotNull") || a.startsWith("@NotBlank") || a.startsWith("@NotEmpty") } }
            )
        )
    }

    /**
     * Writes the statements that create and persist one row of [fqn], related rows it cannot exist without first.
     * Returns the variable that holds it, or null when that is not possible (unknown entity, cycle, no sample value).
     */
    private fun persist(fqn: String, setup: MutableList<String>, imports: MutableSet<String>, visiting: MutableSet<String>, depth: Int): String? {
        val t = known[fqn] ?: return null
        if (fqn in visiting || depth > 3) return null
        visiting += fqn
        val lines = mutableListOf<String>()
        val v = t.name.decapFirst() + (depth + 1)
        lines += "${t.name} $v = new ${t.name}();"
        val id = t.idField
        if (id != null && !id.generated) lines += "$v.${t.idSetter}(${Samples.of(id.type, id.name)?.java ?: return null});"
        for (f in t.fields) {
            when {
                f.isId -> Unit
                f.relation == RelationKind.NONE && !f.isCollection -> {
                    val s = Samples.constrain(Samples.of(f.type, f.name, f.length, f.enumFqn, f.enumFirstConstant), f.type, f.validationAnnotations)
                    when {
                        s != null -> lines += "$v.${f.setter}(${s.java});"
                        !f.nullable && !f.primitive -> return null
                    }
                }

                (f.relation == RelationKind.MANY_TO_ONE || f.relation == RelationKind.ONE_TO_ONE) && !f.inverse && !f.nullable -> {
                    val r = persist(f.relationTarget?.fqn ?: return null, setup, imports, visiting, depth + 1) ?: return null
                    lines += "$v.${f.setter}($r);"
                }
            }
        }
        lines += "entityManager.persist($v);"
        setup += lines
        imports += t.fqn
        visiting -= fqn
        return v
    }

    /** Only values that come back unchanged as JSON are compared; decimals and zoned times may change their scale or offset. */
    private fun checkable(f: DtoField): Boolean = f.source.isEnum || f.type in setOf(
        "String", "Integer", "int", "Long", "long", "Short", "short", "Boolean", "boolean", "LocalDate"
    )

    private fun missingId(type: String): String? = when (type) {
        "Long" -> "999999L"
        "Integer" -> "999999"
        "Short" -> "(short) 9999"
        "String" -> "\"missing\""
        "UUID" -> "java.util.UUID.fromString(\"00000000-0000-0000-0000-000000000000\")"
        else -> null
    }

    /** `{"a":1,"b":\u0000x.getId()\u0000}` -> `"{\"a\":1,\"b\":" + x.getId() + "}"` */
    private fun toJavaConcat(template: String): String =
        template.split('\u0000').mapIndexed { i, piece ->
            if (i % 2 == 1) piece else "\"" + Samples.javaString(piece) + "\""
        }.filter { it != "\"\"" }.joinToString(" + ").ifEmpty { "\"{}\"" }
}
