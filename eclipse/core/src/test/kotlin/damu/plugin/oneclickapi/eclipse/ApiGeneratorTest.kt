package damu.plugin.oneclickapi.eclipse

import damu.plugin.oneclickapi.core.model.OrmType
import damu.plugin.oneclickapi.core.write.MemoryFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiGeneratorTest {

    private fun ann(q: String, vararg attrs: Pair<String, Any?>) = RawAnnotation().apply {
        qualifiedName = q
        source = "@" + q.substringAfterLast('.')
        attributes = linkedMapOf(*attrs)
    }

    private fun field(n: String, t: String, vararg a: RawAnnotation, primitive: Boolean = false) = RawField().apply {
        name = n; type = t; this.primitive = primitive; annotations = a.toMutableList()
    }

    private fun book() = RawClass().apply {
        name = "Book"
        packageName = "com.acme.shop.entity"
        sourcePath = "src/main/java/com/acme/shop/entity/Book.java"
        annotations = mutableListOf(ann("jakarta.persistence.Entity"))
        fields = mutableListOf(
            field("id", "Long", ann("jakarta.persistence.Id"), ann("jakarta.persistence.GeneratedValue")),
            field("title", "String", ann("jakarta.persistence.Column", "length" to 120, "nullable" to false)),
            field("pages", "int", primitive = true)
        )
    }

    @Test
    fun `raw class becomes a JPA entity model`() {
        val m = ModelFactory.build(book())
        assertEquals(OrmType.JPA, m.orm)
        assertEquals(true, m.jakartaPersistence)
        assertEquals("id", m.idName)
        val title = m.fields.first { it.name == "title" }
        assertEquals(120, title.length)
        assertEquals(false, title.nullable)
        assertEquals(false, m.fields.first { it.name == "pages" }.nullable)
    }

    @Test
    fun `static and transient fields are dropped`() {
        val c = book().apply {
            fields.add(field("serial", "long").apply { isStatic = true })
            fields.add(field("cache", "String", ann("jakarta.persistence.Transient")))
        }
        assertEquals(listOf("id", "title", "pages"), ModelFactory.build(c).fields.map { it.name })
    }

    @Test
    fun `a plain class without Entity is a MyBatis POJO`() {
        val c = book().apply { annotations = mutableListOf() }
        assertEquals(OrmType.MYBATIS, ModelFactory.build(c).orm)
    }

    @Test
    fun `generator plans the CRUD files and writes them through ProjectFiles`() {
        val files = MemoryFiles()
        val generator = ApiGenerator(files, emptyMap(), emptyList(), null, emptyList())
        val options = generator.options("com.acme.shop", mapOf("onConflict" to "skip"))
        val prepared = generator.prepare(listOf(ModelFactory.build(book())), options)

        val paths = prepared.files.map { it.path }
        assertTrue(paths.any { it.endsWith("controller/BookController.java") })
        assertTrue(paths.any { it.endsWith("service/BookService.java") })
        assertTrue(paths.any { it.endsWith("repository/BookRepository.java") })

        val report = generator.execute(prepared, options, emptySet())
        assertTrue(report.written > 0)
        assertNotNull(files.read(paths.first { it.endsWith("BookController.java") }))
    }

    @Test
    fun `refine applies dialog overrides on top of a preset`() {
        val generator = ApiGenerator(MemoryFiles(), emptyMap(), emptyList(), null, emptyList())
        val preset = generator.presets().first()
        val base = generator.options("com.acme", preset.values)
        val refined = generator.refine(base, mapOf("standards" to mapOf("pagination" to "true")))
        assertTrue(refined.standards.pagination)
    }
}
