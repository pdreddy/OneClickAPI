package az.mtech.autoapigen.core.write

/**
 * Adds the members and imports that a freshly generated Java file has and the existing file lacks, and
 * leaves everything the user already wrote untouched. Members are matched by name (fields) or by name and
 * parameter types (methods, constructors, nested types), so an edited method body is never overwritten.
 *
 * This is deliberately a small tokenizer, not a Java parser: it only needs to find the top-level type, split
 * its body into members and read their signatures.
 */
object JavaMerger {

    data class Result(
        val content: String,
        val addedMembers: List<String>,
        val addedImports: List<String>,
        /** Things the user should look at after the merge, e.g. a constructor that was left alone. */
        val notes: List<String> = emptyList(),
        /** Non-null when the merge could not be done; [content] is then the unchanged existing text. */
        val problem: String? = null
    ) {
        val changed: Boolean get() = addedMembers.isNotEmpty() || addedImports.isNotEmpty()
    }

    private class Member(
        val key: String,
        val label: String,
        val start: Int,
        val end: Int,
        val isConstructor: Boolean = false
    )

    private class Parsed(
        val typeName: String,
        val text: String,
        val masked: String,
        val bodyStart: Int,
        val bodyEnd: Int,
        val members: List<Member>
    )

    fun merge(existing: String, generated: String): Result {
        val old = parse(existing) ?: return Result(existing, emptyList(), emptyList(), problem = "could not find a class body in the existing file")
        val new = parse(generated) ?: return Result(existing, emptyList(), emptyList(), problem = "could not read the generated file")

        val have = old.members.map { it.key }.toSet()
        val hasConstructor = old.members.any { it.isConstructor }
        val notes = mutableListOf<String>()
        val toAdd = new.members.filter { m ->
            when {
                m.key in have -> false
                // A second constructor next to the user's own would not initialise the fields the old one
                // knows nothing about; leave constructors to the user and say so.
                m.isConstructor && hasConstructor -> {
                    notes += "constructor ${m.label} not added because ${old.typeName} already has one; check that its fields are initialised"
                    false
                }
                else -> true
            }
        }
        if (toAdd.isEmpty()) return Result(existing, emptyList(), emptyList(), notes)

        val chunks = toAdd.map { dropLeadingBlankLines(new.text.substring(it.start, it.end)).trimEnd() }
        val sb = StringBuilder(existing)

        // Insert the new members before a trailing custom-code region if there is one, else before the closing brace.
        val regionLine = findRegionLineStart(old)
        if (regionLine != null) {
            sb.insert(regionLine, chunks.joinToString("\n\n") + "\n\n")
        } else {
            var p = old.bodyEnd
            while (p > old.bodyStart && existing[p - 1].isWhitespace()) p--
            sb.insert(p, "\n\n" + chunks.joinToString("\n\n"))
        }
        var result = sb.toString()

        val importsAdded = mergeImports(result, generated, chunks.joinToString("\n"))
        result = importsAdded.first
        return Result(result, toAdd.map { it.label }, importsAdded.second, notes)
    }

    // ---------------------------------------------------------------- imports

    private val importRegex = Regex("""(?m)^[ \t]*import\s+(static\s+)?([\w.]+(?:\.\*)?)\s*;[ \t]*$""")

    private fun mergeImports(target: String, generated: String, addedCode: String): Pair<String, List<String>> {
        val existing = importRegex.findAll(target).map { (if (it.groupValues[1].isNotEmpty()) "static " else "") + it.groupValues[2] }.toSet()
        val existingSimple = existing.filter { !it.startsWith("static ") && !it.endsWith("*") }
            .associateBy { it.substringAfterLast('.') }
        val wildcardPackages = existing.filter { it.endsWith(".*") && !it.startsWith("static ") }
            .map { it.removeSuffix(".*") }.toSet()
        val own = Regex("""(?m)^\s*package\s+([\w.]+)\s*;""").find(target)?.groupValues?.get(1)

        val missing = mutableListOf<String>()
        for (m in importRegex.findAll(generated)) {
            val isStatic = m.groupValues[1].isNotEmpty()
            val name = m.groupValues[2]
            val key = (if (isStatic) "static " else "") + name
            if (key in existing) continue
            val simple = name.substringAfterLast('.')
            if (!isStatic && !name.endsWith("*")) {
                if (name.substringBeforeLast('.') in wildcardPackages) continue
                if (name.substringBeforeLast('.') == own) continue
                if (existingSimple.containsKey(simple)) continue // a different class with that name is already imported
                if (!Regex("""\b${Regex.escape(simple)}\b""").containsMatchIn(addedCode)) continue
            } else if (isStatic) {
                if (!Regex("""\b${Regex.escape(simple)}\b""").containsMatchIn(addedCode)) continue
            }
            missing += key
        }
        if (missing.isEmpty()) return target to emptyList()

        val lines = missing.sorted().joinToString("\n") { "import $it;" }
        val last = importRegex.findAll(target).lastOrNull()
        val out = if (last != null) {
            target.substring(0, last.range.last + 1) + "\n" + lines + target.substring(last.range.last + 1)
        } else {
            val pkg = Regex("""(?m)^\s*package\s+[\w.]+\s*;[ \t]*$""").find(target)
            if (pkg != null) target.substring(0, pkg.range.last + 1) + "\n\n" + lines + target.substring(pkg.range.last + 1)
            else lines + "\n\n" + target
        }
        return out to missing
    }

