package az.mtech.autoapigen.core.plan

/** A realistic value for a field, rendered for every place that needs one (Java test, JSON, OpenAPI). */
data class Sample(
    /** Java expression, fully qualified so no extra import is needed. */
    val java: String,
    /** JSON literal, e.g. `"sample"` (with quotes) or `1`. */
    val json: String,
    /** Plain text, for query strings and OpenAPI `example`. */
    val text: String,
    val openApiType: String,
    val openApiFormat: String? = null
)

object Samples {

    /** Types the list endpoint can filter on without extra code. */
    val FILTERABLE = setOf(
        "String", "Integer", "int", "Long", "long", "Short", "short", "Byte", "byte", "Double", "double",
        "Float", "float", "Boolean", "boolean", "Character", "char", "BigDecimal", "BigInteger",
        "LocalDate", "LocalDateTime", "LocalTime", "Instant", "OffsetDateTime", "ZonedDateTime", "UUID"
    )

    /**
     * Sample for a type written as in source (`Long`, `String`, `LocalDate` ...). [enumFqn]/[enumFirst]
     * describe an enum type. Returns null for types we cannot invent a value for.
     */
    fun of(
        type: String,
        fieldName: String = "",
        length: Int? = null,
        enumFqn: String? = null,
        enumFirst: String? = null
    ): Sample? {
        if (enumFqn != null) {
            return enumFirst?.let { Sample("$enumFqn.$it", "\"$it\"", it, "string") }
        }
        return when (type) {
            "String" -> {
                val text = when {
                    fieldName.contains("email", ignoreCase = true) -> "user@example.com"
                    else -> "sample"
                }.let { if (length != null && length in 1 until it.length) it.take(length) else it }
                Sample("\"$text\"", "\"$text\"", text, "string", if (fieldName.contains("email", true)) "email" else null)
            }

            "Long", "long" -> Sample("1L", "1", "1", "integer", "int64")
            "Integer", "int" -> Sample("1", "1", "1", "integer", "int32")
            "Short", "short" -> Sample("(short) 1", "1", "1", "integer", "int32")
            "Byte", "byte" -> Sample("(byte) 1", "1", "1", "integer", "int32")
            "Double", "double" -> Sample("1.5", "1.5", "1.5", "number", "double")
            "Float", "float" -> Sample("1.5f", "1.5", "1.5", "number", "float")
            "BigDecimal" -> Sample("new java.math.BigDecimal(\"10.50\")", "10.50", "10.50", "number")
            "BigInteger" -> Sample("java.math.BigInteger.ONE", "1", "1", "integer")
            "Boolean", "boolean" -> Sample("true", "true", "true", "boolean")
            "Character", "char" -> Sample("'a'", "\"a\"", "a", "string")
            "LocalDate" -> Sample("java.time.LocalDate.of(2024, 1, 15)", "\"2024-01-15\"", "2024-01-15", "string", "date")
            "LocalDateTime" -> Sample(
                "java.time.LocalDateTime.of(2024, 1, 15, 10, 30)", "\"2024-01-15T10:30:00\"", "2024-01-15T10:30:00", "string", "date-time"
            )
            "LocalTime" -> Sample("java.time.LocalTime.of(10, 30)", "\"10:30:00\"", "10:30:00", "string")
            "Instant" -> Sample(
                "java.time.Instant.parse(\"2024-01-15T10:30:00Z\")", "\"2024-01-15T10:30:00Z\"", "2024-01-15T10:30:00Z", "string", "date-time"
            )
            "OffsetDateTime" -> Sample(
                "java.time.OffsetDateTime.parse(\"2024-01-15T10:30:00Z\")", "\"2024-01-15T10:30:00Z\"", "2024-01-15T10:30:00Z", "string", "date-time"
            )
            "ZonedDateTime" -> Sample(
                "java.time.ZonedDateTime.parse(\"2024-01-15T10:30:00Z\")", "\"2024-01-15T10:30:00Z\"", "2024-01-15T10:30:00Z", "string", "date-time"
            )
            "UUID" -> Sample(
                "java.util.UUID.fromString(\"123e4567-e89b-12d3-a456-426614174000\")",
                "\"123e4567-e89b-12d3-a456-426614174000\"", "123e4567-e89b-12d3-a456-426614174000", "string", "uuid"
            )

            else -> null
        }
    }

