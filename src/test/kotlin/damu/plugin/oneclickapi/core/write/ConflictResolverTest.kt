package damu.plugin.oneclickapi.core.write

import damu.plugin.oneclickapi.core.options.ConflictPolicy
import damu.plugin.oneclickapi.core.plan.FileKind
import damu.plugin.oneclickapi.core.plan.PlannedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolverTest {

    private fun java(content: String, forced: ConflictPolicy? = null, kind: FileKind = FileKind.SOURCE) =
        PlannedFile("src/main/java/p/A.java", content, kind, forcedPolicy = forced)

    private val generated = """
        package p;
        public class A {
            void a() {}
            // region custom code (kept when you regenerate)
            // endregion
        }
    """.trimIndent()

    @Test
    fun `new file is created whatever the policy`() {
        for (p in ConflictPolicy.entries) {
            val r = ConflictResolver.resolve(null, java(generated), p)
            assertEquals(Outcome.CREATED, r.outcome)
            assertEquals(generated, r.content)
        }
    }

    @Test
    fun `skip leaves an existing file alone`() {
        val r = ConflictResolver.resolve("class A {}", java(generated), ConflictPolicy.SKIP)
        assertEquals(Outcome.SKIPPED, r.outcome)
        assertNull(r.content)
    }

    @Test
    fun `identical file is unchanged under every policy`() {
        for (p in ConflictPolicy.entries) {
            assertEquals(Outcome.UNCHANGED, ConflictResolver.resolve(generated, java(generated), p).outcome)
        }
    }

    @Test
    fun `replace keeps custom code regions`() {
        val existing = """
            package p;
            public class A {
                void old() {}
                // region custom code (kept when you regenerate)
                void mine() { /* hand written */ }
                // endregion
            }
        """.trimIndent()
        val r = ConflictResolver.resolve(existing, java(generated), ConflictPolicy.REPLACE)
        assertEquals(Outcome.REPLACED, r.outcome)
        assertTrue(r.content!!.contains("void a() {}"))
        assertFalse(r.content!!.contains("void old()"))
        assertTrue(r.content!!.contains("void mine() { /* hand written */ }"))
        assertTrue(r.detail.contains("1 custom code region"))
    }

    @Test
    fun `named regions are matched by name`() {
        val gen = "x\n// region custom code [a]\n// endregion\n// region custom code [b]\n// endregion\n"
        val old = "// region custom code [b]\nB\n// endregion\n// region custom code [a]\nA\n// endregion\n"
        val out = CustomRegions.inject(gen, CustomRegions.extract(old))
        assertEquals("x\n// region custom code [a]\nA\n// endregion\n// region custom code [b]\nB\n// endregion\n", out)
    }

    @Test
    fun `unterminated region is ignored instead of swallowing the file`() {
        assertTrue(CustomRegions.extract("// region custom code\nfoo\nbar").isEmpty())
    }

    @Test
    fun `merge adds missing members`() {
        val existing = "package p;\npublic class A {\n    void other() {}\n}\n"
        val r = ConflictResolver.resolve(existing, java(generated), ConflictPolicy.MERGE)
        assertEquals(Outcome.MERGED, r.outcome)
        assertTrue(r.content!!.contains("void other() {}"))
        assertTrue(r.content!!.contains("void a() {}"))
    }

    @Test
    fun `forced policy wins over the global one`() {
        // entity patches may only be merged, even if the user chose Replace
        val existing = "package p;\npublic class A {\n    private int keep;\n}\n"
        val patch = java("package p;\npublic class A {\n    private boolean deleted;\n}\n", ConflictPolicy.MERGE, FileKind.ENTITY_PATCH)
        val r = ConflictResolver.resolve(existing, patch, ConflictPolicy.REPLACE)
        assertEquals(Outcome.MERGED, r.outcome)
        assertTrue(r.content!!.contains("int keep"))
        assertTrue(r.content!!.contains("boolean deleted"))
    }

    @Test
    fun `entity patch for a missing entity is skipped`() {
        val patch = java("package p;\npublic class A {}\n", ConflictPolicy.MERGE, FileKind.ENTITY_PATCH)
        assertEquals(Outcome.SKIPPED, ConflictResolver.resolve(null, patch, ConflictPolicy.MERGE).outcome)
    }

    @Test
    fun `merge of unsupported types keeps the file and says so`() {
        val f = PlannedFile("src/main/resources/x.xml", "<a/>", FileKind.RESOURCE)
        val r = ConflictResolver.resolve("<b/>", f, ConflictPolicy.MERGE)
        assertEquals(Outcome.SKIPPED, r.outcome)
        assertTrue(r.detail.contains("not supported"))
    }

    @Test
    fun `http files merge by request line`() {
        val old = "### Mine\nGET {{host}}/api/v1/book\n\n### Extra\nGET {{host}}/custom\n"
        val gen = "### List\nGET {{host}}/api/v1/book\n\n### Create\nPOST {{host}}/api/v1/book\nContent-Type: application/json\n\n{}\n"
        val f = PlannedFile("http-requests/Book.http", gen, FileKind.HTTP)
        val r = ConflictResolver.resolve(old, f, ConflictPolicy.MERGE)
        assertEquals(Outcome.MERGED, r.outcome)
        assertTrue(r.content!!.contains("### Mine"))
        assertTrue(r.content!!.contains("POST {{host}}/api/v1/book"))
        assertEquals(1, Regex("GET \\{\\{host}}/api/v1/book").findAll(r.content!!).count())
    }

    @Test
    fun `executor reports every outcome and isolates failures`() {
        val fs = object : ProjectFiles {
            val data = mutableMapOf("src/main/java/p/Old.java" to "class Old {}")
            override fun read(path: String) = data[path]
            override fun write(path: String, content: String) {
                if (path.endsWith("Boom.java")) error("disk full")
                data[path] = content
            }
        }
        fun f(name: String) = PlannedFile("src/main/java/p/$name.java", "package p;\nclass $name {}\n", FileKind.SOURCE)
        val report = PlanExecutor(fs).execute(
            listOf(f("New"), f("Old"), f("Boom"), f("Off")),
            { ConflictPolicy.SKIP },
            excluded = setOf("src/main/java/p/Off.java")
        )
        val by = report.entries.associateBy { it.path.substringAfterLast('/') }
        assertEquals(Outcome.CREATED, by["New.java"]!!.outcome)
        assertEquals(Outcome.SKIPPED, by["Old.java"]!!.outcome)
        assertEquals(Outcome.FAILED, by["Boom.java"]!!.outcome)
        assertEquals(Outcome.SKIPPED, by["Off.java"]!!.outcome)
        assertEquals("class Old {}", fs.data["src/main/java/p/Old.java"])
        assertTrue(report.toMarkdown().contains("## Failed (1)"))
    }
}
