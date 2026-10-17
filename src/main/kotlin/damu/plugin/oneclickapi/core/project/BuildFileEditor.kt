package damu.plugin.oneclickapi.core.project

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

    /** JaCoCo version written to the build file; supports Java up to 25. */
    const val JACOCO = "0.8.14"

    /**
     * Makes `test` also write a JaCoCo coverage report: `build/reports/jacoco/test/html/index.html` (Gradle) or
     * `target/site/jacoco/index.html` (Maven). Returns [text] unchanged if the build already mentions JaCoCo.
     */
    fun addJacoco(tool: BuildTool, text: String): String {
        if (text.contains("jacoco", ignoreCase = true)) return text
        return when (tool) {
            BuildTool.MAVEN -> addMavenJacoco(text)
            BuildTool.GRADLE_KOTLIN -> addGradleJacoco(text, kotlin = true)
            BuildTool.GRADLE_GROOVY -> addGradleJacoco(text, kotlin = false)
            BuildTool.UNKNOWN -> throw EditException("No pom.xml or build.gradle(.kts) found in the module.")
        }
    }

    private fun addGradleJacoco(text: String, kotlin: Boolean): String {
        val nl = if (text.contains("\r\n")) "\r\n" else "\n"
        val plugins = topLevelBlock(text, "plugins")
        val withPlugin = if (plugins != null) {
            val close = plugins.last
            val lineStart = text.lastIndexOf('\n', close - 1) + 1
            val at = if (text.substring(lineStart, close).isBlank()) lineStart else close
            text.substring(0, at) + (if (kotlin) "    jacoco" else "    id 'jacoco'") + nl + text.substring(at)
        } else {
            (if (kotlin) "apply(plugin = \"jacoco\")" else "apply plugin: 'jacoco'") + nl + nl + text
        }
        val config = if (kotlin) listOf(
            "configure<JacocoPluginExtension> { toolVersion = \"$JACOCO\" }",
            "tasks.named<Test>(\"test\") { finalizedBy(\"jacocoTestReport\") }",
            "tasks.named<JacocoReport>(\"jacocoTestReport\") {",
            "    dependsOn(\"test\")",
            "    reports { xml.required.set(true); html.required.set(true) }",
            "}"
        ) else listOf(
            "jacoco { toolVersion = '$JACOCO' }",
            "tasks.named('test') { finalizedBy 'jacocoTestReport' }",
            "tasks.named('jacocoTestReport') {",
            "    dependsOn 'test'",
            "    reports { xml.required = true; html.required = true }",
            "}"
        )
        val sep = if (withPlugin.endsWith("\n")) "" else nl
        return withPlugin + sep + nl + "// OneClickAPI: test coverage report in build/reports/jacoco/test/html/index.html" + nl +
            config.joinToString(nl) + nl
    }

    private fun addMavenJacoco(text: String): String {
        val nl = if (text.contains("\r\n")) "\r\n" else "\n"
        fun plugin(indent: String): String {
            val i1 = "$indent    "
            val i2 = "$i1    "
            val i3 = "$i2    "
            val i4 = "$i3    "
            return listOf(
                "$indent<!-- OneClickAPI: test coverage report in target/site/jacoco/index.html -->",
                "$indent<plugin>",
                "$i1<groupId>org.jacoco</groupId>",
                "$i1<artifactId>jacoco-maven-plugin</artifactId>",
                "$i1<version>$JACOCO</version>",
                "$i1<executions>",
                "$i2<execution>",
                "$i3<goals>",
                "$i4<goal>prepare-agent</goal>",
                "$i3</goals>",
                "$i2</execution>",
                "$i2<execution>",
                "$i3<id>report</id>",
                "$i3<phase>test</phase>",
                "$i3<goals>",
                "$i4<goal>report</goal>",
                "$i3</goals>",
                "$i2</execution>",
                "$i1</executions>",
                "$indent</plugin>"
            ).joinToString(nl) + nl
        }
        closeOf(text, listOf("project", "build", "plugins"))?.let { close ->
            val lineStart = text.lastIndexOf('\n', close - 1) + 1
            val closeIndent = text.substring(lineStart, close).takeIf { it.isBlank() } ?: ""
            val at = if (closeIndent.isNotEmpty() || lineStart == close) lineStart else close
            return text.substring(0, at) + plugin("$closeIndent    ") + text.substring(at)
        }
        closeOf(text, listOf("project", "build"))?.let { close ->
            val lineStart = text.lastIndexOf('\n', close - 1) + 1
            val at = if (text.substring(lineStart, close).isBlank()) lineStart else close
            return text.substring(0, at) + "        <plugins>$nl" + plugin("            ") + "        </plugins>$nl" + text.substring(at)
        }
        val end = text.lastIndexOf("</project>")
        if (end < 0) throw EditException("pom.xml has no </project> tag.")
        val lineStart = text.lastIndexOf('\n', end - 1) + 1
        return text.substring(0, lineStart) + "    <build>$nl        <plugins>$nl" + plugin("            ") +
            "        </plugins>$nl    </build>$nl" + text.substring(lineStart)
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
    private fun projectDependenciesClose(text: String): Int? = closeOf(text, listOf("project", "dependencies"))

    /** Index of the closing tag of the element at exactly [path] from the root, e.g. project/build/plugins. */
    private fun closeOf(text: String, path: List<String>): Int? {
        val tag = Regex("""<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?]]>|<(/?)([A-Za-z_][\w.-]*)[^>]*?(/?)>""")
        val stack = ArrayDeque<String>()
        for (m in tag.findAll(text)) {
            if (m.groupValues[2].isEmpty()) continue
            val name = m.groupValues[2]
            val closing = m.groupValues[1] == "/"
            val selfClosing = m.groupValues[3] == "/"
            if (closing) {
                if (name == path.last() && stack.toList() == path) return m.range.first
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
    private fun topLevelDependenciesBlock(text: String): IntRange? = topLevelBlock(text, "dependencies")

    /** Range of the top-level `name { ... }` block, braces included. */
    private fun topLevelBlock(text: String, name: String): IntRange? {
        var depth = 0
        var i = 0
        val masked = maskStringsAndComments(text)
        while (i < masked.length) {
            when (masked[i]) {
                '{' -> depth++
                '}' -> depth--
                else -> if (depth == 0 && masked.startsWith(name, i) &&
                    (i == 0 || !masked[i - 1].isLetterOrDigit()) &&
                    Regex("""$name\s*\{""").matchAt(masked, i) != null
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
