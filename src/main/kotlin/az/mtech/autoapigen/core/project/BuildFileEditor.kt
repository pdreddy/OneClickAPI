package az.mtech.autoapigen.core.project

/**
 * Adds dependencies to a Maven or Gradle build file as text, touching nothing else. The caller shows the result
 * as a diff before anything is written.
 */
object BuildFileEditor {

    class EditException(message: String) : RuntimeException(message)

    /** Returns the new file text; throws [EditException] if no safe insertion point was found. */
    fun addDependencies(tool: BuildTool, text: String, deps: List<Dependency>): String {
        if (deps.isEmpty()) return text
        return when (tool) {
            BuildTool.MAVEN -> addMaven(text, deps)
            BuildTool.GRADLE_KOTLIN -> addGradle(text, deps, kotlin = true)
            BuildTool.GRADLE_GROOVY -> addGradle(text, deps, kotlin = false)
            BuildTool.UNKNOWN -> throw EditException("No pom.xml or build.gradle(.kts) found in the module.")
        }
    }

    // ------------------------------------------------------------------------------------------ Maven

    private fun addMaven(text: String, deps: List<Dependency>): String {
        val nl = if (text.contains("\r\n")) "\r\n" else "\n"
        val closeAt = projectDependenciesClose(text)
        val indent: String
        val insertAt: Int
        val prefix: String
        if (closeAt != null) {
            // Indentation of the existing <dependency> entries, else one level deeper than </dependencies>.
            val lineStart = text.lastIndexOf('\n', closeAt - 1) + 1
            val closeIndent = text.substring(lineStart, closeAt).takeIf { it.isBlank() } ?: ""
            val lastDep = Regex("""(?m)^([ \t]*)<dependency>""").findAll(text.substring(0, closeAt)).lastOrNull()
            indent = lastDep?.groupValues?.get(1) ?: (closeIndent + "    ")
            insertAt = if (text.substring(lineStart, closeAt).isBlank()) lineStart else closeAt
            prefix = ""
        } else {
            val end = text.lastIndexOf("</project>")
            if (end < 0) throw EditException("pom.xml has no </project> tag.")
            val lineStart = text.lastIndexOf('\n', end - 1) + 1
            insertAt = lineStart
            indent = "        "
            prefix = "    <dependencies>$nl"
        }
        val body = deps.joinToString("") { xml(it, indent, nl) }
        val block = if (closeAt != null) body else prefix + body + "    </dependencies>$nl"
        return text.substring(0, insertAt) + block + text.substring(insertAt)
    }

    private fun xml(d: Dependency, indent: String, nl: String): String {
        val inner = "$indent    "
        val sb = StringBuilder()
        sb.append("$indent<!-- OneClickAPI: ${d.reason} -->$nl")
        sb.append("$indent<dependency>$nl")
        sb.append("$inner<groupId>${d.group}</groupId>$nl")
        sb.append("$inner<artifactId>${d.artifact}</artifactId>$nl")
        d.version?.let { sb.append("$inner<version>$it</version>$nl") }
        when (d.scope) {
            Scope.TEST -> sb.append("$inner<scope>test</scope>$nl")
            Scope.COMPILE_ONLY_PROCESSOR, Scope.ANNOTATION_PROCESSOR -> sb.append("$inner<scope>provided</scope>$nl")
            Scope.RUNTIME -> sb.append("$inner<scope>runtime</scope>$nl")
            Scope.IMPLEMENTATION -> Unit
        }
        sb.append("$indent</dependency>$nl")
        return sb.toString()
    }

