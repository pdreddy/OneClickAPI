package damu.plugin.oneclickapi.ide

import damu.plugin.oneclickapi.core.options.ConflictPolicy
import damu.plugin.oneclickapi.core.plan.FileKind
import damu.plugin.oneclickapi.core.plan.PlannedFile
import damu.plugin.oneclickapi.core.write.ConflictResolver
import damu.plugin.oneclickapi.core.write.Outcome
import damu.plugin.oneclickapi.core.write.PlanExecutor
import damu.plugin.oneclickapi.core.write.ResolvedFile
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

/**
 * "Preview Before Generate": lists every file the generator wants to write with its package, what will happen to
 * it, and a diff against what is on disk. Nothing is written until the user presses Generate; each file can be
 * unticked, and files that already exist can be set to Skip / Replace / Merge individually.
 */
class PreviewDialog(
    private val project: Project,
    files: List<PlannedFile>,
    private val executor: PlanExecutor,
    defaultPolicy: ConflictPolicy,
    private val warnings: List<String>
) : DialogWrapper(project, true) {

    private class Entry(val file: PlannedFile, var policy: ConflictPolicy, var include: Boolean, var resolved: ResolvedFile)

    private val entries: List<Entry> = files.map { f ->
        val policy = ConflictResolver.effectivePolicy(f, defaultPolicy)
        Entry(f, policy, true, executor.resolve(listOf(f)) { policy }.single())
    }

    private val columns = arrayOf("", "Action", "If exists", "File", "Package / folder")

    private val tableModel: AbstractTableModel = object : AbstractTableModel() {
        override fun getRowCount() = entries.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(column: Int) = columns[column]
        override fun getColumnClass(columnIndex: Int): Class<*> = if (columnIndex == 0) java.lang.Boolean::class.java else String::class.java
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = when (columnIndex) {
            0 -> true
            2 -> canChoosePolicy(entries[rowIndex])
            else -> false
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val e = entries[rowIndex]
            return when (columnIndex) {
                0 -> e.include
                1 -> actionLabel(e)
                2 -> if (canChoosePolicy(e)) e.policy.label else if (e.resolved.existing == null) "-" else e.policy.label + " (fixed)"
                3 -> e.file.fileName
                else -> e.file.packageName.ifEmpty { e.file.path.substringBeforeLast('/', "") }
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val e = entries[rowIndex]
            when (columnIndex) {
                0 -> e.include = aValue as? Boolean ?: true
                2 -> {
                    val chosen = ConflictPolicy.entries.firstOrNull { it.label == aValue } ?: return
                    e.policy = chosen
                    e.resolved = executor.resolve(listOf(e.file)) { chosen }.single()
                }
            }
            fireTableRowsUpdated(rowIndex, rowIndex)
            refreshSummary()
            showDiff(table.selectedRow)
        }
    }

    private val table: JBTable = JBTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        columnModel.getColumn(0).apply { minWidth = 28; maxWidth = 28 }
        columnModel.getColumn(1).preferredWidth = 90
        columnModel.getColumn(2).preferredWidth = 90
        columnModel.getColumn(2).cellEditor = DefaultCellEditor(JComboBox(ConflictPolicy.entries.map { it.label }.toTypedArray()))
        columnModel.getColumn(3).preferredWidth = 190
        columnModel.getColumn(4).preferredWidth = 260
        autoCreateRowSorter = false
    }

    private val summaryLabel = JBLabel()
    private val defaultPolicyBox = JComboBox(ConflictPolicy.entries.map { it.label }.toTypedArray()).apply {
        selectedItem = defaultPolicy.label
    }
    private val diffPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)

    init {
        title = "Preview Before Generate"
        setOKButtonText("Generate")
        init()
        table.selectionModel.addListSelectionListener { if (!it.valueIsAdjusting) showDiff(table.selectedRow) }
        refreshSummary()
        if (entries.isNotEmpty()) table.setRowSelectionInterval(0, 0)
    }

    override fun getDimensionServiceKey(): String = "damu.plugin.oneclickapi.PreviewDialog"

    private fun canChoosePolicy(e: Entry) = e.file.forcedPolicy == null && e.resolved.existing != null

    private fun actionLabel(e: Entry): String = when (e.resolved.resolution.outcome) {
        Outcome.CREATED -> "New file"
        Outcome.REPLACED -> "Replace"
        Outcome.MERGED -> if (e.file.kind == FileKind.ENTITY_PATCH) "Update entity" else "Merge"
        Outcome.SKIPPED -> "Skip"
        Outcome.UNCHANGED -> "No change"
        Outcome.FAILED -> "Failed"
    }

    private fun refreshSummary() {
        val counts = entries.filter { it.include }.groupingBy { it.resolved.resolution.outcome }.eachCount()
        val parts = Outcome.entries.mapNotNull { o -> counts[o]?.let { "$it ${o.label.lowercase()}" } }
        summaryLabel.text = parts.joinToString(", ").ifEmpty { "Nothing selected" }
        isOKActionEnabled = entries.any { it.include && it.resolved.resolution.writes }
    }

    private fun showDiff(row: Int) {
        if (row < 0 || row >= entries.size) return
        val e = entries[row]
        val factory = DiffContentFactory.getInstance()
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(e.file.fileName)
        val before = e.resolved.existing.orEmpty()
        val after = e.resolved.resolution.content ?: (e.resolved.existing ?: e.file.content)
        val note = e.resolved.resolution.detail.let { if (it.isNotEmpty()) " - $it" else "" }
        val request = SimpleDiffRequest(
            e.file.path + note,
            factory.create(project, before, fileType),
            factory.create(project, after, fileType),
            if (e.resolved.existing == null) "Does not exist yet" else "On disk",
            "After generating"
        )
        diffPanel.setRequest(request)
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 6))
        // Cap to the screen so the Generate/Cancel buttons at the bottom are never pushed off-screen on small displays.
        val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
        root.preferredSize = Dimension(minOf(1150, (screen.width * 0.8).toInt()), minOf(680, (screen.height * 0.6).toInt()))

        val top = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        top.add(JBLabel("Files that already exist:"))
        top.add(defaultPolicyBox)
        val apply = JButton("Apply to all")
        apply.addActionListener {
            val chosen = ConflictPolicy.entries.firstOrNull { it.label == defaultPolicyBox.selectedItem } ?: return@addActionListener
            entries.filter { canChoosePolicy(it) }.forEach {
                it.policy = chosen
                it.resolved = executor.resolve(listOf(it.file)) { chosen }.single()
            }
            tableModel.fireTableDataChanged()
            if (table.rowCount > 0 && table.selectedRow < 0) table.setRowSelectionInterval(0, 0)
            refreshSummary()
            showDiff(table.selectedRow)
        }
        top.add(apply)
        top.add(JBLabel("   "))
        top.add(summaryLabel)
        root.add(top, BorderLayout.NORTH)

        val splitter = OnePixelSplitter(false, 0.42f)
        splitter.firstComponent = JBScrollPane(table)
        splitter.secondComponent = diffPanel.component
        root.add(splitter, BorderLayout.CENTER)

        if (warnings.isNotEmpty()) {
            val text = "<html>" + warnings.joinToString("<br>") { "• " + it.replace("<", "&lt;") } + "</html>"
            root.add(JBLabel(text).apply { foreground = java.awt.Color(0xC7, 0x7F, 0x2D) }, BorderLayout.SOUTH)
        }
        return root
    }

    override fun getPreferredFocusedComponent(): JComponent = table

    // ---------------------------------------------------------------------------- results

    fun excludedPaths(): Set<String> = entries.filter { !it.include }.map { it.file.path }.toSet()

    fun policyFor(file: PlannedFile): ConflictPolicy =
        entries.firstOrNull { it.file.path == file.path }?.policy ?: ConflictPolicy.SKIP
}
