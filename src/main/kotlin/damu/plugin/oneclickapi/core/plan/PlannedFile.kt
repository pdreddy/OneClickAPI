package damu.plugin.oneclickapi.core.plan

import damu.plugin.oneclickapi.core.options.ConflictPolicy

enum class FileKind(val label: String) {
    SOURCE("Source"),
    TEST("Test"),
    RESOURCE("Resource"),
    HTTP("HTTP request"),
    DOC("Documentation"),
    ENTITY_PATCH("Entity update"),
    BUILD("Build file")
}

/**
 * A file the generator would like to write. Nothing is touched on disk until a [Plan] is executed.
 *
 * @property path path relative to the module root, always with `/` separators
 * @property forcedPolicy set when the user's global choice must not apply to this file
 *   (an entity may only be merged, never replaced; a build file is only ever edited)
 */
data class PlannedFile(
    val path: String,
    val content: String,
    val kind: FileKind,
    val description: String = "",
    val group: String = "",
    val forcedPolicy: ConflictPolicy? = null
) {
    val fileName: String get() = path.substringAfterLast('/')
    val isJava: Boolean get() = path.endsWith(".java")

    /** Java package for source files, derived from the path; empty otherwise. */
    val packageName: String
        get() {
            if (!isJava) return ""
            val marker = listOf("src/main/java/", "src/test/java/").firstOrNull { path.contains(it) } ?: return ""
            return path.substringAfter(marker).substringBeforeLast('/', "").replace('/', '.')
        }
}

data class Plan(
    val files: List<PlannedFile>,
    /** Things the user should know before generating, e.g. a missing dependency or a skipped feature. */
    val warnings: List<String> = emptyList()
)
