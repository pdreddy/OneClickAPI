package damu.plugin.oneclickapi.core.preset

import damu.plugin.oneclickapi.core.options.ApiVersioning
import damu.plugin.oneclickapi.core.options.ConflictPolicy
import damu.plugin.oneclickapi.core.options.ConverterType
import damu.plugin.oneclickapi.core.options.GenerationOptions
import damu.plugin.oneclickapi.core.options.LiquibaseFormat
import damu.plugin.oneclickapi.core.options.LiquibaseOptions
import damu.plugin.oneclickapi.core.options.OpenApiOptions
import damu.plugin.oneclickapi.core.options.PackageLayout
import damu.plugin.oneclickapi.core.options.RepositoryType
import damu.plugin.oneclickapi.core.options.Standards
import damu.plugin.oneclickapi.core.options.TestOptions
import damu.plugin.oneclickapi.core.write.ProjectFiles

/** A named, shareable set of generator options. [values] uses the same keys as `.oneclickapi.yml`. */
data class Preset(val name: String, val description: String, val values: Map<String, Any?>, val builtIn: Boolean = false)

/** Result of reading `.oneclickapi.yml`; a broken file produces [problems], never an exception. */
data class PresetFile(
    val presets: List<Preset>,
    val defaultPreset: String?,
    val problems: List<String> = emptyList(),
    val exists: Boolean = false
)

object BuiltInPresets {

    private fun m(vararg pairs: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(*pairs)

    val STANDARD = Preset(
        "Standard Spring Boot CRUD", "Controller, service, repository, MapStruct mapper and DTOs with validation, plus unit tests.",
        m(
            "lombok" to "true", "repository" to "JPA Repository", "converter" to "MapStruct",
            "standards" to m("validation" to "true", "relationDtos" to "true", "versioning" to "path", "apiVersion" to "1"),
            "tests" to m("service" to "true", "controller" to "true"),
            "onConflict" to "skip"
        ),
        builtIn = true
    )

    val CLEAN = Preset(
        "Clean Architecture", "Web / application / infrastructure packages, standard response envelope, full tests.",
        m(
            "lombok" to "true", "repository" to "JPA Repository", "converter" to "MapStruct",
            "layout" to m(
                "controller" to "web.controller", "dto" to "web.dto", "mapper" to "web.mapper",
                "service" to "application.service",
                "repository" to "infrastructure.persistence", "persistence" to "infrastructure.persistence",
                "exception" to "web.exception", "config" to "infrastructure.config"
            ),
            "standards" to m(
                "validation" to "true", "standardResponse" to "true", "relationDtos" to "true", "versioning" to "path", "apiVersion" to "1"
            ),
            "tests" to m("service" to "true", "controller" to "true"),
            "onConflict" to "skip"
        ),
        builtIn = true
    )

    val ENTERPRISE = Preset(
        "Enterprise API", "Everything on: pagination and search, standard responses and errors, soft delete, audit fields, OpenAPI, service, controller, mapper and full-stack API tests and .http files.",
        m(
            "lombok" to "true", "repository" to "JPA Repository", "converter" to "MapStruct", "exceptionHandling" to "true",
            "standards" to m(
                "validation" to "true", "pagination" to "true", "standardResponse" to "true", "softDelete" to "true",
                "auditFields" to "true", "relationDtos" to "true", "versioning" to "path", "apiVersion" to "1"
            ),
            "tests" to m(
                "service" to "true", "controller" to "true", "http" to "true",
                "mapper" to "true", "integration" to "true"
            ),
            "openApi" to m("annotations" to "true", "spec" to "true", "config" to "true"),
            "onConflict" to "merge"
        ),
        builtIn = true
    )

    val MYBATIS = Preset(
        "MyBatis Backend", "MyBatis mapper + XML, pagination with LIMIT/OFFSET, standard responses, tests. Use with plain POJO classes.",
        m(
            "lombok" to "true", "converter" to "MapStruct",
            "standards" to m(
                "validation" to "true", "pagination" to "true", "standardResponse" to "true", "relationDtos" to "true",
                "versioning" to "path", "apiVersion" to "1"
            ),
            "tests" to m("service" to "true", "controller" to "true"),
            "onConflict" to "skip"
        ),
        builtIn = true
    )

    val all: List<Preset> = listOf(STANDARD, CLEAN, ENTERPRISE, MYBATIS)
}

/** Converts between [GenerationOptions] and the string-valued maps used in `.oneclickapi.yml`. */
object PresetCodec {

