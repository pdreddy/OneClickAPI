package damu.plugin.oneclickapi.eclipse.ui;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** "Generate tests only": picks test types for classes whose code is already in the project. */
final class TestsOnlyDialog extends Dialog {

    private final String entityNames;
    private final String defaultPackage;

    private Text basePackage;
    private Button service;
    private Button controller;
    private Button mapper;
    private Button integration;
    private Button http;
    private Button preview;

    TestsOnlyDialog(Shell parent, String entityNames, String defaultPackage) {
        super(parent);
        this.entityNames = entityNames;
        this.defaultPackage = defaultPackage;
    }

    String basePackage() {
        return basePackage.getText().trim();
    }

    boolean service() {
        return service.getSelection();
    }

    boolean controller() {
        return controller.getSelection();
    }

    boolean mapper() {
        return mapper.getSelection();
    }

    boolean integration() {
        return integration.getSelection();
    }

    boolean http() {
        return http.getSelection();
    }

    boolean wantsPreview() {
        return preview.getSelection();
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Generate Tests Only (OneClickAPI)");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(2, false));

        Label info = new Label(area, SWT.WRAP);
        info.setText("Writes test classes only, for code that is already in the project. "
            + "Each test needs the class it tests; a missing one is reported in the preview.");
        GridData infoData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        infoData.widthHint = 520;
        info.setLayoutData(infoData);

        Label classes = new Label(area, SWT.NONE);
        classes.setText("Classes:");
        Label names = new Label(area, SWT.WRAP);
        names.setText(entityNames);
        names.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label pkgLabel = new Label(area, SWT.NONE);
        pkgLabel.setText("Base package:");
        basePackage = new Text(area, SWT.BORDER);
        basePackage.setText(defaultPackage);
        basePackage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Group group = new Group(area, SWT.NONE);
        group.setText("Test types");
        group.setLayout(new GridLayout(1, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
        service = check(group, "Service tests (JUnit 5 + Mockito)", true);
        controller = check(group, "Controller tests (MockMvc)", true);
        mapper = check(group, "Mapper tests", false);
        integration = check(group, "Full-stack API tests (H2, JPA entities only)", false);
        http = check(group, "HTTP request file", false);

        preview = new Button(area, SWT.CHECK);
        preview.setText("Preview files before writing");
        preview.setSelection(true);
        preview.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        return area;
    }

    private static Button check(Composite parent, String text, boolean selected) {
        Button b = new Button(parent, SWT.CHECK);
        b.setText(text);
        b.setSelection(selected);
        return b;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Generate", true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }
}
