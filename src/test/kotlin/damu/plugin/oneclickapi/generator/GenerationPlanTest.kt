package damu.plugin.oneclickapi.generator

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import damu.plugin.oneclickapi.GenerationPreviewDialog

class GenerationPlanTest : BasePlatformTestCase() {
    fun testPreviewSelectsOnlyNewFilesAndCancelDoesNotWrite() {
        val existing = myFixture.addFileToProject("Existing.java", "class Existing {}").virtualFile
        val replacement = PlannedFile.capture(existing.parent, existing.name, "class Existing { int generated; }")
        val newFile = PlannedFile.capture(existing.parent, "new/Other.java", "class Other {}")
        val unchanged = PlannedFile.capture(existing.parent, existing.name, "class Existing {}")
        val dialog = GenerationPreviewDialog(project, listOf(replacement, newFile, unchanged))
        try {
            assertEquals(listOf(newFile), dialog.selectedFiles())
        } finally {
            dialog.doCancelAction()
        }
        assertNull(existing.parent.findChild("new"))
        assertEquals("class Existing {}", PlannedFile.currentText(existing))
    }

    fun testPreviewDoesNotCreateFilesAndApplyingOnlySelectedFileWorks() {
        val root = myFixture.tempDirFixture.getFile(".")!!
        val first = PlannedFile.capture(root, "generated/First.java", "class First {}")
        val second = PlannedFile.capture(root, "generated/Second.java", "class Second {}")
        assertNull(root.findChild("generated"))
        GenerationPlan.apply(project, listOf(second))
        assertNull(root.findFileByRelativePath(first.relativePath))
        assertEquals(second.content, PlannedFile.currentText(root.findFileByRelativePath(second.relativePath)!!))
    }

