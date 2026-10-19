package damu.plugin.oneclickapi.eclipse.ui;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import damu.plugin.oneclickapi.core.write.GenerationReport;

/** What was created, merged, skipped or failed, as the same Markdown report the IntelliJ plugin shows. */
final class ReportDialog extends Dialog {

    private final GenerationReport report;

    ReportDialog(Shell parent, GenerationReport report) {
        super(parent);
        this.report = report;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("OneClickAPI - " + report.getSummary());
        shell.setSize(760, 520);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Text text = new Text(area, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL);
        text.setFont(JFaceResources.getTextFont());
        text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        text.setText(report.toMarkdown("OneClickAPI report"));
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.CLOSE_LABEL, true);
    }
}
