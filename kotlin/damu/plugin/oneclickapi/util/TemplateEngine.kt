// Copyright 2026 Damohara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

// damu.plugin.oneclickapi.util/TemplateEngine.kt

package damu.plugin.oneclickapi.util

import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateExceptionHandler
import java.io.StringWriter

/**
 * Singleton object for configuring and running the FreeMarker Template Engine.
 */
object TemplateEngine {
    private val config: Configuration = Configuration(Configuration.VERSION_2_3_31).apply {

        // Specify the directory where the templates are located (inside resources)
        setClassForTemplateLoading(TemplateEngine::class.java, "/templates")

        // Set the default character encoding
        defaultEncoding = "UTF-8"

        // Enable API built-ins (recommended for modern FreeMarker usage)
        setAPIBuiltinEnabled(true)

        // Configure how FreeMarker handles object wrapping (important for modern Java objects)
        objectWrapper = freemarker.template.DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_31).build()

        // Set the exception handler. RETHROW_HANDLER ensures exceptions are passed up the chain.
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER

        // Disable template exceptions logging to the console (handled internally)
        logTemplateExceptions = false

        // Wrap unchecked exceptions for cleaner handling
        wrapUncheckedExceptions = true
    }

    /**
     * Executes the FreeMarker template with the provided data model and returns the result as a String.
     * * @param templateName The name of the template file (e.g., "Controller.java.ft").
     * @param dataModel A map containing the dynamic variables to be used in the template.
     * @return The generated file content as a String.
     */
    fun processTemplate(templateName: String, dataModel: Map<String, Any>): String {
        val template: Template = config.getTemplate(templateName)
        val writer = StringWriter()
        template.process(dataModel, writer)
        return writer.toString()
    }
}