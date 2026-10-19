package damu.plugin.oneclickapi.core.write

/** Merges IntelliJ HTTP Client files: appends the `###` request blocks that the existing file lacks. */
object HttpMerger {
    private val requestLine = Regex("""^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+(\S+)""")

    data class Result(val content: String, val added: List<String>)

    fun merge(existing: String, generated: String): Result {
        val have = blocks(existing).mapNotNull { key(it) }.toSet()
        val missing = blocks(generated).filter { b -> key(b)?.let { it !in have } == true }
        if (missing.isEmpty()) return Result(existing, emptyList())
        val sb = StringBuilder(existing.trimEnd())
        for (b in missing) sb.append("\n\n").append(b.trim('\n'))
        return Result(sb.append('\n').toString(), missing.mapNotNull { key(it) })
    }

    private fun blocks(text: String): List<String> {
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (line in text.lines()) {
            if (line.startsWith("###") && current.isNotBlank()) {
                out += current.toString()
                current = StringBuilder()
            }
            current.append(line).append('\n')
        }
        if (current.isNotBlank()) out += current.toString()
        return out
    }

    private fun key(block: String): String? =
        block.lines().firstNotNullOfOrNull { requestLine.find(it.trim())?.let { m -> m.groupValues[1] + " " + m.groupValues[2] } }
}
