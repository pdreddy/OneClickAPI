package az.mtech.autoapigen.core

import az.mtech.autoapigen.core.model.EntityModel
import az.mtech.autoapigen.core.model.FieldModel
import az.mtech.autoapigen.core.model.OrmType
import az.mtech.autoapigen.core.model.RelationKind
import az.mtech.autoapigen.core.model.RelationTarget
import az.mtech.autoapigen.core.write.MemoryFiles

/** Hand-written models that mirror the .java files in src/test/resources/samples. */
object SampleEntities {

    private const val VALID = "jakarta.validation.constraints"

    val author = EntityModel(
        name = "Author", packageName = "com.acme.shop.model", orm = OrmType.JPA,
        sourcePath = "src/main/java/com/acme/shop/model/Author.java", tableName = "authors",
        lombokAccessors = true, jakartaPersistence = true,
        fields = listOf(
            FieldModel("id", "Long", isId = true, nullable = false),
            FieldModel("name", "String", nullable = false, length = 80),
            FieldModel(
                "books", "List<Book>", imports = setOf("java.util.List"), relation = RelationKind.ONE_TO_MANY, inverse = true,
                relationTarget = RelationTarget("Book", "com.acme.shop.model.Book")
            )
        )
    )

    val book = EntityModel(
        name = "Book", packageName = "com.acme.shop.model", orm = OrmType.JPA,
        sourcePath = "src/main/java/com/acme/shop/model/Book.java", jakartaPersistence = true,
        fields = listOf(
            FieldModel("id", "Long", isId = true, nullable = false),
            FieldModel("title", "String", nullable = false, length = 100),
            FieldModel(
                "isbn", "String", validationAnnotations = listOf("@Size(min = 10, max = 13)"),
                validationImports = setOf("$VALID.Size")
            ),
            FieldModel("pages", "int", nullable = false),
            FieldModel("price", "BigDecimal", imports = setOf("java.math.BigDecimal")),
            FieldModel("published", "LocalDate", imports = setOf("java.time.LocalDate")),
            FieldModel("contactEmail", "String"),
            FieldModel(
                "status", "BookStatus", imports = setOf("com.acme.shop.model.BookStatus"), isEnum = true,
                enumFqn = "com.acme.shop.model.BookStatus", enumFirstConstant = "DRAFT",
                enumConstants = listOf("DRAFT", "PUBLISHED", "RETIRED")
            ),
            FieldModel(
                "author", "Author", nullable = false, imports = setOf("com.acme.shop.model.Author"),
                relation = RelationKind.MANY_TO_ONE,
                relationTarget = RelationTarget("Author", "com.acme.shop.model.Author")
            ),
            FieldModel(
                "reviews", "List<Review>", imports = setOf("java.util.List"), relation = RelationKind.ONE_TO_MANY, inverse = true,
                relationTarget = RelationTarget("Review", "com.acme.shop.model.Review")
            )
        )
    )

    val customer = EntityModel(
        name = "Customer", packageName = "com.acme.shop.pojo", orm = OrmType.MYBATIS,
        sourcePath = "src/main/java/com/acme/shop/pojo/Customer.java",
        fields = listOf(
            FieldModel("id", "Long", isId = true, nullable = false),
            FieldModel("name", "String"),
            FieldModel("age", "int", nullable = false),
            FieldModel("email", "String"),
            FieldModel("active", "boolean", nullable = false),
            FieldModel("registered", "LocalDate", imports = setOf("java.time.LocalDate"))
        )
    )

    /** An in-memory project containing the sample sources, so Merge/patch can be exercised end to end. */
    /** The same entities written against javax.* (Spring Boot 2). */
    fun javaxModel(e: EntityModel): EntityModel = e.copy(
        jakartaPersistence = e.jakartaPersistence?.let { false },
        fields = e.fields.map { f -> f.copy(validationImports = f.validationImports.map { it.replace("jakarta.", "javax.") }.toSet()) }
    )

    fun project(javax: Boolean = false): MemoryFiles {
        val out = MemoryFiles()
        fun load(name: String, pkg: String) {
            var text = SampleEntities::class.java.getResourceAsStream("/samples/$name.java")!!.readBytes().toString(Charsets.UTF_8)
            if (javax) text = text.replace("jakarta.", "javax.")
            out.write("src/main/java/${pkg.replace('.', '/')}/$name.java", text)
        }
        listOf("Author", "Book", "BookStatus", "Review").forEach { load(it, "com.acme.shop.model") }
        load("Customer", "com.acme.shop.pojo")
        return out
    }
}
