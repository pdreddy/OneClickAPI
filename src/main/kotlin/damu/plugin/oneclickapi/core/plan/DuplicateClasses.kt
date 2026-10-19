package damu.plugin.oneclickapi.core.plan

/**
 * Generating again with another package layout leaves the classes of the earlier run in their old packages. Two
 * Spring beans with the same simple name stop the app with a ConflictingBeanDefinitionException, and the old tests
 * break once the old classes are removed. This finds those clashes before anything is written:
 *
 * - Shared classes (OpenApiConfig, GlobalExceptionHandler, ResourceNotFoundException, ApiError, ApiResult, the upload
 *   services, ...) exist once per application, so an existing copy is reused: the planned copy is dropped and the
 *   planned files that refer to it are pointed at the existing package. They are reused as a set, so the handler that
 *   is kept also handles the exception the new services throw.
 * - For per-entity classes and their tests (ProductService, ProductServiceTest, ...) the old files are reported as
 *   [Result.stale], because the user may be moving to the new layout on purpose and only they can delete them.
 */
object DuplicateClasses {

    data class Result(val files: List<PlannedFile>, val warnings: List<String>, val stale: List<String>)

    /** @param existing module-relative paths of the .java files already under src/main/java and src/test/java */
    fun check(files: List<PlannedFile>, existing: Collection<String>): Result {
        val planned = files.map { it.path }.toSet()
        val byName = existing.filter { it.endsWith(".java") && it !in planned }
            .groupBy { it.substringAfterLast('/').removeSuffix(".java") }

        val moved = mutableMapOf<String, Pair<String, String>>() // name -> (planned package, existing package)
        val kept = files.filter { f ->
            val name = f.fileName.removeSuffix(".java")
            if (f.kind != FileKind.SOURCE || f.group != "shared") return@filter true
            val other = byName[name].orEmpty().firstOrNull { it.contains("src/main/java/") } ?: return@filter true
            moved[name] = f.packageName to PlannedFile(other, "", FileKind.SOURCE).packageName
            false
        }
        val relocated = if (moved.isEmpty()) kept else kept.map { f -> relocateSharedTest(f, moved, kept) }
        val rewritten = if (moved.isEmpty()) relocated else relocated.map { f -> if (f.isJava) retarget(f, moved) else f }

        // Old copies of every generated test count, shared ones included (GlobalExceptionHandlerTest of an earlier layout
        // no longer compiles once its handler lives elsewhere); shared main classes are reused above, never stale.
        val stale = rewritten.filter { it.kind == FileKind.TEST || (it.kind == FileKind.SOURCE && it.group != "shared") }
            .flatMap { f ->
                val dir = if (f.kind == FileKind.TEST) "src/test/java/" else "src/main/java/"
                byName[f.fileName.removeSuffix(".java")].orEmpty().filter { it.contains(dir) && it != f.path }
            }
            .distinct().sorted()

        val warnings = moved.map { (name, pkgs) ->
            "$name already exists in ${pkgs.second}; it is reused instead of generating a second copy in ${pkgs.first} " +
                "(two copies would clash at startup)."
        } + if (stale.isEmpty()) emptyList() else listOf(
            "These classes from an earlier run in another package have the same names as the new ones. Delete them after " +
                "generating, or Spring fails to start with a ConflictingBeanDefinitionException and the old tests stop " +
                "compiling: " + stale.joinToString(", ")
        )
        return Result(rewritten, warnings, stale)
    }

    /**
     * The test of a reused shared class (GlobalExceptionHandlerTest) goes next to the class it tests, so it does not end up
     * in a package where that class does not exist. Classes it used from its old package without an import get one.
     */
    private fun relocateSharedTest(f: PlannedFile, moved: Map<String, Pair<String, String>>, kept: List<PlannedFile>): PlannedFile {
        if (f.kind != FileKind.TEST || f.group != "shared") return f
        val subject = f.fileName.removeSuffix(".java").removeSuffix("Test")
        val (from, to) = moved[subject] ?: return f
        if (from == to || f.packageName != from) return f
        var text = f.content.replaceFirst(Regex("(?m)^package [\\w.]+;"), "package $to;")
        val leftBehind = kept.filter { it.kind == FileKind.SOURCE && it.packageName == from }.map { it.fileName.removeSuffix(".java") }
        for (name in leftBehind) {
            if (Regex("\\b$name\\b").containsMatchIn(text) && !text.contains("import $from.$name;")) {
                text = text.replaceFirst(Regex("(?m)^(package [\\w.]+;[ \\t]*\\n)"), "$1\nimport $from.$name;\n")
            }
        }
        val path = "src/test/java/" + to.replace('.', '/') + "/" + f.fileName
        return f.copy(path = path, content = text)
    }

    private fun retarget(f: PlannedFile, moved: Map<String, Pair<String, String>>): PlannedFile {
        var text = f.content
        for ((name, pkgs) in moved) {
            val (from, to) = pkgs
            text = text.replace("import $from.$name;", "import $to.$name;")
            // Same-package references have no import; add one now that the class lives elsewhere.
            if (f.packageName == from && from != to && Regex("\\b$name\\b").containsMatchIn(text) && !text.contains("import $to.$name;")) {
                text = text.replaceFirst(Regex("(?m)^(package [\\w.]+;[ \\t]*\\n)"), "$1\nimport $to.$name;\n")
            }
        }
        return if (text == f.content) f else f.copy(content = text)
    }
}