    private fun Any?.str(): String? = (this as? String)?.takeIf { it.isNotEmpty() }
    private fun Any?.bool(): Boolean? = (this as? String)?.lowercase()?.let { if (it == "true" || it == "yes") true else if (it == "false" || it == "no") false else null }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.map(): Map<String, Any?> = (this as? Map<String, Any?>) ?: emptyMap()

    /** Applies every key present in [values] on top of [base]; unknown keys and bad values are ignored but reported. */
    fun apply(base: GenerationOptions, values: Map<String, Any?>, problems: MutableList<String> = mutableListOf()): GenerationOptions {
        var o = base
        values["basePackage"].str()?.let { o = o.copy(basePackage = it) }
        values["lombok"].bool()?.let { o = o.copy(lombok = it) }
        values["exceptionHandling"].bool()?.let { o = o.copy(exceptionHandling = it) }
        values["repository"].str()?.let { l -> RepositoryType.entries.firstOrNull { it.label.equals(l, true) }?.let { o = o.copy(repositoryType = it) } ?: problems.add("unknown repository '$l'") }
        values["converter"].str()?.let { l -> ConverterType.entries.firstOrNull { it.label.equals(l, true) || it.name.equals(l, true) }?.let { o = o.copy(converter = it) } ?: problems.add("unknown converter '$l'") }
        values["onConflict"].str()?.let { l -> ConflictPolicy.entries.firstOrNull { it.name.equals(l, true) }?.let { o = o.copy(conflictPolicy = it) } ?: problems.add("unknown onConflict '$l'") }

        values["layout"].map().takeIf { it.isNotEmpty() }?.let { l ->
            val d = o.layout
            o = o.copy(
                layout = PackageLayout(
                    controller = l["controller"].str() ?: d.controller, service = l["service"].str() ?: d.service,
                    repository = l["repository"].str() ?: d.repository, mapper = l["mapper"].str() ?: d.mapper,
                    persistence = l["persistence"].str() ?: d.persistence, dto = l["dto"].str() ?: d.dto,
                    exception = l["exception"].str() ?: d.exception, config = l["config"].str() ?: d.config
                )
            )
        }
        values["standards"].map().takeIf { it.isNotEmpty() }?.let { s ->
            val d = o.standards
            val versioning = s["versioning"].str()?.let { v ->
                when (v.lowercase()) {
                    "path" -> ApiVersioning.PATH
                    "header" -> ApiVersioning.HEADER
                    "none" -> ApiVersioning.NONE
                    else -> { problems.add("unknown versioning '$v'"); null }
                }
            }
            o = o.copy(
                standards = Standards(
                    validation = s["validation"].bool() ?: d.validation,
                    pagination = s["pagination"].bool() ?: d.pagination,
                    standardResponse = s["standardResponse"].bool() ?: d.standardResponse,
                    softDelete = s["softDelete"].bool() ?: d.softDelete,
                    auditFields = s["auditFields"].bool() ?: d.auditFields,
                    versioning = versioning ?: d.versioning,
                    apiVersion = s["apiVersion"].str()?.toIntOrNull()?.takeIf { it > 0 } ?: d.apiVersion,
                    relationDtos = s["relationDtos"].bool() ?: d.relationDtos
                )
            )
        }
        values["tests"].map().takeIf { it.isNotEmpty() }?.let { t ->
            val d = o.tests
            o = o.copy(
                tests = TestOptions(
                    serviceTests = t["service"].bool() ?: d.serviceTests,
                    controllerTests = t["controller"].bool() ?: d.controllerTests,
                    httpFile = t["http"].bool() ?: d.httpFile,
                    mapperTests = t["mapper"].bool() ?: d.mapperTests,
                    integrationTests = t["integration"].bool() ?: d.integrationTests
                )
            )
        }
        values["openApi"].map().takeIf { it.isNotEmpty() }?.let { a ->
            val d = o.openApi
            o = o.copy(
                openApi = OpenApiOptions(
                    annotations = a["annotations"].bool() ?: d.annotations,
                    spec = a["spec"].bool() ?: d.spec,
                    config = a["config"].bool() ?: d.config
                )
            )
        }
        values["liquibase"].map().takeIf { it.isNotEmpty() }?.let { l ->
            if (l["enabled"].bool() == true) {
                val fmt = l["format"].str()?.let { f -> LiquibaseFormat.entries.firstOrNull { it.name.equals(f, true) } } ?: LiquibaseFormat.XML
                o = o.copy(liquibase = LiquibaseOptions(l["path"].str() ?: o.liquibase?.packagePath ?: "db/changelog", o.liquibase?.fileName ?: "", fmt))
            } else if (l["enabled"].bool() == false) o = o.copy(liquibase = null)
        }
        return o
    }