    private val INTEGER_TYPES = setOf("Long", "long", "Integer", "int", "Short", "short", "Byte", "byte")
    private val DECIMAL_TYPES = setOf("Double", "double", "Float", "float", "BigDecimal")

    /**
     * Adjusts [base] so it satisfies the Bean Validation annotations already on the field
     * (`@Size`, `@Min`, `@Max`, `@Positive`, `@Email`, `@Future` ...). Returns null when no sensible value exists
     * (`@Pattern`), in which case the field is left out of generated sample payloads.
     */
    fun constrain(base: Sample?, type: String, annotations: List<String>): Sample? {
        if (base == null || annotations.isEmpty()) return base
        fun has(n: String) = annotations.any { it.startsWith("@$n") }
        fun arg(a: String, key: String): String? =
            annotations.firstOrNull { it.startsWith("@$a") }?.let { Regex("""\b$key\s*=\s*"?(-?\d+(?:\.\d+)?)"?""").find(it)?.groupValues?.get(1) }
        if (has("Pattern")) return null

        if (type == "String") {
            var text = base.text
            if (has("Email")) text = "user@example.com"
            val min = arg("Size", "min")?.toInt()
            val max = arg("Size", "max")?.toInt()
            if (min != null && text.length < min) text = text.padEnd(min, 'x')
            if (max != null && text.length > max) text = text.take(max)
            if (has("NotEmpty") || has("NotBlank")) text = text.ifEmpty { "x" }
            return base.copy(java = "\"$text\"", json = "\"$text\"", text = text)
        }
        if (type in INTEGER_TYPES || type in DECIMAL_TYPES) {
            var v = 1L
            (arg("Min", "value") ?: annotations.firstOrNull { it.startsWith("@Min") }?.let { Regex("""-?\d+""").find(it)?.value })
                ?.toLongOrNull()?.let { v = maxOf(v, it) }
            annotations.firstOrNull { it.startsWith("@DecimalMin") }?.let { Regex("""-?\d+""").find(it)?.value?.toLongOrNull() }
                ?.let { v = maxOf(v, it) }
            annotations.firstOrNull { it.startsWith("@Max") }?.let { Regex("""-?\d+""").find(it)?.value?.toLongOrNull() }
                ?.let { v = minOf(v, it) }
            if (v == 1L) return base
            return number(type, v) ?: base
        }
        if ((has("Future") || has("FutureOrPresent")) && type in setOf("LocalDate", "LocalDateTime", "Instant")) {
            return when (type) {
                "LocalDate" -> base.copy(java = "java.time.LocalDate.of(2999, 12, 31)", json = "\"2999-12-31\"", text = "2999-12-31")
                "LocalDateTime" -> base.copy(java = "java.time.LocalDateTime.of(2999, 12, 31, 10, 30)", json = "\"2999-12-31T10:30:00\"", text = "2999-12-31T10:30:00")
                else -> base.copy(java = "java.time.Instant.parse(\"2999-12-31T10:30:00Z\")", json = "\"2999-12-31T10:30:00Z\"", text = "2999-12-31T10:30:00Z")
            }
        }
        return base
    }

    private fun number(type: String, v: Long): Sample? = when (type) {
        "Long", "long" -> Sample("${v}L", "$v", "$v", "integer", "int64")
        "Integer", "int" -> Sample("$v", "$v", "$v", "integer", "int32")
        "Short", "short" -> Sample("(short) $v", "$v", "$v", "integer", "int32")
        "Byte", "byte" -> Sample("(byte) $v", "$v", "$v", "integer", "int32")
        "Double", "double" -> Sample("$v.0", "$v.0", "$v.0", "number", "double")
        "Float", "float" -> Sample("$v.0f", "$v.0", "$v.0", "number", "float")
        "BigDecimal" -> Sample("new java.math.BigDecimal(\"$v\")", "$v", "$v", "number")
        else -> null
    }

    /** Escapes [s] for use inside a Java string literal. */
    fun javaString(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
