package damu.plugin.oneclickapi.eclipse.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.jface.resource.JFaceResources;

import damu.plugin.oneclickapi.core.write.ResolvedFile;

/** Lists every file that would be written, with what happens to it; unticked files are left alone. */
final class PreviewDialog extends Dialog {

    private final List<ResolvedFile> files;
    private final List<String> warnings;
    private final Set<String> excluded = new HashSet<>();
    private Table table;
    private Text content;

    PreviewDialog(Shell parent, List<ResolvedFile> files, List<String> warnings) {
        super(parent);
        this.files = files;
        this.warnings = warnings;
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
    }

    Set<String> excludedPaths() {
        return excluded;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Preview - OneClickAPI");
        shell.setSize(1000, 700);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(1, false));

        if (!warnings.isEmpty()) {
            Label w = new Label(area, SWT.WRAP);
            w.setText("Warnings:\n - " + String.join("\n - ", warnings));
            GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
            gd.widthHint = 900;
            w.setLayoutData(gd);
        }

        SashForm sash = new SashForm(area, SWT.VERTICAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        table = new Table(sash, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        table.setHeaderVisible(true);
        for (String h : new String[] { "File", "Result", "Kind", "Detail" }) {
            new TableColumn(table, SWT.NONE).setText(h);
        }
        for (ResolvedFile r : files) {
            TableItem item = new TableItem(table, SWT.NONE);
            item.setText(new String[] {
                r.getFile().getPath(),
                r.getResolution().getOutcome().getLabel(),
                r.getFile().getKind().getLabel(),
                r.getResolution().getDetail(),
            });
            item.setData(r);
            item.setChecked(r.getResolution().getWrites());
            if (!r.getResolution().getWrites()) {
                item.setGrayed(true);
            }
        }
        for (TableColumn c : table.getColumns()) {
            c.pack();
        }

        content = new Text(sash, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL);
        Font mono = JFaceResources.getTextFont();
        content.setFont(mono);
        sash.setWeights(new int[] { 40, 60 });

        table.addListener(SWT.Selection, e -> {
            TableItem[] sel = table.getSelection();
            if (sel.length == 1) {
                ResolvedFile r = (ResolvedFile) sel[0].getData();
                String text = r.getResolution().getContent();
                content.setText(text != null ? text : "(nothing is written: " + r.getResolution().getDetail() + ")");
            }
        });
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Write files", true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void okPressed() {
        for (TableItem item : table.getItems()) {
            if (!item.getChecked()) {
                excluded.add(((ResolvedFile) item.getData()).getFile().getPath());
            }
        }
        super.okPressed();
    }
}
