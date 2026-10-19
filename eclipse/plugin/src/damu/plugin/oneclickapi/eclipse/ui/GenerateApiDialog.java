package damu.plugin.oneclickapi.eclipse.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import damu.plugin.oneclickapi.core.options.GenerationOptions;
import damu.plugin.oneclickapi.core.preset.Preset;
import damu.plugin.oneclickapi.eclipse.ApiGenerator;

/** Options dialog: a preset sets the starting point, the check boxes below refine it. */
final class GenerateApiDialog extends Dialog {

    /** One check box: label, path of the {@code .oneclickapi.yml} key (a/b = key b inside map a). */
    private record Flag(String label, String key) {
    }

    private static final Flag[] GENERAL = {
        new Flag("Lombok on generated classes", "lombok"),
        new Flag("Exception handler", "exceptionHandling"),
    };
    private static final Flag[] STANDARDS = {
        new Flag("Bean Validation constraints", "standards/validation"),
        new Flag("Pagination, filter and search", "standards/pagination"),
        new Flag("Standard response envelope (ApiResult / ApiError)", "standards/standardResponse"),
        new Flag("Soft delete", "standards/softDelete"),
        new Flag("Audit fields (createdAt / updatedAt)", "standards/auditFields"),
        new Flag("Expose relations as ids in DTOs", "standards/relationDtos"),
    };
    private static final Flag[] TESTS = {
        new Flag("Service tests", "tests/service"),
        new Flag("Controller tests (MockMvc)", "tests/controller"),
        new Flag("Mapper tests", "tests/mapper"),
        new Flag("Full-stack API tests (H2)", "tests/integration"),
        new Flag("HTTP request file", "tests/http"),
        new Flag("JaCoCo coverage report", "tests/coverage"),
    };
    private static final Flag[] OPENAPI = {
        new Flag("Springdoc annotations", "openApi/annotations"),
        new Flag("OpenAPI YAML per entity", "openApi/spec"),
        new Flag("OpenApiConfig bean", "openApi/config"),
    };

    private final ApiGenerator generator;
    private final List<Preset> presets;
    private final String entityNames;
    private final String defaultPackage;
    private final Map<String, Button> boxes = new LinkedHashMap<>();

    private Combo presetCombo;
    private Label presetInfo;
    private Text basePackage;
    private Combo repository;
    private Combo converter;
    private Combo conflict;
    private Button preview;

    private GenerationOptions result;
    private boolean previewChosen = true;

    GenerateApiDialog(Shell parent, ApiGenerator generator, String entityNames, String defaultPackage) {
        super(parent);
        this.generator = generator;
        this.presets = generator.presets();
        this.entityNames = entityNames;
        this.defaultPackage = defaultPackage;
    }

    GenerationOptions options() {
        return result;
    }

    boolean wantsPreview() {
        return previewChosen;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Generate API (OneClickAPI)");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(2, false));

        label(area, "Classes:");
        Label names = new Label(area, SWT.WRAP);
        names.setText(entityNames);
        names.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        label(area, "Preset:");
        presetCombo = new Combo(area, SWT.READ_ONLY | SWT.DROP_DOWN);
        for (Preset p : presets) {
            presetCombo.add(p.getName());
        }
        presetCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        presetInfo = new Label(area, SWT.WRAP);
        GridData info = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        info.widthHint = 520;
        presetInfo.setLayoutData(info);

        label(area, "Base package:");
        basePackage = new Text(area, SWT.BORDER);
        basePackage.setText(defaultPackage);
        basePackage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        label(area, "Repository:");
        repository = new Combo(area, SWT.READ_ONLY | SWT.DROP_DOWN);
        repository.setItems("JPA Repository", "CRUD Repository");
        repository.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        label(area, "Converter:");
        converter = new Combo(area, SWT.READ_ONLY | SWT.DROP_DOWN);
        converter.setItems("MapStruct", "Manual Conversion");
        converter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        label(area, "Existing files:");
        conflict = new Combo(area, SWT.READ_ONLY | SWT.DROP_DOWN);
        conflict.setItems("skip", "replace", "merge");
        conflict.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        group(area, "General", GENERAL);
        group(area, "API standards", STANDARDS);
        group(area, "Tests", TESTS);
        group(area, "OpenAPI", OPENAPI);

        preview = new Button(area, SWT.CHECK);
        preview.setText("Preview files before writing");
        preview.setSelection(true);
        preview.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));

        presetCombo.addListener(SWT.Selection, e -> loadPreset());
        presetCombo.select(0);
        loadPreset();
        return area;
    }

    private void loadPreset() {
        Preset preset = presets.get(presetCombo.getSelectionIndex());
        presetInfo.setText(preset.getDescription());
        GenerationOptions o = generator.options(basePackage.getText().trim(), preset.getValues());
        Map<String, Object> values = asMap(generator.values(o));
        boxes.forEach((key, box) -> box.setSelection("true".equals(lookup(values, key))));
        select(repository, String.valueOf(values.get("repository")));
        select(converter, String.valueOf(values.get("converter")));
        select(conflict, String.valueOf(values.get("onConflict")));
        presetInfo.getParent().layout(true, true);
    }

    @Override
    protected void okPressed() {
        String pkg = basePackage.getText().trim();
        Preset preset = presets.get(presetCombo.getSelectionIndex());
        GenerationOptions o = generator.options(pkg, preset.getValues());

        Map<String, Object> overrides = new LinkedHashMap<>();
        boxes.forEach((key, box) -> put(overrides, key, Boolean.toString(box.getSelection())));
        overrides.put("repository", repository.getText());
        overrides.put("converter", converter.getText());
        overrides.put("onConflict", conflict.getText());
        result = generator.refine(o, overrides);
        previewChosen = preview.getSelection();
        super.okPressed();
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Generate", true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    // ------------------------------------------------------------------------------------ helpers

    private void group(Composite area, String title, Flag[] flags) {
        Group g = new Group(area, SWT.NONE);
        g.setText(title);
        g.setLayout(new GridLayout(2, true));
        g.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
        for (Flag f : flags) {
            Button b = new Button(g, SWT.CHECK);
            b.setText(f.label());
            boxes.put(f.key(), b);
        }
    }

    private static void label(Composite parent, String text) {
        Label l = new Label(parent, SWT.NONE);
        l.setText(text);
    }

    private static void select(Combo combo, String value) {
        int i = combo.indexOf(value);
        combo.select(i >= 0 ? i : 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Map<String, ?> m) {
        return (Map<String, Object>) m;
    }

    @SuppressWarnings("unchecked")
    private static Object lookup(Map<String, Object> values, String key) {
        String[] parts = key.split("/");
        Object cur = values;
        for (String p : parts) {
            if (!(cur instanceof Map)) {
                return null;
            }
            cur = ((Map<String, Object>) cur).get(p);
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> target, String key, Object value) {
        String[] parts = key.split("/");
        Map<String, Object> cur = target;
        for (int i = 0; i < parts.length - 1; i++) {
            cur = (Map<String, Object>) cur.computeIfAbsent(parts[i], k -> new LinkedHashMap<String, Object>());
        }
        cur.put(parts[parts.length - 1], value);
    }
}