    /** Everything a preset can carry, as written to `.oneclickapi.yml`. */
    fun toValues(o: GenerationOptions, includeBasePackage: Boolean = false): Map<String, Any?> {
        val m = linkedMapOf<String, Any?>()
        if (includeBasePackage && o.basePackage.isNotEmpty()) m["basePackage"] = o.basePackage
        m["lombok"] = o.lombok.toString()
        m["repository"] = o.repositoryType.label
        m["converter"] = o.converter.label
        m["exceptionHandling"] = o.exceptionHandling.toString()
        m["layout"] = linkedMapOf(
            "controller" to o.layout.controller, "service" to o.layout.service, "repository" to o.layout.repository,
            "mapper" to o.layout.mapper, "persistence" to o.layout.persistence, "dto" to o.layout.dto,
            "exception" to o.layout.exception, "config" to o.layout.config
        )
        val s = o.standards
        m["standards"] = linkedMapOf(
            "validation" to s.validation.toString(), "pagination" to s.pagination.toString(),
            "standardResponse" to s.standardResponse.toString(), "softDelete" to s.softDelete.toString(),
            "auditFields" to s.auditFields.toString(), "relationDtos" to s.relationDtos.toString(),
            "versioning" to s.versioning.name.lowercase(), "apiVersion" to s.apiVersion.toString()
        )
        m["tests"] = linkedMapOf(
            "service" to o.tests.serviceTests.toString(), "controller" to o.tests.controllerTests.toString(),
            "http" to o.tests.httpFile.toString(), "mapper" to o.tests.mapperTests.toString(),
            "integration" to o.tests.integrationTests.toString()
        )
        m["openApi"] = linkedMapOf(
            "annotations" to o.openApi.annotations.toString(), "spec" to o.openApi.spec.toString(), "config" to o.openApi.config.toString()
        )
        m["onConflict"] = o.conflictPolicy.name.lowercase()
        return m
    }
}

/** Reads and writes `.oneclickapi.yml` in the repository root. */
class PresetStore(private val root: ProjectFiles) {

    fun load(): PresetFile {
        val text = root.read(FILE_NAME) ?: return PresetFile(emptyList(), null)
        val problems = mutableListOf<String>()
        val doc = try {
            MiniYaml.parse(text)
        } catch (e: MiniYaml.YamlException) {
            return PresetFile(emptyList(), null, listOf("$FILE_NAME: ${e.message}"), exists = true)
        }
        @Suppress("UNCHECKED_CAST")
        val raw = (doc["presets"] as? Map<String, Any?>) ?: emptyMap()
        val byName = linkedMapOf<String, Map<String, Any?>>()
        raw.forEach { (name, v) -> (v as? Map<String, Any?>)?.let { byName[name] = it } ?: problems.add("preset '$name' is empty or not a map") }

        fun resolve(name: String, seen: Set<String>): Map<String, Any?> {
            val own = byName[name] ?: BuiltInPresets.all.firstOrNull { it.name == name }?.values ?: run {
                problems.add("preset '$name' extends unknown preset"); return emptyMap()
            }
            val parent = own["extends"] as? String
            if (parent == null) return own
            if (parent in seen || name in seen) { problems.add("preset '$name' has a circular 'extends'"); return own }
            return deepMerge(resolve(parent, seen + name), own - "extends")
        }

        val presets = byName.keys.map { n ->
            val values = resolve(n, emptySet())
            Preset(n, (byName[n]?.get("description") as? String) ?: "", values - "description")
        }
        val default = (doc["defaultPreset"] as? String)?.takeIf { it.isNotEmpty() }
        if (default != null && presets.none { it.name == default } && BuiltInPresets.all.none { it.name == default }) {
            problems.add("defaultPreset '$default' does not exist")
        }
        return PresetFile(presets, default, problems, exists = true)
    }