    fun testExistingUnsavedTextIsIncludedAndReplacementIsExplicit() {
        val file = myFixture.addFileToProject("Existing.java", "class Existing {}").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("class Existing { int custom; }") }
        val plan = PlannedFile.capture(file.parent, file.name, "class Existing { int generated; }")
        assertEquals("class Existing { int custom; }", plan.originalContent)
        GenerationPlan.apply(project, emptyList())
        assertEquals(plan.originalContent, document.text)
        GenerationPlan.apply(project, listOf(plan))
        assertEquals(plan.content, document.text)
    }

    fun testStalePreviewRejectsEntireSelectionBeforeCreatingFiles() {
        val file = myFixture.addFileToProject("Existing.java", "class Existing {}").virtualFile
        val existing = PlannedFile.capture(file.parent, file.name, "class Existing { int generated; }")
        val newFile = PlannedFile.capture(file.parent, "new/Other.java", "class Other {}")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("class Existing { int newerEdit; }") }
        try {
            GenerationPlan.apply(project, listOf(newFile, existing))
            fail("Expected stale-preview rejection")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("changed after preview"))
        }
        assertNull(file.parent.findChild("new"))
        assertEquals("class Existing { int newerEdit; }", document.text)
    }

    fun testNewFileAppearingAfterPreviewIsNotOverwritten() {
        val root = myFixture.tempDirFixture.getFile(".")!!
        val plan = PlannedFile.capture(root, "Other.java", "class Other {}")
        myFixture.addFileToProject("Other.java", "class Other { int userEdit; }")
        try {
            GenerationPlan.apply(project, listOf(plan))
            fail("Expected collision rejection")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("changed after preview"))
        }
    }

    fun testMergeDeduplicatesSharedFilesAndRejectsConflictingOutput() {
        val root = myFixture.tempDirFixture.getFile(".")!!
        val file = PlannedFile.capture(root, "Shared.java", "class Shared {}")
        assertEquals(listOf(file), GenerationPlan.merge(listOf(file, file)))
        try {
            GenerationPlan.merge(listOf(file, file.copy(content = "different")))
            fail("Expected conflicting-output rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Different generated files"))
        }
    }

    fun testRejectsTraversalPaths() {
        val root = myFixture.tempDirFixture.getFile(".")!!
        for (path in listOf("../escape.java", "/absolute.java", "a//file.java", "a/./file.java", "a\\file.java")) {
            try {
                PlannedFile(root, path, "", null)
                fail("Expected invalid-path rejection for $path")
            } catch (_: IllegalArgumentException) { }
        }
    }

    fun testReplacementCanBeUndoneInOneCommand() {
        val first = myFixture.addFileToProject("First.java", "class First {}").virtualFile
        val second = myFixture.addFileToProject("Second.java", "class Second {}").virtualFile
        FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, first), true)
        val plans = listOf(first, second).map { PlannedFile.capture(it.parent, it.name, "// generated\n${PlannedFile.currentText(it)}") }
        GenerationPlan.apply(project, plans)
        val editor = FileEditorManager.getInstance(project).selectedEditor
        assertTrue(UndoManager.getInstance(project).isUndoAvailable(editor))
        UndoManager.getInstance(project).undo(editor)
        plans.forEach { assertEquals(it.originalContent, PlannedFile.currentText(it.root.findFileByRelativePath(it.relativePath)!!)) }
    }

    fun testCreatedFilesAndDirectoriesCanBeUndoneTogether() {
        val context = myFixture.addFileToProject("Context.java", "class Context {}").virtualFile
        val root = context.parent
        FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, context), true)
        GenerationPlan.apply(project, listOf(
            PlannedFile.capture(root, "generated/First.java", "class First {}"),
            PlannedFile.capture(root, "generated/Second.java", "class Second {}")
        ))
        assertNotNull(root.findFileByRelativePath("generated/Second.java"))
        val editor = FileEditorManager.getInstance(project).selectedEditor
        assertTrue(UndoManager.getInstance(project).isUndoAvailable(editor))
        UndoManager.getInstance(project).undo(editor)
        assertNull(root.findChild("generated"))
    }

    fun testJpaPlanningDoesNotRequireResourcesOrCreatePackages() {
        val entity = myFixture.addFileToProject("src/main/java/example/Book.java", "package example; public class Book { private Long id; private String title; }") as PsiJavaFile
        val root = myFixture.tempDirFixture.getFile("src/main/java")!!
        PsiTestUtil.addSourceRoot(module, root)
        val plan = ClassGenerator.planApi(entity.classes.single(), "example.api", "JPA Repository", "MapStruct", false,
            false, "", "", null, false, false, false, "JPA/Hibernate")
        assertEquals(6, plan.size)
        assertTrue(plan.all { it.isNew })
        assertNull(root.findFileByRelativePath("example/api"))
        assertTrue(plan.first { it.relativePath.endsWith("BookController.java") }.content.contains("/api/v1/book"))
        GenerationPlan.apply(project, listOf(plan.first()))
        assertNotNull(root.findFileByRelativePath(plan.first().relativePath))
        assertNull(root.findFileByRelativePath(plan.last().relativePath))
    }

    fun testMyBatisUploadsAndLiquibaseAreIncludedWithoutWriting() {
        val entity = myFixture.addFileToProject("src/main/java/example/Book.java", "package example; public class Book { private Long id; private String title; }") as PsiJavaFile
        val javaRoot = myFixture.tempDirFixture.getFile("src/main/java")!!
        val resources = myFixture.tempDirFixture.findOrCreateDir("src/main/resources")
        PsiTestUtil.addSourceRoot(module, javaRoot)
        PsiTestUtil.addSourceRoot(module, resources)
        val plan = ClassGenerator.planApi(entity.classes.single(), "example.api", "JPA Repository", "MapStruct", true,
            true, "db/changelog", "book", "yaml", true, true, true, "MyBatis")
        assertEquals(14, plan.size)
        assertTrue(plan.any { it.relativePath == "mybatis/mappers/BookMapper.xml" })
        assertTrue(plan.any { it.relativePath == "db/changelog/book-01.yaml" })
        assertTrue(plan.any { it.relativePath.endsWith("GlobalExceptionHandler.java") })
        assertTrue(plan.any { it.relativePath.endsWith("FileDto.java") && it.content.contains("package example.api.dto;") })
        assertTrue(plan.any { it.relativePath.endsWith("PdfUploadController.java") && it.content.contains("package example.api.controller;") })
        assertNull(javaRoot.findFileByRelativePath("example/api"))
        assertTrue(resources.children.isEmpty())
    }
}
