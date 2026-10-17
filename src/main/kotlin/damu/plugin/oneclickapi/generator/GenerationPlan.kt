// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/** The original text includes unsaved editor changes, so the preview matches what the user sees. */
data class PlannedFile(
    val root: VirtualFile,
    val relativePath: String,
    val content: String,
    val originalContent: String?
) {
    val targetPath: String get() = "${root.path}/$relativePath"
    val isChanged: Boolean get() = content != originalContent
    val isNew: Boolean get() = originalContent == null

    init {
        require(relativePath.isNotBlank() && relativePath.split('/').all {
            it.isNotEmpty() && it != "." && it != ".." && '\\' !in it
        }) { "Invalid generated file path: $relativePath" }
    }

    companion object {
        fun capture(root: VirtualFile, relativePath: String, content: String): PlannedFile {
            val existing = root.findFileByRelativePath(relativePath)
            require(existing?.isDirectory != true) { "A directory occupies $relativePath." }
            return PlannedFile(root, relativePath, content.replace("\r\n", "\n").replace('\r', '\n'),
                existing?.let { currentText(it) })
        }

        internal fun currentText(file: VirtualFile): String =
            FileDocumentManager.getInstance().getDocument(file)?.text ?: VfsUtil.loadText(file)
    }
}

object GenerationPlan {
    /** Shared files are deduplicated; differing output at the same path must be resolved first. */
    fun merge(files: List<PlannedFile>): List<PlannedFile> = files.groupBy { it.targetPath }.map { (path, candidates) ->
        require(candidates.map { it.content }.distinct().size == 1) {
            "Different generated files target $path. Choose separate packages or generate these classes separately."
        }
        candidates.first()
    }.sortedBy { it.targetPath }

    fun apply(project: Project, files: List<PlannedFile>) {
        val selected = merge(files).filter { it.isChanged }
        if (selected.isEmpty()) return
        WriteCommandAction.writeCommandAction(project).withName("Generate API files").withGlobalUndo().run<RuntimeException> {
            // Check every target before the first mutation. Never overwrite edits made after preview.
            selected.forEach { planned ->
                check(planned.root.isValid && planned.root.isWritable) { "Source root is no longer writable: ${planned.root.path}" }
                var parent: VirtualFile? = planned.root
                planned.relativePath.split('/').dropLast(1).forEach { segment ->
                    val child = parent?.findChild(segment)
                    if (child != null) {
                        check(child.isDirectory && child.isWritable) { "Cannot write directory: ${child.path}" }
                    }
                    parent = child
                }
                val existing = planned.root.findFileByRelativePath(planned.relativePath)
                check(existing == null || (!existing.isDirectory && existing.isWritable)) { "Cannot write ${planned.targetPath}" }
                check(existing?.let { PlannedFile.currentText(it) } == planned.originalContent) {
                    "${planned.relativePath} changed after preview. Reopen Generate API to review the latest version."
                }
            }
            selected.forEach { planned ->
                val segments = planned.relativePath.split('/')
                var directory = planned.root
                segments.dropLast(1).forEach { segment ->
                    directory = directory.findChild(segment) ?: directory.createChildDirectory(this, segment)
                }
                val file = directory.findChild(segments.last()) ?: directory.createChildData(this, segments.last())
                val document = FileDocumentManager.getInstance().getDocument(file)
                if (document != null) document.setText(planned.content) else VfsUtil.saveText(file, planned.content)
            }
        }
    }
}
