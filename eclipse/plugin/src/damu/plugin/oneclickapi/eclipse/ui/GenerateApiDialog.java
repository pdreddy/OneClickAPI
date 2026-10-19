package damu.plugin.oneclickapi.eclipse.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
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
import damu.plugin.oneclickapi.core.options.LiquibaseFormat;
import damu.plugin.oneclickapi.core.options.LiquibaseOptions;
import damu.plugin.oneclickapi.core.preset.Preset;
import damu.plugin.oneclickapi.eclipse.ApiGenerator;

/** Options dialog: a preset sets the starting point, the controls below refine it. Same options as the IntelliJ dialog. */
final class GenerateApiDialog extends Dialog {

    /** One check box: label, and the {@code .oneclickapi.yml} key (a/b = key b inside map a). */
    private record Flag(String label, String key) {
    }

    private static final Flag[] GENERAL = {
        new Flag("Lombok on generated classes", "lombok"),
        new Flag("Global exception handling", "exceptionHandling"),
    };
    private static final Flag[] STANDARDS = {
        new Flag("Bean Validation constraints", "standards/validation"),
        new Flag("Pagination, sorting, filtering and search", "standards/pagination"),
        new Flag("Standard response envelope (ApiResult / ApiError)", "standards/standardResponse"),
        new Flag("Soft delete (adds a 'deleted' field to the entity)", "standards/softDelete"),
        new Flag("Audit fields (createdAt / updatedAt)", "standards/auditFields"),
        new Flag("Expose relations as ids (no circular DTOs)", "standards/relationDtos"),
    };
    private static final Flag[] TESTS = {
        new Flag("Service tests (JUnit 5 + Mockito)", "tests/service"),
        new Flag("Controller tests (JUnit 5 + MockMvc)", "tests/controller"),
        new Flag("Mapper tests (entity <-> DTO, relation ids)", "tests/mapper"),
        new Flag("Full-stack API tests (H2, JPA entities only)", "tests/integration"),
        new Flag("HTTP request file", "tests/http"),
        new Flag("JaCoCo coverage report", "tests/coverage"),
    };
    private static final Flag[] OPENAPI = {
        new Flag("Springdoc / OpenAPI annotations", "openApi/annotations"),
        new Flag("OpenAPI 3 spec file (YAML)", "openApi/spec"),
        new Flag("OpenApiConfig bean (title, version)", "openApi/config"),
    };

    private final ApiGenerator generator;
    private List<Preset> presets;
    private final List<String> classNames;
    private final String defaultPackage;
    private final Map<String, Button> boxes = new LinkedHashMap<>();

    private Combo presetCombo;
    private Label presetInfo;
    private Text basePackage;
    private Combo repository;
    private Combo converter;
    private Combo conflict;
    private Button useLiquibase;
    private Text liquibasePath;
    private Text liquibaseFileName;
    private Combo liquibaseFormat;
    private Button imageUpload;
    private Button pdfUpload;
    private Button preview;

    private GenerationOptions result;
    private boolean previewChosen = true;

