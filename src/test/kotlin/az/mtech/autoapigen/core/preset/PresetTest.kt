package az.mtech.autoapigen.core.preset

import az.mtech.autoapigen.core.options.*
import az.mtech.autoapigen.core.write.MemoryFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetTest {

    @Test
    fun `yaml round trip keeps nesting lists and special characters`() {
        val doc = linkedMapOf<String, Any?>(
            "a" to "plain",
            "b" to linkedMapOf("c: d" to "x: y", "e" to listOf("one", "two words", "#three")),
            "empty" to emptyList<String>()
        )
        val parsed = MiniYaml.parse(MiniYaml.write(doc))
        assertEquals("plain", parsed["a"])
        @Suppress("UNCHECKED_CAST") val b = parsed["b"] as Map<String, Any?>
        assertEquals("x: y", b["c: d"])
        assertEquals(listOf("one", "two words", "#three"), b["e"])
    }

    @Test
    fun `yaml reader handles comments flow lists and same-indent lists`() {
        val parsed = MiniYaml.parse(
            """
            # comment
            version: 1   # trailing
            list: [a, "b c", d]
            items:
            - x
            - y
            nested:
              deep:
                key: "va#lue"
            """.trimIndent()
        )
        assertEquals("1", parsed["version"])
        assertEquals(listOf("a", "b c", "d"), parsed["list"])
        assertEquals(listOf("x", "y"), parsed["items"])
        @Suppress("UNCHECKED_CAST") val deep = (parsed["nested"] as Map<String, Any?>)["deep"] as Map<String, Any?>
        assertEquals("va#lue", deep["key"])
    }

    @Test(expected = MiniYaml.YamlException::class)
    fun `yaml reader rejects a line without a colon`() {
        MiniYaml.parse("just words")
    }

    @Test
    fun `codec applies only the keys present`() {
        val base = GenerationOptions("com.acme", lombok = false, standards = Standards(validation = true))
        val o = PresetCodec.apply(base, mapOf("lombok" to "true", "standards" to mapOf("pagination" to "true", "versioning" to "header")))
        assertTrue(o.lombok)
        assertTrue(o.standards.validation) // untouched
        assertTrue(o.standards.pagination)
        assertEquals(ApiVersioning.HEADER, o.standards.versioning)
        assertEquals("com.acme", o.basePackage)
    }

    @Test
    fun `codec reports bad values instead of throwing`() {
        val problems = mutableListOf<String>()
        val o = PresetCodec.apply(GenerationOptions("p"), mapOf("converter" to "Nope", "onConflict" to "x"), problems)
        assertEquals(2, problems.size)
        assertEquals(ConverterType.MAPSTRUCT, o.converter)
    }

    @Test
    fun `options survive a trip through a preset`() {
        val o = GenerationOptions(
            "com.acme", layout = PackageLayout(controller = "api"), lombok = true, converter = ConverterType.MANUAL,
            standards = Standards(pagination = true, softDelete = true, versioning = ApiVersioning.NONE, apiVersion = 2),
            tests = TestOptions(true, false, true), openApi = OpenApiOptions(spec = true), conflictPolicy = ConflictPolicy.MERGE
        )
        val yaml = MiniYaml.write(mapOf("x" to PresetCodec.toValues(o)))
        @Suppress("UNCHECKED_CAST") val values = MiniYaml.parse(yaml)["x"] as Map<String, Any?>
        val back = PresetCodec.apply(GenerationOptions("com.acme"), values)
        assertEquals(o, back)
    }

    @Test
    fun `store reads presets default and extends`() {
        val fs = MemoryFiles(
            mapOf(
                PresetStore.FILE_NAME to """
                version: 1
                defaultPreset: Team
                presets:
                  Base:
                    description: shared
                    lombok: true
                    standards:
                      validation: true
                  Team:
                    extends: Base
                    standards:
                      pagination: true
                """.trimIndent()
            )
        )
        val file = PresetStore(fs).load()
        assertTrue(file.problems.toString(), file.problems.isEmpty())
        assertEquals("Team", file.defaultPreset)
        val team = file.presets.first { it.name == "Team" }
        val o = PresetCodec.apply(GenerationOptions("p"), team.values)
        assertTrue(o.lombok)
        assertTrue(o.standards.validation)
        assertTrue(o.standards.pagination)
        assertEquals("shared", file.presets.first { it.name == "Base" }.description)
    }

    @Test
    fun `a file preset can extend a built in one`() {
        val fs = MemoryFiles(mapOf(PresetStore.FILE_NAME to "presets:\n  Mine:\n    extends: Enterprise API\n    lombok: false\n"))
        val mine = PresetStore(fs).load().presets.single()
        val o = PresetCodec.apply(GenerationOptions("p"), mine.values)
        assertFalse(o.lombok)
        assertTrue(o.standards.softDelete)
    }

    @Test
    fun `a broken file is reported not thrown`() {
        val file = PresetStore(MemoryFiles(mapOf(PresetStore.FILE_NAME to "presets:\n  - what\n  x"))).load()
        assertTrue(file.exists)
        assertTrue(file.problems.isNotEmpty())
        assertTrue(file.presets.isEmpty())
    }

    @Test
    fun `circular extends does not loop`() {
        val fs = MemoryFiles(mapOf(PresetStore.FILE_NAME to "presets:\n  A:\n    extends: B\n  B:\n    extends: A\n"))
        assertTrue(PresetStore(fs).load().problems.any { it.contains("circular") })
    }

    @Test
    fun `available lists file presets then the built ins that were not overridden`() {
        val fs = MemoryFiles(mapOf(PresetStore.FILE_NAME to "presets:\n  Clean Architecture:\n    lombok: false\n  Mine:\n    lombok: true\n"))
        val names = PresetStore(fs).available().map { it.name }
        assertEquals(listOf("Clean Architecture", "Mine", "Standard Spring Boot CRUD", "Enterprise API", "MyBatis Backend"), names)
    }

    @Test
    fun `save creates the file`() {
        val fs = MemoryFiles()
        PresetStore(fs).save("My Team", "Our rules", mapOf("lombok" to "true"), makeDefault = true)
        val text = fs.read(PresetStore.FILE_NAME)!!
        assertTrue(text, text.startsWith("version: 1\ndefaultPreset: My Team\npresets:\n  My Team:\n"))
        val file = PresetStore(fs).load()
        assertEquals("My Team", file.defaultPreset)
        assertEquals("Our rules", file.presets.single().description)
    }

    @Test
    fun `save replaces one preset and leaves comments and others alone`() {
        val original = """
            # team settings
            version: 1
            presets:
              # first
              A:
                lombok: true
                standards:
                  validation: true

              B:
                lombok: false
            other: keep
        """.trimIndent() + "\n"
        val fs = MemoryFiles(mapOf(PresetStore.FILE_NAME to original))
        val store = PresetStore(fs)
        store.save("A", "", mapOf("lombok" to "false"))
        val text = fs.read(PresetStore.FILE_NAME)!!
        assertTrue(text, text.contains("# team settings"))
        assertTrue(text, text.contains("  # first"))
        assertTrue(text, text.contains("other: keep"))
        val file = store.load()
        assertTrue(file.problems.toString(), file.problems.isEmpty())
        assertEquals("false", file.presets.first { it.name == "A" }.values["lombok"])
        assertFalse(text, text.contains("validation"))
        assertEquals("false", file.presets.first { it.name == "B" }.values["lombok"])

        store.save("C", "new", mapOf("lombok" to "true"))
        val after = store.load()
        assertEquals(listOf("A", "B", "C"), after.presets.map { it.name })
        assertTrue(fs.read(PresetStore.FILE_NAME)!!.contains("other: keep"))
        assertNotNull(after.presets.first { it.name == "C" })
    }

    @Test
    fun `built in presets only use keys the codec understands`() {
        for (p in BuiltInPresets.all) {
            val problems = mutableListOf<String>()
            PresetCodec.apply(GenerationOptions("p"), p.values, problems)
            assertTrue("${p.name}: $problems", problems.isEmpty())
        }
    }
}
