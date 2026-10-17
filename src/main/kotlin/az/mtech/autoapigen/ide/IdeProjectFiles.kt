package az.mtech.autoapigen.ide

import az.mtech.autoapigen.core.write.ProjectFiles
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * [ProjectFiles] on top of the virtual file system. Reads prefer the editor's document so that unsaved edits
 * are seen; writes must run inside a write action (the caller wraps the whole generation in one command, which
 * makes it a single undo step).
 */
class IdeProjectFiles(private val base: VirtualFile) : ProjectFiles {

    override fun read(path: String): String? {
        val file = base.findFileByRelativePath(path) ?: return null
        if (file.isDirectory) return null
        val document = FileDocumentManager.getInstance().getDocument(file)
        return document?.text ?: VfsUtilCore.loadText(file)
    }

    override fun write(path: String, content: String) {
        val existing = base.findFileByRelativePath(path)
        if (existing != null && !existing.isDirectory) {
            val document = FileDocumentManager.getInstance().getDocument(existing)
            if (document != null) document.setText(content) else VfsUtil.saveText(existing, content)
            return
        }
        val parentPath = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val dir = if (parentPath.isEmpty()) base else VfsUtil.createDirectoryIfMissing(base, parentPath)
            ?: throw IllegalStateException("Cannot create directory $parentPath")
        val file = dir.createChildData(this, name)
        VfsUtil.saveText(file, content)
    }
}
