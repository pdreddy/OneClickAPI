package damu.plugin.oneclickapi.core.plan

import freemarker.cache.ClassTemplateLoader
import freemarker.cache.FileTemplateLoader
import freemarker.cache.MultiTemplateLoader
import freemarker.cache.TemplateLoader
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import java.io.File
import java.io.StringWriter

/**
 * FreeMarker wrapper. Templates ship in `/templates` on the classpath; a project can override any of them
 * by dropping a file with the same name into `.oneclickapi/templates/` next to `.oneclickapi.yml`.
 */
class TemplateEngine(overrideDir: File? = null) {
    private val config = Configuration(Configuration.VERSION_2_3_31).apply {
        val loaders = mutableListOf<TemplateLoader>()
        if (overrideDir != null && overrideDir.isDirectory) loaders += FileTemplateLoader(overrideDir)
        loaders += ClassTemplateLoader(TemplateEngine::class.java, "/templates")
        templateLoader = MultiTemplateLoader(loaders.toTypedArray())
        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        // Java source: numbers like 1000 must never be rendered as "1,000".
        numberFormat = "computer"
        booleanFormat = "true,false"
        // Templates see Kotlin maps/lists only; no reflection into our classes.
        objectWrapper = freemarker.template.DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_31).build()
    }

    fun render(templateName: String, model: Map<String, Any?>): String {
        val writer = StringWriter()
        config.getTemplate(templateName).process(model, writer)
        return tidy(writer.toString())
    }

    companion object {
        /** Normalises line endings, trims trailing blanks and collapses runs of blank lines. */
        fun tidy(text: String): String =
            text.replace("\r\n", "\n").replace('\r', '\n')
                .lines().joinToString("\n") { it.trimEnd() }
                .replace(Regex("\n{3,}"), "\n\n")
                .trim('\n') + "\n"
    }
}