    // ---------------------------------------------------------------- parsing

    private fun dropLeadingBlankLines(s: String) = s.replace(Regex("""\A(?:[ \t]*\r?\n)+"""), "")

    private fun findRegionLineStart(p: Parsed): Int? {
        val body = p.text.substring(p.bodyStart, p.bodyEnd)
        val m = Regex("""(?m)^[ \t]*(?://|#|<!--)[ \t]*${Regex.escape(CustomRegions.MARKER)}""").find(body) ?: return null
        return p.bodyStart + m.range.first
    }

    private fun parse(text: String): Parsed? {
        val masked = mask(text)
        val n = masked.length

        // First class/interface/enum/record keyword outside any braces/parentheses.
        var depth = 0
        var paren = 0
        var decl: MatchResult? = null
        val kw = Regex("""\b(class|interface|enum|record)\s+([A-Za-z_]\w*)""")
        val candidates = kw.findAll(masked).toList()
        var ci = 0
        for (i in 0 until n) {
            if (ci < candidates.size && candidates[ci].range.first == i) {
                if (depth == 0 && paren == 0 && (i == 0 || masked[i - 1] != '@')) {
                    decl = candidates[ci]
                    break
                }
                ci++
            }
            when (masked[i]) {
                '{' -> depth++
                '}' -> depth--
                '(' -> paren++
                ')' -> paren--
            }
        }
        decl ?: return null

        var open = -1
        var p = decl.range.last + 1
        var par = 0
        while (p < n) {
            val c = masked[p]
            if (c == '(') par++ else if (c == ')') par-- else if (c == '{' && par == 0) { open = p; break }
            p++
        }
        if (open < 0) return null
        val close = matching(masked, open) ?: return null

        val members = mutableListOf<Member>()
        var chunkStart = open + 1
        var d = 0
        var pr = 0
        var braceOpenInChunk = -1
        var i = open + 1
        while (i < close) {
            when (masked[i]) {
                '(' -> pr++
                ')' -> pr--
                '{' -> {
                    if (d == 0 && pr == 0 && braceOpenInChunk < 0) braceOpenInChunk = i
                    d++
                }
                '}' -> {
                    d--
                    if (d == 0 && pr == 0) {
                        val header = masked.substring(chunkStart, braceOpenInChunk)
                        if (!hasTopLevelEquals(header)) {
                            addMember(members, decl.groupValues[2], masked, chunkStart, i + 1)
                            chunkStart = i + 1
                            braceOpenInChunk = -1
                        }
                    }
                }
                ';' -> if (d == 0 && pr == 0) {
                    addMember(members, decl.groupValues[2], masked, chunkStart, i + 1)
                    chunkStart = i + 1
                    braceOpenInChunk = -1
                }
            }
            i++
        }
        return Parsed(decl.groupValues[2], text, masked, open + 1, close, members)
    }

    private fun hasTopLevelEquals(header: String): Boolean {
        var pr = 0
        for (ch in header) {
            if (ch == '(') pr++ else if (ch == ')') pr-- else if (ch == '=' && pr == 0) return true
        }
        return false
    }

    private fun addMember(out: MutableList<Member>, typeName: String, masked: String, start: Int, end: Int) {
        val sig = describe(masked.substring(start, end), typeName) ?: return
        out += Member(sig.first, sig.second, start, end, sig.first.startsWith("m:$typeName("))
    }