    /** All presets a user can pick: those of the file first, then built-ins that the file does not override. */
    fun available(): List<Preset> {
        val file = load()
        return file.presets + BuiltInPresets.all.filter { b -> file.presets.none { it.name == b.name } }
    }

    fun save(name: String, description: String, values: Map<String, Any?>, makeDefault: Boolean = false) {
        val body = linkedMapOf<String, Any?>()
        if (description.isNotBlank()) body["description"] = description
        body.putAll(values)
        root.write(FILE_NAME, upsert(root.read(FILE_NAME), name, body, makeDefault))
    }

    companion object {
        const val FILE_NAME = ".oneclickapi.yml"
        const val TEMPLATE_DIR = ".oneclickapi/templates"

        @Suppress("UNCHECKED_CAST")
        private fun deepMerge(a: Map<String, Any?>, b: Map<String, Any?>): Map<String, Any?> {
            val out = LinkedHashMap(a)
            for ((k, v) in b) {
                val old = out[k]
                out[k] = if (old is Map<*, *> && v is Map<*, *>) deepMerge(old as Map<String, Any?>, v as Map<String, Any?>) else v
            }
            return out
        }

        /**
         * Inserts or replaces one preset block in [existing] without touching the rest of the file, so comments
         * and the other presets survive.
         */
        fun upsert(existing: String?, name: String, body: Map<String, Any?>, makeDefault: Boolean = false): String {
            val blockLines = MiniYaml.write(mapOf(name to body)).trimEnd('\n').lines().map { "  $it" }
            val defaultLine = "defaultPreset: " + MiniYaml.write(mapOf("x" to name)).removePrefix("x: ").trim()

            if (existing.isNullOrBlank()) {
                return buildList {
                    add("version: 1")
                    if (makeDefault) add(defaultLine)
                    add("presets:")
                    addAll(blockLines)
                }.joinToString("\n") + "\n"
            }

            val lines = existing.replace("\r\n", "\n").lines().toMutableList()
            while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)

            var presetsAt = lines.indexOfFirst { it.trimEnd() == "presets:" }
            if (makeDefault) {
                val idx = lines.indexOfFirst { it.startsWith("defaultPreset:") }
                if (idx >= 0) lines[idx] = defaultLine
                else {
                    lines.add(if (presetsAt >= 0) presetsAt else lines.size, defaultLine)
                    if (presetsAt >= 0) presetsAt++
                }
            }

            if (presetsAt < 0) {
                lines.add("presets:")
                lines.addAll(blockLines)
                return lines.joinToString("\n") + "\n"
            }

            // The presets section ends at the next unindented, non-comment line.
            val sectionEnd = (presetsAt + 1 until lines.size).firstOrNull { i ->
                lines[i].isNotBlank() && !lines[i].trimStart().startsWith("#") && !lines[i].startsWith(" ")
            } ?: lines.size
            val header = Regex("""^  ("?)${Regex.escape(name)}\1:\s*$""")
            val start = (presetsAt + 1 until sectionEnd).firstOrNull { header.matches(lines[it]) }
            if (start != null) {
                var end = start + 1
                while (end < sectionEnd && (lines[end].isBlank() || lines[end].startsWith("   "))) end++
                while (end > start + 1 && lines[end - 1].isBlank()) end-- // keep the gap before the next preset
                repeat(end - start) { lines.removeAt(start) }
                lines.addAll(start, blockLines)
            } else {
                var at = sectionEnd
                while (at > presetsAt + 1 && lines[at - 1].isBlank()) at--
                lines.addAll(at, blockLines)
            }
            return lines.joinToString("\n") + "\n"
        }
    }
}
