package damu.plugin.oneclickapi.core.write

import damu.plugin.oneclickapi.core.options.ConflictPolicy
import damu.plugin.oneclickapi.core.plan.FileKind
import damu.plugin.oneclickapi.core.plan.PlannedFile

data class ReportEntry(
    val path: String,
    val kind: FileKind,
    val outcome: Outcome,
    val detail: String = ""
)

data class GenerationReport(
    val entries: List<ReportEntry>,
    val warnings: List<String> = emptyList()
) {
    fun count(outcome: Outcome): Int = entries.count { it.outcome == outcome }
    val written: Int get() = count(Outcome.CREATED) + count(Outcome.REPLACED) + count(Outcome.MERGED)

    val summary: String
        get() = Outcome.entries
            .filter { count(it) > 0 }
            .joinToString(", ") { "${count(it)} ${it.label.lowercase()}" }
            .ifEmpty { "nothing to do" }

    fun toMarkdown(title: String = "OneClickAPI report"): String = buildString {
        appendLine("# $title")
        appendLine()
        appendLine(summary.replaceFirstChar { it.uppercase() } + ".")
        for (outcome in Outcome.entries) {
            val group = entries.filter { it.outcome == outcome }
            if (group.isEmpty()) continue
            appendLine()
            appendLine("## ${outcome.label} (${group.size})")
            for (e in group.sortedBy { it.path }) {
                append("- `${e.path}`")
                if (e.detail.isNotEmpty()) append(" - ${e.detail}")
                appendLine()
            }
        }
        if (warnings.isNotEmpty()) {
            appendLine()
            appendLine("## Warnings (${warnings.size})")
            warnings.forEach { appendLine("- $it") }
        }
    }
}

/** A planned file together with what the chosen policy would do to it. */
data class ResolvedFile(
    val file: PlannedFile,
    val existing: String?,
    val policy: ConflictPolicy,
    val resolution: Resolution
)

class PlanExecutor(private val fs: ProjectFiles) {

    /** Dry run: what would happen to each file. Reads the disk, never writes. */
    fun resolve(files: List<PlannedFile>, policyFor: (PlannedFile) -> ConflictPolicy): List<ResolvedFile> =
        files.map { f ->
            val existing = fs.read(f.path)
            val policy = ConflictResolver.effectivePolicy(f, policyFor(f))
            ResolvedFile(f, existing, policy, ConflictResolver.resolve(existing, f, policy))
        }

    /**
     * Writes every file that is not in [excluded]. One failing file does not stop the others; it shows up
     * as FAILED in the report.
     */
    fun execute(
        files: List<PlannedFile>,
        policyFor: (PlannedFile) -> ConflictPolicy,
        excluded: Set<String> = emptySet(),
        warnings: List<String> = emptyList()
    ): GenerationReport {
        val entries = mutableListOf<ReportEntry>()
        for (r in resolve(files, policyFor)) {
            val f = r.file
            if (f.path in excluded) {
                entries += ReportEntry(f.path, f.kind, Outcome.SKIPPED, "deselected in preview")
                continue
            }
            val res = r.resolution
            if (!res.writes) {
                entries += ReportEntry(f.path, f.kind, res.outcome, res.detail)
                continue
            }
            try {
                fs.write(f.path, res.content!!)
                entries += ReportEntry(f.path, f.kind, res.outcome, res.detail)
            } catch (e: Exception) {
                entries += ReportEntry(f.path, f.kind, Outcome.FAILED, e.message ?: e.javaClass.simpleName)
            }
        }
        return GenerationReport(entries, warnings)
    }
}