    /** (key, human readable label) for one member chunk, or null for chunks that are only whitespace/comments. */
    private fun describe(maskedChunk: String, typeName: String): Pair<String, String>? {
        val stripped = stripAnnotations(maskedChunk).trim()
        if (stripped.isEmpty() || stripped == ";") return null

        // Header = everything before the first top-level '{', ';' or '='.
        var pr = 0
        var cut = stripped.length
        for ((idx, ch) in stripped.withIndex()) {
            if (ch == '(') pr++
            else if (ch == ')') pr--
            else if (pr == 0 && (ch == '{' || ch == ';' || ch == '=')) { cut = idx; break }
        }
        val header = stripped.substring(0, cut).trim()

        Regex("""\b(class|interface|enum|record)\s+([A-Za-z_]\w*)""").find(header)?.let {
            return "t:${it.groupValues[2]}" to "${it.groupValues[1]} ${it.groupValues[2]}"
        }
        val paren = header.indexOf('(')
        if (paren >= 0) {
            val name = Regex("""([A-Za-z_]\w*)\s*$""").find(header.substring(0, paren))?.groupValues?.get(1) ?: return null
            val closeIdx = matching(header, paren) ?: header.length
            val params = splitParams(header.substring(paren + 1, closeIdx.coerceAtMost(header.length)))
            val types = params.joinToString(",")
            return "m:$name($types)" to "$name($types)"
        }
        if (header.isEmpty() || header == "static") {
            return "i:${stripped.hashCode()}" to "initializer block"
        }
        val name = Regex("""([A-Za-z_]\w*)\s*(?:\[\s*])*$""").find(header)?.groupValues?.get(1) ?: return null
        return "f:$name" to "field $name"
    }

    private fun splitParams(raw: String): List<String> {
        val cleaned = stripAnnotations(raw)
        val parts = mutableListOf<String>()
        var depth = 0
        val sb = StringBuilder()
        for (ch in cleaned) {
            when {
                ch == '<' -> { depth++; sb.append(ch) }
                ch == '>' -> { depth--; sb.append(ch) }
                ch == ',' && depth == 0 -> { parts += sb.toString(); sb.clear() }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotBlank()) parts += sb.toString()
        return parts.mapNotNull { p ->
            var s = p.trim().replace(Regex("""\bfinal\b"""), " ")
            s = s.replace(Regex("""\s*([<,\[])\s*"""), "$1").replace(Regex("""\s*([>\]])"""), "$1").replace(Regex("""\s+"""), " ").trim()
            if (s.isEmpty()) return@mapNotNull null
            val type = s.substringBeforeLast(' ', s)
            type.replace(Regex("""(?:\b[a-z_]\w*\.)+"""), "")
        }
    }

    private fun stripAnnotations(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '@' && i + 1 < s.length && (s[i + 1].isLetter() || s[i + 1] == '_') && !s.startsWith("@interface", i)) {
                i++
                while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '.')) i++
                var j = i
                while (j < s.length && s[j].isWhitespace()) j++
                if (j < s.length && s[j] == '(') {
                    i = (matching(s, j) ?: (s.length - 1)) + 1
                }
                sb.append(' ')
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun matching(s: String, open: Int): Int? {
        val o = s[open]
        val c = when (o) { '{' -> '}'; '(' -> ')'; '[' -> ']'; else -> return null }
        var depth = 0
        for (i in open until s.length) {
            if (s[i] == o) depth++
            else if (s[i] == c) { depth--; if (depth == 0) return i }
        }
        return null
    }

    /** Same-length copy of [src] with comments, string/char literals and text blocks blanked out. */
    private fun mask(src: String): String {
        val out = CharArray(src.length) { src[it] }
        var i = 0
        fun blank(from: Int, to: Int) {
            for (k in from until minOf(to, out.size)) if (out[k] != '\n' && out[k] != '\r') out[k] = ' '
        }
        while (i < src.length) {
            val c = src[i]
            when {
                src.startsWith("//", i) -> {
                    val e = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                    blank(i, e); i = e
                }
                src.startsWith("/*", i) -> {
                    val e = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                    blank(i, e); i = e
                }
                src.startsWith("\"\"\"", i) -> {
                    val e = src.indexOf("\"\"\"", i + 3).let { if (it < 0) src.length else it + 3 }
                    blank(i, e); i = e
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != c && src[j] != '\n') {
                        if (src[j] == '\\') j++
                        j++
                    }
                    blank(i, j + 1); i = j + 1
                }
                else -> i++
            }
        }
        return String(out)
    }
}
