// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi

import com.intellij.diff.DiffManager
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import damu.plugin.oneclickapi.generator.PlannedFile
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/** Nothing is written until the user explicitly applies the checked files. */
class GenerationPreviewDialog(private val project: Project, private val files: List<PlannedFile>) : DialogWrapper(project) {
    private val checkedFiles = files.filter { it.isNew && it.isChanged }.toMutableSet()
    private val root = DefaultMutableTreeNode("Generated files")
    private val tree = JTree(root)
    private val summary = JLabel()
    private val diffPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)

    init {
        title = "Preview generated API files"
        setOKButtonText("Apply selected files")
        files.forEach { file ->
            var node = root
            // Include module/source-root paths so similarly named files remain distinguishable.
            val displayPath = project.basePath?.let { file.targetPath.removePrefix("$it/") } ?: file.targetPath
            val parts = displayPath.split('/').filter { it.isNotEmpty() }
            parts.dropLast(1).forEach { segment ->
                val existing = (0 until node.childCount).map { node.getChildAt(it) as DefaultMutableTreeNode }
                    .find { it.userObject == segment }
                node = existing ?: DefaultMutableTreeNode(segment).also { node.add(it) }
            }
            node.add(DefaultMutableTreeNode(file))
        }
        tree.model = DefaultTreeModel(root)
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = object : DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(
                tree: JTree, value: Any, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
            ): java.awt.Component {
                val component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
                val file = (value as DefaultMutableTreeNode).userObject as? PlannedFile ?: return component
                return JCheckBox("${file.relativePath.substringAfterLast('/')}  [${status(file)}]", file in checkedFiles).apply {
                    isEnabled = file.isChanged
                    isOpaque = true
                    background = if (sel) backgroundSelectionColor else backgroundNonSelectionColor
                    foreground = if (sel) textSelectionColor else textNonSelectionColor
                    toolTipText = file.targetPath
                }
            }
        }
        tree.addTreeSelectionListener { showDiff() }
        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                val bounds = tree.getPathBounds(path) ?: return
                if (SwingUtilities.isLeftMouseButton(e) && e.x <= bounds.x + 24) toggle(path)
            }
        })
        tree.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleFile")
        tree.actionMap.put("toggleFile", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) { tree.selectionPath?.let { toggle(it) } }
        })
        init()
        for (row in 0 until tree.rowCount) tree.expandRow(row)
        // Expanding a node adds more rows, so expand until all levels are visible.
        var row = 0
        while (row < tree.rowCount) tree.expandRow(row++)
        files.firstOrNull()?.let { first ->
            val node = root.depthFirstEnumeration().asSequence()
                .filterIsInstance<DefaultMutableTreeNode>().first { it.userObject == first }
            tree.selectionPath = TreePath(node.path)
        }
        refreshSelection()
    }

    fun selectedFiles(): List<PlannedFile> = files.filter { it in checkedFiles }

    private fun status(file: PlannedFile): String = when {
        !file.isChanged -> "Unchanged"
        file.isNew -> "New"
        else -> "Replace — review before selecting"
    }

    private fun toggle(path: TreePath) {
        val file = (path.lastPathComponent as DefaultMutableTreeNode).userObject as? PlannedFile ?: return
        if (!file.isChanged) return
        if (!checkedFiles.add(file)) checkedFiles.remove(file)
        refreshSelection()
    }

    private fun refreshSelection() {
        val replacements = checkedFiles.count { !it.isNew }
        summary.text = "${checkedFiles.size} of ${files.size} files selected ($replacements replacements). Existing files are unchecked by default."
        setOKActionEnabled(checkedFiles.isNotEmpty())
        tree.repaint()
    }

    private fun showDiff() {
        val file = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? PlannedFile ?: return
        val factory = DiffContentFactory.getInstance()
        val type = FileTypeManager.getInstance().getFileTypeByFileName(file.relativePath.substringAfterLast('/'))
        val request = SimpleDiffRequest(file.relativePath,
            factory.create(project, file.originalContent.orEmpty(), type),
            factory.create(project, file.content, type),
            if (file.isNew) "Current (file does not exist)" else "Current (including unsaved edits)", "Generated")
        request.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true)
        diffPanel.setRequest(request)
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        preferredSize = Dimension(1100, 650)
        add(JLabel("Select files to create or replace. Click a file to compare; click its checkbox or press Space to select."), BorderLayout.NORTH)
        val left = JPanel(BorderLayout()).apply {
            val buttons = JPanel().apply {
                add(JButton("Select new").apply {
                    addActionListener { checkedFiles.clear(); checkedFiles.addAll(files.filter { it.isNew && it.isChanged }); refreshSelection() }
                })
                add(JButton("Clear selection").apply {
                    addActionListener { checkedFiles.clear(); refreshSelection() }
                })
            }
            add(buttons, BorderLayout.NORTH)
            add(JScrollPane(tree), BorderLayout.CENTER)
        }
        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, diffPanel.component).apply {
            resizeWeight = 0.35
            dividerLocation = 380
        }, BorderLayout.CENTER)
        add(summary, BorderLayout.SOUTH)
    }
}