    /** Index of the `</dependencies>` that closes the project-level block (not dependencyManagement/plugins/profiles). */
    private fun projectDependenciesClose(text: String): Int? {
        val tag = Regex("""<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?]]>|<(/?)([A-Za-z_][\w.-]*)[^>]*?(/?)>""")
        val stack = ArrayDeque<String>()
        for (m in tag.findAll(text)) {
            if (m.groupValues[2].isEmpty()) continue
            val name = m.groupValues[2]
            val closing = m.groupValues[1] == "/"
            val selfClosing = m.groupValues[3] == "/"
            if (closing) {
                if (name == "dependencies" && stack.size == 2 && stack[0] == "project" && stack[1] == "dependencies") return m.range.first
                if (stack.isNotEmpty()) stack.removeLast()
            } else if (!selfClosing && !m.value.startsWith("<?")) {
                stack.addLast(name)
            }
        }
        return null
    }

    // ------------------------------------------------------------------------------------------ Gradle

    private fun addGradle(text: String, deps: List<Dependency>, kotlin: Boolean): String {
        val nl = if (text.contains("\r\n")) "\r\n" else "\n"
        val range = topLevelDependenciesBlock(text)
        val lines = deps.map { gradleLine(it, kotlin) }
        if (range == null) {
            val sep = if (text.endsWith("\n")) "" else nl
            return text + sep + nl + "dependencies {" + nl + lines.joinToString("") { "    $it$nl" } + "}" + nl
        }
        val close = range.last
        val lineStart = text.lastIndexOf('\n', close - 1) + 1
        val before = text.substring(lineStart, close)
        val at = if (before.isBlank()) lineStart else close
        val comment = "    // OneClickAPI$nl"
        return text.substring(0, at) + comment + lines.joinToString("") { "    $it$nl" } + text.substring(at)
    }

    private fun gradleLine(d: Dependency, kotlin: Boolean): String {
        val c = d.coordinates
        fun call(conf: String) = if (kotlin) "$conf(\"$c\")" else "$conf '$c'"
        return when (d.scope) {
            Scope.IMPLEMENTATION -> call("implementation")
            Scope.TEST -> call("testImplementation")
            Scope.RUNTIME -> call("runtimeOnly")
            Scope.ANNOTATION_PROCESSOR -> call("annotationProcessor")
            // Lombok must be on both the compile classpath and the processor path.
            Scope.COMPILE_ONLY_PROCESSOR ->
                call("compileOnly") + (if (kotlin) "\n    annotationProcessor(\"$c\")" else "\n    annotationProcessor '$c'")
        }
    }

    /** Range of the outermost `dependencies { ... }` (not the one inside `buildscript`). */
    private fun topLevelDependenciesBlock(text: String): IntRange? {
        var depth = 0
        var i = 0
        val masked = maskStringsAndComments(text)
        while (i < masked.length) {
            when (masked[i]) {
                '{' -> depth++
                '}' -> depth--
                else -> if (depth == 0 && masked.startsWith("dependencies", i) &&
                    (i == 0 || !masked[i - 1].isLetterOrDigit()) &&
                    Regex("""dependencies\s*\{""").matchAt(masked, i) != null
                ) {
                    val open = masked.indexOf('{', i)
                    var d = 0
                    for (j in open until masked.length) {
                        if (masked[j] == '{') d++ else if (masked[j] == '}') { d--; if (d == 0) return open..j }
                    }
                    return null
                }
            }
            i++
        }
        return null
    }

    private fun maskStringsAndComments(src: String): String {
        val out = CharArray(src.length) { src[it] }
        var i = 0
        fun blank(a: Int, b: Int) { for (k in a until minOf(b, out.size)) if (out[k] != '\n') out[k] = ' ' }
        while (i < src.length) {
            val c = src[i]
            when {
                src.startsWith("//", i) -> { val e = src.indexOf('\n', i).let { if (it < 0) src.length else it }; blank(i, e); i = e }
                src.startsWith("/*", i) -> { val e = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }; blank(i, e); i = e }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != c && src[j] != '\n') { if (src[j] == '\\') j++; j++ }
                    blank(i + 1, j); i = j + 1
                }
                else -> i++
            }
        }
        return String(out)
    }
}
