package damu.plugin.oneclickapi.core.write

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaMergerTest {

    private val existing = """
        package com.acme.service;

        import java.util.List;
        import org.slf4j.Logger;

        /** Hand-edited. */
        @Service
        public class BookService {

            private static final String NAME = "a { b";
            private final BookRepository repository;

            public BookService(BookRepository repository) {
                this.repository = repository;
            }

            public List<BookResponse> findAll() {
                // user changed this on purpose; } unbalanced in a comment
                return List.of();
            }

            // region custom code (kept)
            void mine() {}
            // endregion
        }
    """.trimIndent()

    private val generated = """
        package com.acme.service;

        import java.util.List;
        import java.util.Optional;
        import java.util.stream.Collectors;

        @Service
        public class BookService {

            private static final String NAME = "other";
            private final BookRepository repository;
            private final BookMapper mapper;

            public BookService(BookRepository repository, BookMapper mapper) {
                this.repository = repository;
                this.mapper = mapper;
            }

            public List<BookResponse> findAll() {
                return repository.findAll().stream().map(mapper::toResponse).collect(Collectors.toList());
            }

            public Optional<BookResponse> findById(Long id) {
                return repository.findById(id).map(mapper::toResponse);
            }

            public void delete(Long id) {
                repository.deleteById(id);
            }
        }
    """.trimIndent()

    @Test
    fun `adds only missing members and keeps edited ones`() {
        val r = JavaMerger.merge(existing, generated)
        assertNull(r.problem)
        // user's version of findAll and the constructor are untouched
        assertTrue(r.content.contains("// user changed this on purpose"))
        assertFalse(r.content.contains("repository.findAll().stream()"))
        // the generated constructor is not added next to the user's own, but the user is told
        assertFalse(r.content.contains("BookMapper mapper) {"))
        assertEquals(1, r.notes.size)
        assertTrue(r.notes[0].contains("constructor"))
        // new members are present
        assertTrue(r.content.contains("findById(Long id)"))
        assertTrue(r.content.contains("void delete(Long id)"))
        // new field with a different name is added, the existing NAME constant is not duplicated
        assertTrue(r.content.contains("private final BookMapper mapper;"))
        assertEquals(1, Regex("NAME =").findAll(r.content).count())
        // imports needed by added code are added, unused ones are not
        assertTrue(r.content.contains("import java.util.Optional;"))
        assertFalse(r.content.contains("import java.util.stream.Collectors;"))
        // inserted before the custom region, region intact
        assertTrue(r.content.indexOf("findById") < r.content.indexOf("region custom code"))
        assertTrue(r.content.contains("void mine() {}"))
    }

    @Test
    fun `merge is idempotent`() {
        val once = JavaMerger.merge(existing, generated).content
        val twice = JavaMerger.merge(once, generated)
        assertFalse(twice.changed)
        assertEquals(once, twice.content)
    }

    @Test
    fun `overloads are different members`() {
        val old = "class A { void f(String s) {} }"
        val gen = "class A { void f(String s) {} void f(int i) {} void f(java.util.List<String> l) {} }"
        val r = JavaMerger.merge(old, gen)
        assertEquals(listOf("f(int)", "f(List<String>)"), r.addedMembers)
    }

    @Test
    fun `qualified and simple parameter types match`() {
        val old = "class A { void f(java.util.List<String> l) {} }"
        val gen = "class A { void f(List<String> items) {} }"
        assertFalse(JavaMerger.merge(old, gen).changed)
    }

    @Test
    fun `field with initializer containing braces is one member`() {
        val old = "class A { int x; }"
        val gen = "class A { private final Map<String, Integer> m = new HashMap<>() {{ put(\"a\", 1); }}; int x; void g() {} }"
        val r = JavaMerger.merge(old, gen)
        assertEquals(listOf("field m", "g()"), r.addedMembers)
    }

    @Test
    fun `entity patch adds fields to a class without touching annotations`() {
        val entity = """
            package com.acme.model;

            import jakarta.persistence.*;

            @Entity
            @Table(name = "book")
            public class Book {
                @Id
                @GeneratedValue
                private Long id;
                private String title;
            }
        """.trimIndent()
        val patch = """
            package com.acme.model;

            import java.time.LocalDateTime;
            import org.hibernate.annotations.CreationTimestamp;

            public class Book {
                @CreationTimestamp
                @Column(updatable = false)
                private LocalDateTime createdAt;

                private boolean deleted;

                public boolean isDeleted() { return deleted; }
            }
        """.trimIndent()
        val r = JavaMerger.merge(entity, patch)
        assertTrue(r.content.contains("@CreationTimestamp"))
        assertTrue(r.content.contains("import org.hibernate.annotations.CreationTimestamp;"))
        assertTrue(r.content.contains("import java.time.LocalDateTime;"))
        assertTrue(r.content.contains("@Table(name = \"book\")"))
        assertTrue(r.content.trimEnd().endsWith("}"))
    }

    @Test
    fun `no class body is reported not thrown`() {
        val r = JavaMerger.merge("// nothing here", generated)
        assertNotNull(r.problem)
    }

    @Test
    fun `interface members merge too`() {
        val old = "package p;\npublic interface R extends JpaRepository<Book, Long> {\n    List<Book> findByTitle(String t);\n}\n"
        val gen = "package p;\npublic interface R extends JpaRepository<Book, Long> {\n    List<Book> findByTitle(String t);\n    long countByTitle(String t);\n}\n"
        val r = JavaMerger.merge(old, gen)
        assertEquals(listOf("countByTitle(String)"), r.addedMembers)
    }
}