    GenerateApiDialog(Shell parent, ApiGenerator generator, List<String> classNames, String defaultPackage) {
        super(parent);
        this.generator = generator;
        this.presets = generator.presets();
        this.classNames = classNames;
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
        names.setText(String.join(", ", classNames));
        names.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        label(area, "Preset:");
        presetCombo = new Combo(area, SWT.READ_ONLY | SWT.DROP_DOWN);
        fillPresets();
        presetCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        presetInfo = new Label(area, SWT.WRAP);
        GridData info = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        info.widthHint = 520;
        presetInfo.setLayoutData(info);

        Button save = new Button(area, SWT.PUSH);
        save.setText("Save as Preset...");
        save.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        save.addListener(SWT.Selection, e -> saveAsPreset());

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
        liquibaseGroup(area);
        uploadGroup(area);

        preview = new Button(area, SWT.CHECK);
        preview.setText("Preview files before writing");
        preview.setSelection(true);
        preview.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));

        presetCombo.addListener(SWT.Selection, e -> loadPreset());
        presetCombo.select(0);
        loadPreset();
        return area;
    }

    private void fillPresets() {
        presetCombo.removeAll();
        for (Preset p : presets) {
            presetCombo.add(p.getName());
        }
    }

    private void liquibaseGroup(Composite area) {
        Group g = new Group(area, SWT.NONE);
        g.setText("Liquibase");
        g.setLayout(new GridLayout(2, false));
        g.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

        useLiquibase = new Button(g, SWT.CHECK);
        useLiquibase.setText("Generate Liquibase changelog");
        useLiquibase.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        useLiquibase.addListener(SWT.Selection, e -> updateLiquibaseEnabled());

        label(g, "Package path:");
        liquibasePath = new Text(g, SWT.BORDER);
        liquibasePath.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        label(g, "File name:");
        liquibaseFileName = new Text(g, SWT.BORDER);
        liquibaseFileName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        label(g, "Format:");
        liquibaseFormat = new Combo(g, SWT.READ_ONLY | SWT.DROP_DOWN);
        liquibaseFormat.setItems("XML", "YAML");
        liquibaseFormat.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void uploadGroup(Composite area) {
        Group g = new Group(area, SWT.NONE);
        g.setText("Uploads");
        g.setLayout(new GridLayout(1, false));
        g.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
        imageUpload = new Button(g, SWT.CHECK);
        imageUpload.setText("Image upload API");
        pdfUpload = new Button(g, SWT.CHECK);
        pdfUpload.setText("PDF upload API");
    }

    private void updateLiquibaseEnabled() {
        boolean on = useLiquibase.getSelection();
        liquibasePath.setEnabled(on);
        liquibaseFormat.setEnabled(on);
        // A file name only makes sense for a single class; several classes get one changelog each.
        liquibaseFileName.setEnabled(on && classNames.size() == 1);
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

        LiquibaseOptions lb = o.getLiquibase();
        useLiquibase.setSelection(lb != null);
        liquibasePath.setText(lb != null ? lb.getPackagePath() : "db/changelog/" + LocalDateTime.now().getYear());
        liquibaseFileName.setText(lb != null && !lb.getFileName().isEmpty() ? lb.getFileName() : defaultFileName());
        select(liquibaseFormat, lb != null && lb.getFormat() == LiquibaseFormat.YAML ? "YAML" : "XML");
        updateLiquibaseEnabled();

        imageUpload.setSelection(o.getImageUpload());
        pdfUpload.setSelection(o.getPdfUpload());
    }

    /** The changelog name for a single class, as the IntelliJ dialog suggests it: timestamp plus the class name. */
    private String defaultFileName() {
        if (classNames.size() != 1) {
            return "";
        }
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String description = classNames.get(0).toLowerCase().replace("entity", "").trim();
        return timestamp + "_" + description;
    }

    /** The options as the dialog currently shows them, or null if a field is not valid. */
    private GenerationOptions currentOptions() {
        if (useLiquibase.getSelection() && liquibasePath.getText().trim().isEmpty()) {
            MessageDialog.openError(getShell(), "OneClickAPI", "Enter a package path for the Liquibase changelog.");
            return null;
        }
        Preset preset = presets.get(presetCombo.getSelectionIndex());
        GenerationOptions o = generator.options(basePackage.getText().trim(), preset.getValues());

        Map<String, Object> overrides = new LinkedHashMap<>();
        boxes.forEach((key, box) -> put(overrides, key, Boolean.toString(box.getSelection())));
        overrides.put("repository", repository.getText());
        overrides.put("converter", converter.getText());
        overrides.put("onConflict", conflict.getText());
        GenerationOptions refined = generator.refine(o, overrides);

        LiquibaseOptions lb = generator.liquibase(
            useLiquibase.getSelection(),
            liquibasePath.getText().trim(),
            classNames.size() == 1 ? liquibaseFileName.getText().trim() : "",
            "YAML".equals(liquibaseFormat.getText()));
        return generator.withFeatures(refined, lb, imageUpload.getSelection(), pdfUpload.getSelection());
    }

    private void saveAsPreset() {
        GenerationOptions o = currentOptions();
        if (o == null) {
            return;
        }
        InputDialog name = new InputDialog(getShell(), "Save Preset",
            "Name of the preset. It is stored in .oneclickapi.yml in the project root so the whole team can use it.", "", null);
        if (name.open() != Window.OK || name.getValue().trim().isEmpty()) {
            return;
        }
        String presetName = name.getValue().trim();
        boolean makeDefault = MessageDialog.openQuestion(getShell(), "Save Preset",
            "Use '" + presetName + "' automatically whenever this dialog opens?");
        generator.savePreset(presetName, generator.values(o), makeDefault);
        presets = generator.presets();
        fillPresets();
        int index = 0;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).getName().equals(presetName)) {
                index = i;
            }
        }
        presetCombo.select(index);
        loadPreset();
    }

    @Override
    protected void okPressed() {
        GenerationOptions o = currentOptions();
        if (o == null) {
            return;
        }
        result = o;
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
        Object cur = values;
        for (String p : key.split("/")) {
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
