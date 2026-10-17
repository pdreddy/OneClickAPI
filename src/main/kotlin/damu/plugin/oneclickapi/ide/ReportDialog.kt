package damu.plugin.oneclickapi.ide

import damu.plugin.oneclickapi.core.write.GenerationReport
import damu.plugin.oneclickapi.core.write.Outcome
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

/** Shows what a generation run did, file by file. */
class ReportDialog(project: Project, private val report: GenerationReport) : DialogWrapper(project, false) {

    init {
        title = "OneClickAPI - " + (if (report.count(Outcome.FAILED) > 0) "Finished with errors" else "Report")
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val area = JBTextArea(report.toMarkdown()).apply {
            isEditable = false
            lineWrap = false
            caretPosition = 0
        }
        return JBScrollPane(area).apply { preferredSize = Dimension(760, 460) }
    }

    override fun createActions(): Array<Action> {
        val copy = object : AbstractAction("Copy as Markdown") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                CopyPasteManager.getInstance().setContents(StringSelection(report.toMarkdown()))
            }
        }
        return arrayOf(copy, okAction)
    }
}
