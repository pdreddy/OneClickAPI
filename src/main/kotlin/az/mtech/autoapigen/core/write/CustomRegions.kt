package az.mtech.autoapigen.core.write

/**
 * Generated files carry an empty, foldable region:
 *
 * ```
 * // region custom code (kept when you regenerate)
 * // endregion
 * ```
 *
 * Whatever the user writes between the markers survives Replace and Merge. A region can be given a name,
 * `// region custom code [queries]`, when a file needs more than one. `#` and `<!-- -->` comments work too.
 */
object CustomRegions {
    const val MARKER = "region custom code"

    private val start = Regex("""^\s*(?://|#|<!--)\s*region custom code(?:\s*\[([\w-]+)])?.*$""")
    private val end = Regex("""^\s*(?://|#|<!--)\s*endregion.*$""")

    /** Region name ("" for the unnamed one) -> body, for every region that actually has content. */
    fun extract(text: String): Map<String, List<String>> {
        val result = linkedMapOf<String, List<String>>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val m = start.matchEntire(lines[i])
            if (m != null) {
                val key = m.groupValues[1]
                val body = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size && !end.matches(lines[j])) {
                    body += lines[j]
                    j++
                }
                // Only a properly closed region counts; an unterminated one would swallow the rest of the file.
                if (j < lines.size && body.any { it.isNotBlank() }) result[key] = body
                i = j
            }
            i++
        }
        return result
    }

    /** Puts [regions] into the matching (empty) regions of freshly generated [generated]. */
    fun inject(generated: String, regions: Map<String, List<String>>): String {
        if (regions.isEmpty()) return generated
        val out = mutableListOf<String>()
        val lines = generated.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            out += line
            val m = start.matchEntire(line)
            val body = m?.let { regions[it.groupValues[1]] }
            if (m != null && body != null) {
                var j = i + 1
                while (j < lines.size && !end.matches(lines[j])) j++
                if (j < lines.size) {
                    out += body
                    i = j
                    continue
                }
            }
            i++
        }
        return out.joinToString("\n")
    }

    fun count(text: String): Int = extract(text).size
}
