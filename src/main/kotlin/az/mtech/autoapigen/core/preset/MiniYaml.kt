package az.mtech.autoapigen.core.preset

/**
 * A tiny YAML reader/writer for the one shape `.oneclickapi.yml` uses: nested maps, scalar values and lists
 * (`- item` or `[a, b]`). The IDE bundles a YAML library, but it is not a stable API for plugins, and pulling
 * one in for a dozen keys is not worth the compatibility risk.
 *
 * Values come back as `String`, `List<String>` or `Map<String, Any?>`; callers convert scalars themselves.
 */
object MiniYaml {

    class YamlException(message: String, val line: Int) : RuntimeException("line $line: $message")

    private data class Line(val number: Int, val indent: Int, val text: String)

    fun parse(text: String): Map<String, Any?> {
        val lines = text.lines().mapIndexedNotNull { i, raw ->
            val stripped = stripComment(raw.replace("\t", "  ")).trimEnd()
            if (stripped.isBlank() || stripped.trim() == "---") null
            else Line(i + 1, stripped.length - stripped.trimStart().length, stripped.trim())
        }
        if (lines.isEmpty()) return emptyMap()
        val pos = intArrayOf(0)
        val result = block(lines, pos, lines[0].indent)
        if (pos[0] < lines.size) throw YamlException("unexpected indentation", lines[pos[0]].number)
        return result
    }

    private fun block(lines: List<Line>, pos: IntArray, indent: Int): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        while (pos[0] < lines.size) {
            val line = lines[pos[0]]
            if (line.indent < indent) break
            if (line.indent > indent) throw YamlException("unexpected indentation", line.number)
            if (line.text.startsWith("- ")) throw YamlException("list item without a key", line.number)
            val (key, rest) = splitKey(line)
            pos[0]++
            map[key] = when {
                rest.isNotEmpty() -> scalarOrFlow(rest)
                pos[0] < lines.size && lines[pos[0]].indent > indent -> {
                    val next = lines[pos[0]]
                    if (next.text.startsWith("- ") || next.text == "-") listBlock(lines, pos, next.indent)
                    else block(lines, pos, next.indent)
                }
                // `- item` lists may sit at the same indent as their key
                pos[0] < lines.size && lines[pos[0]].indent == indent && lines[pos[0]].text.startsWith("- ") ->
                    listBlock(lines, pos, indent)
                else -> ""
            }
        }
        return map
    }

    private fun listBlock(lines: List<Line>, pos: IntArray, indent: Int): List<String> {
        val out = mutableListOf<String>()
        while (pos[0] < lines.size && lines[pos[0]].indent == indent && lines[pos[0]].text.startsWith("-")) {
            out += unquote(lines[pos[0]].text.removePrefix("-").trim())
            pos[0]++
        }
        return out
    }

    private fun splitKey(line: Line): Pair<String, String> {
        val t = line.text
        if (t.startsWith("\"") || t.startsWith("'")) {
            val q = t[0]
            val end = t.indexOf(q, 1)
            if (end > 0 && t.substring(end + 1).trimStart().startsWith(":")) {
                return t.substring(1, end) to t.substring(end + 1).trimStart().removePrefix(":").trim()
            }
        }
        var i = 0
        while (i < t.length) {
            if (t[i] == ':' && (i + 1 == t.length || t[i + 1] == ' ')) {
                return t.substring(0, i).trim() to t.substring(i + 1).trim()
            }
            i++
        }
        throw YamlException("expected 'key: value'", line.number)
    }

    private fun scalarOrFlow(s: String): Any =
        if (s.startsWith("[") && s.endsWith("]")) {
            s.substring(1, s.length - 1).split(',').map { unquote(it.trim()) }.filter { it.isNotEmpty() }
        } else unquote(s)

    private fun unquote(s: String): String =
        if (s.length >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))))
            s.substring(1, s.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        else s

    private fun stripComment(raw: String): String {
        var quote: Char? = null
        for ((i, c) in raw.withIndex()) {
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '#' && (i == 0 || raw[i - 1].isWhitespace()) -> return raw.substring(0, i)
            }
        }
        return raw
    }

    // ----------------------------------------------------------------------------------------------- writing

    fun write(map: Map<String, Any?>, baseIndent: Int = 0): String = buildString { writeMap(map, baseIndent, this) }

    private fun writeMap(map: Map<String, Any?>, indent: Int, sb: StringBuilder) {
        val pad = " ".repeat(indent)
        for ((k, v) in map) {
            when (v) {
                null -> Unit
                is Map<*, *> -> {
                    sb.append(pad).append(key(k)).append(":\n")
                    @Suppress("UNCHECKED_CAST")
                    writeMap(v as Map<String, Any?>, indent + 2, sb)
                }

                is List<*> -> {
                    if (v.isEmpty()) sb.append(pad).append(key(k)).append(": []\n")
                    else {
                        sb.append(pad).append(key(k)).append(":\n")
                        v.forEach { sb.append(pad).append("  - ").append(scalar(it.toString())).append('\n') }
                    }
                }

                else -> sb.append(pad).append(key(k)).append(": ").append(scalar(v.toString())).append('\n')
            }
        }
    }

    private fun key(k: String) = if (k.contains(": ") || k.contains('#') || k.startsWith("-") || k.endsWith(":")) quote(k) else k

    private fun scalar(s: String): String {
        val plain = s.isNotEmpty() && s == s.trim() && !s.contains(": ") && !s.contains(" #") &&
                !s.startsWith("[") && !s.startsWith("{") && !s.startsWith("-") && !s.startsWith("\"") &&
                !s.startsWith("'") && !s.startsWith("#") && !s.startsWith("&") && !s.startsWith("*") &&
                !s.startsWith("!") && !s.startsWith("|") && !s.startsWith(">") && !s.startsWith("%") && !s.startsWith("@")
        return if (plain) s else quote(s)
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
