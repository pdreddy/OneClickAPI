package damu.plugin.oneclickapi.core.write

import damu.plugin.oneclickapi.core.options.ConflictPolicy
import damu.plugin.oneclickapi.core.plan.PlannedFile

enum class Outcome(val label: String) {
    CREATED("Created"),
    REPLACED("Replaced"),
    MERGED("Merged"),
    SKIPPED("Skipped"),
    UNCHANGED("Unchanged"),
    FAILED("Failed")
}

/** What would happen to one file: the text it ends up with (null = leave the disk alone) and why. */
data class Resolution(val outcome: Outcome, val content: String?, val detail: String = "") {
    val writes: Boolean get() = content != null
}

object ConflictResolver {

    /** The policy that really applies to [file] when the user asked for [requested]. */
    fun effectivePolicy(file: PlannedFile, requested: ConflictPolicy): ConflictPolicy = file.forcedPolicy ?: requested

    fun resolve(existing: String?, file: PlannedFile, requested: ConflictPolicy): Resolution {
        val policy = effectivePolicy(file, requested)

        if (existing == null) {
            return if (file.kind == damu.plugin.oneclickapi.core.plan.FileKind.ENTITY_PATCH) {
                Resolution(Outcome.SKIPPED, null, "entity source not found")
            } else {
                Resolution(Outcome.CREATED, file.content)
            }
        }

        val same = normalize(existing) == normalize(file.content)
        return when (policy) {
            ConflictPolicy.SKIP ->
                if (same) Resolution(Outcome.UNCHANGED, null) else Resolution(Outcome.SKIPPED, null, "file already exists")

            ConflictPolicy.REPLACE -> replace(existing, file, same)
            ConflictPolicy.MERGE -> merge(existing, file, same)
        }
    }

    private fun replace(existing: String, file: PlannedFile, same: Boolean): Resolution {
        if (same) return Resolution(Outcome.UNCHANGED, null)
        val regions = CustomRegions.extract(existing)
        val content = CustomRegions.inject(file.content, regions)
        if (normalize(content) == normalize(existing)) return Resolution(Outcome.UNCHANGED, null)
        val detail = if (regions.isNotEmpty()) "kept ${regions.size} custom code region(s)" else ""
        return Resolution(Outcome.REPLACED, content, detail)
    }

    private fun merge(existing: String, file: PlannedFile, same: Boolean): Resolution {
        if (same) return Resolution(Outcome.UNCHANGED, null)
        return when {
            file.isJava -> {
                val r = JavaMerger.merge(existing, file.content)
                when {
                    r.problem != null -> Resolution(Outcome.SKIPPED, null, "merge failed: ${r.problem}")
                    !r.changed -> Resolution(Outcome.UNCHANGED, null, (listOf("nothing to add") + r.notes).joinToString("; "))
                    else -> Resolution(
                        Outcome.MERGED, r.content,
                        ("added " + r.addedMembers.joinToString(", ") +
                                if (r.addedImports.isNotEmpty()) "; imports: " + r.addedImports.joinToString(", ") else "") +
                                r.notes.joinToString("") { "; $it" }
                    )
                }
            }

            file.path.endsWith(".http") -> {
                val r = HttpMerger.merge(existing, file.content)
                if (r.added.isEmpty()) Resolution(Outcome.UNCHANGED, null, "nothing to add")
                else Resolution(Outcome.MERGED, r.content, "added requests: " + r.added.joinToString(", "))
            }

            else -> Resolution(Outcome.SKIPPED, null, "merge is not supported for this file type; existing file kept")
        }
    }

    private fun normalize(s: String) = s.replace("\r\n", "\n").trimEnd()
}
