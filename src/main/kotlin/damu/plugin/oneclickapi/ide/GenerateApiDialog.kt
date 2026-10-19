package damu.plugin.oneclickapi.ide

import damu.plugin.oneclickapi.core.options.ApiVersioning
import damu.plugin.oneclickapi.core.options.ConflictPolicy
import damu.plugin.oneclickapi.core.options.ConverterType
import damu.plugin.oneclickapi.core.options.GenerationOptions
import damu.plugin.oneclickapi.core.options.LiquibaseFormat
import damu.plugin.oneclickapi.core.options.LiquibaseOptions
import damu.plugin.oneclickapi.core.options.OpenApiOptions
import damu.plugin.oneclickapi.core.options.PackageLayout
import damu.plugin.oneclickapi.core.options.RepositoryType
import damu.plugin.oneclickapi.core.options.Standards
import damu.plugin.oneclickapi.core.options.TestOptions
import damu.plugin.oneclickapi.core.preset.Preset
import damu.plugin.oneclickapi.core.preset.PresetCodec
import damu.plugin.oneclickapi.core.preset.PresetStore
import damu.plugin.oneclickapi.core.project.ProjectProfile
import damu.plugin.oneclickapi.util.PsiUtils
import com.intellij.ide.util.PackageChooserDialog
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.psi.PsiClass
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel

/** One entry of the preset drop-down. */
private class PresetItem(val preset: Preset?, val label: String) {
    override fun toString(): String = label
}

class GenerateApiDialog(
    initialClasses: List<PsiClass>,
    private val project: Project,
    private val presetStore: PresetStore,
    /** Detected profile of the first class's module, for the info line; null when no class is selected. */
    private val profile: ProjectProfile?,
    /** "Generate Tests Only": the CRUD sections are hidden and only the selected test classes are written. */
    private val testsOnly: Boolean = false
) : DialogWrapper(project, true) {

    private val classPathTextField = JTextField()
    private val classPathButton = JButton("...")

    private val hasCrudEligibleClasses = initialClasses.any { PsiUtils.isEntityClass(it) || PsiUtils.isPojoClass(it) }

    private val generateCrudApiCheckBox = JBCheckBox(
        "Generate CRUD API (Controller, Service, Repository)", hasCrudEligibleClasses
    )

    private val crudWarningLabel = JLabel("⚠ Entity or POJO class required").apply {
        foreground = Color.RED
        isVisible = false
    }
    private val liquibaseWarningLabel = JLabel("⚠ Entity or POJO class required").apply {
        foreground = Color.RED
        isVisible = false
    }

    private val classListModel = DefaultListModel<ClassItem>()
    private val classList = JBList(classListModel).apply { selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION }
    private val classScrollPane = JScrollPane(classList).apply { preferredSize = Dimension(450, 120) }
    private val addClassButton = JButton("Add Class")
    private val removeClassButton = JButton("Remove Selected")

    private val ormTypeLabel = JLabel()

    private val repositoryTypeComboBox = JComboBox(DefaultComboBoxModel(RepositoryType.entries.map { it.label }.toTypedArray()))
    private val useExceptionHandlingCheckBox = JBCheckBox("Add Global Exception Handling")
    private val useLombokCheckBox = JBCheckBox("Use Lombok")
    private val converterTypeComboBox = JComboBox(DefaultComboBoxModel(ConverterType.entries.map { it.label }.toTypedArray()))

    private val useLiquibaseCheckBox = JBCheckBox("Generate Liquibase Scripts")
    private val liquibasePackagePathTextField = JTextField()
    private val liquibaseFileNameTextField = JTextField()
    private val liquibaseFormatComboBox = JComboBox(DefaultComboBoxModel(arrayOf("XML", "YAML")))
    private val generateImageUploadApiCheckBox = JBCheckBox("Generate Image Upload API")
    private val generatePdfUploadApiCheckBox = JBCheckBox("Generate PDF Upload API")

    // ---- presets
    private val presetComboBox = JComboBox<PresetItem>()
    private val savePresetButton = JButton("Save as Preset...")
    private val presetInfoLabel = JBLabel().apply { foreground = Color.GRAY }
    private var layout = PackageLayout()
    private var applyingPreset = false

    // ---- standards
    private val validationCheckBox = JBCheckBox("Bean Validation annotations (@NotNull, @NotBlank, @Size, @Email)")
    private val paginationCheckBox = JBCheckBox("Pagination, sorting, filtering and search on the list endpoint")
    private val standardResponseCheckBox = JBCheckBox("Standard response envelope and error format (ApiResult / ApiError)")
    private val softDeleteCheckBox = JBCheckBox("Soft delete (adds a 'deleted' field to the entity)")
    private val auditFieldsCheckBox = JBCheckBox("Audit fields (adds createdAt / updatedAt to the entity)")
    private val relationDtosCheckBox = JBCheckBox("Expose relations as ids (no circular DTOs)", true)
    private val versioningComboBox = JComboBox(DefaultComboBoxModel(ApiVersioning.entries.map { it.label }.toTypedArray()))
    private val apiVersionSpinner = JSpinner(SpinnerNumberModel(1, 1, 99, 1))

    // ---- tests and documentation
    private val serviceTestsCheckBox = JBCheckBox("Service tests (JUnit 5 + Mockito)")
    private val controllerTestsCheckBox = JBCheckBox("Controller tests (JUnit 5 + MockMvc)")
    private val httpFileCheckBox = JBCheckBox("IntelliJ .http request file")
    private val mapperTestsCheckBox = JBCheckBox("Mapper tests (entity <-> DTO, relation ids)")
    private val integrationTestsCheckBox = JBCheckBox("Full-stack API tests (real controller, service, repository on in-memory H2; JPA only)")
    private val coverageCheckBox = JBCheckBox("Coverage report (adds JaCoCo to the build file; report written by the test task)")
    private val openApiAnnotationsCheckBox = JBCheckBox("Springdoc / OpenAPI annotations")
    private val openApiSpecCheckBox = JBCheckBox("OpenAPI 3 spec file (YAML)")
    private val openApiConfigCheckBox = JBCheckBox("OpenApiConfig bean (title, version)")

    // ---- existing files
    private val conflictComboBox = JComboBox(DefaultComboBoxModel(ConflictPolicy.entries.map { it.label }.toTypedArray()))
    private val previewCheckBox = JBCheckBox(
        "Preview files and changes before generating",
        PropertiesComponent.getInstance().getBoolean(PREVIEW_KEY, true)
    )

    init {
        init()
        title = "OneClickAPI"

        for (psiClass in initialClasses) addClassToList(psiClass)

        populateClassPath()
        updateOrmTypeLabel()
        loadPresets()

        classPathButton.addActionListener { showPackageChooser() }
        addClassButton.addActionListener { showClassChooser() }
        removeClassButton.addActionListener {
            for (i in classList.selectedIndices.reversed()) classListModel.remove(i)
            updateOrmTypeLabel()
            updateLiquibaseFileName()
            updateWarningLabels()
        }
        classList.addListSelectionListener {
            updateOrmTypeLabel()
            updateLiquibaseFileName()
        }

        val now = LocalDateTime.now()
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now)
        liquibasePackagePathTextField.text = "db/changelog/${now.year}/${now.monthValue.toString().padStart(2, '0')}"
        liquibasePackagePathTextField.isEnabled = false
        liquibaseFileNameTextField.isEnabled = false
        liquibaseFormatComboBox.isEnabled = false

        if (initialClasses.size == 1) {
            val description = initialClasses.first().name?.lowercase()?.replace("entity", "")?.trim() ?: "changelog"
            liquibaseFileNameTextField.text = "${timestamp}_${description}"
        }

        useLiquibaseCheckBox.addActionListener {
            val enabled = useLiquibaseCheckBox.isSelected
            liquibasePackagePathTextField.isEnabled = enabled
            liquibaseFileNameTextField.isEnabled = enabled && classListModel.size() == 1
            liquibaseFormatComboBox.isEnabled = enabled
            if (enabled) updateLiquibaseFileName()
            updateWarningLabels()
        }

        generateImageUploadApiCheckBox.addActionListener {
            if (generateImageUploadApiCheckBox.isSelected) showBusinessLogicPopup("Image Upload API")
        }
        generatePdfUploadApiCheckBox.addActionListener {
            if (generatePdfUploadApiCheckBox.isSelected) showBusinessLogicPopup("PDF Upload API")
        }

        generateCrudApiCheckBox.addActionListener {
            updateCrudApiFieldsVisibility()
            updateWarningLabels()
        }
        updateCrudApiFieldsVisibility()
        updateWarningLabels()

        versioningComboBox.addActionListener { apiVersionSpinner.isEnabled = versioningComboBox.selectedIndex == ApiVersioning.PATH.ordinal || versioningComboBox.selectedIndex == ApiVersioning.HEADER.ordinal }
        versioningComboBox.selectedIndex = ApiVersioning.PATH.ordinal

        presetComboBox.addActionListener { if (!applyingPreset) applySelectedPreset() }
        savePresetButton.addActionListener { saveCurrentAsPreset() }

        previewCheckBox.addActionListener { updateOkText() }
        updateOkText()
    }

    // ------------------------------------------------------------------------------------------- presets

    private fun loadPresets() {
        val file = presetStore.load()
        val model = DefaultComboBoxModel<PresetItem>()
        model.addElement(PresetItem(null, "(custom)"))
        presetStore.available().forEach {
            model.addElement(PresetItem(it, if (it.builtIn) "${it.name}  (built-in)" else "${it.name}  (.oneclickapi.yml)"))
        }
        presetComboBox.model = model
        val info = StringBuilder()
        if (file.problems.isNotEmpty()) info.append("<font color='#C75450'>").append(file.problems.joinToString("; ")).append("</font> ")
        val default = file.defaultPreset
        if (default != null) {
            val index = (0 until model.size).firstOrNull { model.getElementAt(it).preset?.name == default }
            if (index != null) {
                applyingPreset = true
                presetComboBox.selectedIndex = index
                applyingPreset = false
                applySelectedPreset()
                info.append("Default preset from .oneclickapi.yml applied.")
            }
        } else if (file.exists) {
            info.append("Presets read from .oneclickapi.yml.")
        }
        presetInfoLabel.text = "<html>$info</html>"
    }

    private fun applySelectedPreset() {
        val preset = (presetComboBox.selectedItem as? PresetItem)?.preset ?: return
        val problems = mutableListOf<String>()
        val applied = PresetCodec.apply(buildOptions(), preset.values, problems)
        showOptions(applied)
        presetInfoLabel.text = "<html>" + (preset.description.takeIf { it.isNotBlank() } ?: "Preset '${preset.name}' applied.") +
                (if (problems.isNotEmpty()) " <font color='#C75450'>Ignored: ${problems.joinToString("; ")}</font>" else "") + "</html>"
    }

    private fun saveCurrentAsPreset() {
        val name = Messages.showInputDialog(
            project, "Name of the preset. It is stored in .oneclickapi.yml in the project root so the whole team can use it.",
            "Save Preset", null
        )?.trim().orEmpty()
        if (name.isEmpty()) return
        val makeDefault = Messages.showYesNoDialog(
            project, "Use '$name' automatically whenever this dialog opens?", "Save Preset", Messages.getQuestionIcon()
        ) == Messages.YES
        try {
            presetStore.save(name, "", PresetCodec.toValues(buildOptions()), makeDefault)
            loadPresets()
            val model = presetComboBox.model
            (0 until model.size).firstOrNull { model.getElementAt(it).preset?.name == name }?.let {
                applyingPreset = true
                presetComboBox.selectedIndex = it
                applyingPreset = false
            }
            presetInfoLabel.text = "<html>Saved '$name' to .oneclickapi.yml. Commit the file to share it.</html>"
        } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message ?: "Could not write .oneclickapi.yml", "Save Preset")
        }
    }

    /** Pushes [o] into the controls. */
    private fun showOptions(o: GenerationOptions) {
        if (o.basePackage.isNotEmpty() && classPathTextField.text.isBlank()) classPathTextField.text = o.basePackage
        layout = o.layout
        useLombokCheckBox.isSelected = o.lombok
        repositoryTypeComboBox.selectedItem = o.repositoryType.label
        converterTypeComboBox.selectedItem = o.converter.label
        useExceptionHandlingCheckBox.isSelected = o.exceptionHandling
        val s = o.standards
        validationCheckBox.isSelected = s.validation
        paginationCheckBox.isSelected = s.pagination
        standardResponseCheckBox.isSelected = s.standardResponse
        softDeleteCheckBox.isSelected = s.softDelete
        auditFieldsCheckBox.isSelected = s.auditFields
        relationDtosCheckBox.isSelected = s.relationDtos
        versioningComboBox.selectedIndex = s.versioning.ordinal
        apiVersionSpinner.value = s.apiVersion
        serviceTestsCheckBox.isSelected = o.tests.serviceTests
        controllerTestsCheckBox.isSelected = o.tests.controllerTests
        httpFileCheckBox.isSelected = o.tests.httpFile
        mapperTestsCheckBox.isSelected = o.tests.mapperTests
        integrationTestsCheckBox.isSelected = o.tests.integrationTests
        coverageCheckBox.isSelected = o.tests.coverage
        openApiAnnotationsCheckBox.isSelected = o.openApi.annotations
        openApiSpecCheckBox.isSelected = o.openApi.spec
        openApiConfigCheckBox.isSelected = o.openApi.config
        conflictComboBox.selectedItem = o.conflictPolicy.label
        o.liquibase?.let { lb ->
            useLiquibaseCheckBox.isSelected = true
            liquibasePackagePathTextField.text = lb.packagePath
            liquibaseFormatComboBox.selectedItem = lb.format.name
            liquibasePackagePathTextField.isEnabled = true
            liquibaseFormatComboBox.isEnabled = true
            liquibaseFileNameTextField.isEnabled = classListModel.size() == 1
            updateLiquibaseFileName()
            updateWarningLabels()
        }
    }

    // ------------------------------------------------------------------------------------------- state

    private fun updateOkText() {
        setOKButtonText(if (previewCheckBox.isSelected) "Preview..." else "Generate")
    }

    private fun updateWarningLabels() {
        val hasClasses = classListModel.size() > 0
        crudWarningLabel.isVisible = generateCrudApiCheckBox.isSelected && !hasClasses
        liquibaseWarningLabel.isVisible = useLiquibaseCheckBox.isSelected && !hasClasses
    }

    private fun addClassToList(psiClass: PsiClass) {
        val classType = when {
            PsiUtils.isEntityClass(psiClass) -> "Entity"
            PsiUtils.isPojoClass(psiClass) -> "POJO"
            else -> null
        } ?: return

        val exists = (0 until classListModel.size()).any { classListModel.getElementAt(it).psiClass == psiClass }
        if (!exists) {
            classListModel.addElement(ClassItem(psiClass, "${psiClass.name} [$classType]", classType))
            updateWarningLabels()
        }
    }

    private fun showClassChooser() {
        val chooser = TreeClassChooserFactory.getInstance(project).createAllProjectScopeChooser("Select Class to Add")
        chooser.showDialog()
        val selectedClass = chooser.selected
        if (selectedClass != null) {
            addClassToList(selectedClass)
            updateOrmTypeLabel()
            updateLiquibaseFileName()
        }
    }

    private fun updateOrmTypeLabel() {
        val allClasses = getAllClasses()
        val hasEntity = allClasses.any { PsiUtils.isEntityClass(it) }
        val hasPojo = allClasses.any { PsiUtils.isPojoClass(it) }
        ormTypeLabel.text = when {
            hasEntity && hasPojo -> "JPA/Hibernate and MyBatis"
            hasEntity -> "JPA/Hibernate"
            hasPojo -> "MyBatis"
            allClasses.isEmpty() -> "No classes selected"
            else -> "Unknown"
        }
        updateRepositoryAndConverterVisibility()
    }

    private fun updateRepositoryAndConverterVisibility() {
        val hasEntity = getAllClasses().any { PsiUtils.isEntityClass(it) }
        val crud = generateCrudApiCheckBox.isSelected
        repositoryTypeComboBox.isVisible = hasEntity && crud
        converterTypeComboBox.isVisible = crud
    }

    private fun updateCrudApiFieldsVisibility() {
        val crud = generateCrudApiCheckBox.isSelected
        useLombokCheckBox.isVisible = crud
        addClassButton.isEnabled = crud
        removeClassButton.isEnabled = crud
        updateRepositoryAndConverterVisibility()
    }

    private fun updateLiquibaseFileName() {
        if (!useLiquibaseCheckBox.isSelected) return
        val allClasses = getAllClasses()
        if (allClasses.size == 1) {
            val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
            val description = allClasses.first().name?.lowercase()?.replace("entity", "")?.trim() ?: "changelog"
            liquibaseFileNameTextField.text = "${timestamp}_${description}"
            liquibaseFileNameTextField.isEnabled = true
        } else if (allClasses.size > 1) {
            liquibaseFileNameTextField.text = "Multiple files will be generated"
            liquibaseFileNameTextField.isEnabled = false
        }
    }

    private fun getAllClasses(): List<PsiClass> = (0 until classListModel.size()).map { classListModel.getElementAt(it).psiClass }

    private fun populateClassPath() {
        val packageName = getAllClasses().firstOrNull()?.qualifiedName?.substringBeforeLast('.', "")
        if (!packageName.isNullOrEmpty()) classPathTextField.text = packageName
    }

    private fun showPackageChooser() {
        val packageChooser = PackageChooserDialog("Select Destination Package", project)
        if (packageChooser.showAndGet()) {
            packageChooser.selectedPackage?.let { classPathTextField.text = it.qualifiedName }
        }
    }

    private fun showBusinessLogicPopup(apiType: String) {
        JOptionPane.showMessageDialog(
            null, "You need to write business logic for the $apiType in your service class.",
            "$apiType Business Logic", JOptionPane.INFORMATION_MESSAGE
        )
    }

    override fun doValidate(): ValidationInfo? {
        if (testsOnly) {
            if (classListModel.isEmpty) return ValidationInfo("Select at least one class.", addClassButton)
            if (classPathTextField.text.isBlank()) return ValidationInfo("Class Path (target package) is required.", classPathTextField)
            val anyTest = serviceTestsCheckBox.isSelected || controllerTestsCheckBox.isSelected || mapperTestsCheckBox.isSelected ||
                    integrationTestsCheckBox.isSelected || httpFileCheckBox.isSelected
            return if (anyTest) null else ValidationInfo("Tick at least one test type.")
        }
        val somethingToGenerate = generateCrudApiCheckBox.isSelected || useLiquibaseCheckBox.isSelected ||
                useExceptionHandlingCheckBox.isSelected || generateImageUploadApiCheckBox.isSelected || generatePdfUploadApiCheckBox.isSelected
        if (!somethingToGenerate) return ValidationInfo("Nothing selected to generate.")
        if ((generateCrudApiCheckBox.isSelected || useLiquibaseCheckBox.isSelected) && classListModel.isEmpty) {
            return ValidationInfo("Select at least one Entity or POJO class.", addClassButton)
        }
        val needsPackage = generateCrudApiCheckBox.isSelected || useExceptionHandlingCheckBox.isSelected ||
                generateImageUploadApiCheckBox.isSelected || generatePdfUploadApiCheckBox.isSelected
        if (needsPackage && classPathTextField.text.isBlank()) {
            return ValidationInfo("Class Path (target package) is required.", classPathTextField)
        }
        return null
    }

    override fun doOKAction() {
        PropertiesComponent.getInstance().setValue(PREVIEW_KEY, previewCheckBox.isSelected, true)
        super.doOKAction()
    }

    // ------------------------------------------------------------------------------------------- UI

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(640, 700)
            minimumSize = Dimension(560, 420)
        }

        val projectLine = profile?.let { p ->
            val parts = mutableListOf<String>()
            parts += when (p.buildTool) {
                damu.plugin.oneclickapi.core.project.BuildTool.MAVEN -> "Maven"
                damu.plugin.oneclickapi.core.project.BuildTool.GRADLE_KOTLIN, damu.plugin.oneclickapi.core.project.BuildTool.GRADLE_GROOVY -> "Gradle"
                damu.plugin.oneclickapi.core.project.BuildTool.UNKNOWN -> "no pom.xml / build.gradle found"
            }
            p.bootVersion?.let { parts += "Spring Boot ${it.raw}" }
            if (p.libs.isNotEmpty()) parts += p.libs.joinToString(", ") { it.display.substringBefore(" (") }
            parts.joinToString("  ·  ")
        } ?: "Select a class to detect the project."

        val mainPanel = panel {
            row("Preset:") {
                cell(presetComboBox).align(AlignX.FILL).resizableColumn()
                cell(savePresetButton)
            }
            row { cell(presetInfoLabel) }
            row("Project:") { cell(JBLabel(projectLine).apply { foreground = Color.GRAY }) }

            separator()

            if (!testsOnly) {
                row("Generate CRUD API:") { cell(generateCrudApiCheckBox) }
                row("Lombok:") { cell(useLombokCheckBox) }
                row { cell(crudWarningLabel) }
            }

            group("Selected Classes") {
                row { cell(classScrollPane).align(Align.FILL) }.resizableRow()
                row {
                    cell(addClassButton)
                    cell(removeClassButton)
                }
                if (classListModel.isEmpty) {
                    row { label("No classes selected. Add classes using the button above.").component.foreground = Color.GRAY }
                }
            }

            row("Class Path:") {
                cell(classPathTextField).align(AlignX.FILL).resizableColumn()
                cell(classPathButton)
            }
            row("Detected ORM Type:") { cell(ormTypeLabel).bold() }
            if (!testsOnly) {
                row("Repository Type:") { cell(repositoryTypeComboBox).align(AlignX.FILL) }
                row("Converter Type:") { cell(converterTypeComboBox).align(AlignX.FILL) }
            }

            if (!testsOnly) {
                collapsibleGroup("API standards") {
                    row { cell(validationCheckBox) }
                    row { cell(paginationCheckBox) }
                    row { cell(standardResponseCheckBox) }
                    row { cell(softDeleteCheckBox) }
                    row { cell(auditFieldsCheckBox) }
                    row { cell(relationDtosCheckBox) }
                    row("API versioning:") {
                        cell(versioningComboBox)
                        cell(apiVersionSpinner)
                    }
                }
            }

            collapsibleGroup("Tests and documentation") {
                row { cell(serviceTestsCheckBox) }
                row { cell(controllerTestsCheckBox) }
                row { cell(httpFileCheckBox) }
                row { cell(mapperTestsCheckBox) }
                row { cell(integrationTestsCheckBox) }
                row { cell(coverageCheckBox) }
                row { cell(openApiAnnotationsCheckBox) }
                row { cell(openApiSpecCheckBox) }
                row { cell(openApiConfigCheckBox) }
            }

            if (!testsOnly) {
                collapsibleGroup("Liquibase") {
                    row("Liquibase:") { cell(useLiquibaseCheckBox) }
                    row { cell(liquibaseWarningLabel) }
                    row("Package Path:") { cell(liquibasePackagePathTextField).align(AlignX.FILL) }
                    row("File Name:") { cell(liquibaseFileNameTextField).align(AlignX.FILL).resizableColumn() }
                    row("Format:") { cell(liquibaseFormatComboBox).align(AlignX.FILL) }
                }
            }

            if (!testsOnly) {
                collapsibleGroup("Other") {
                    row("Exception Handling:") { cell(useExceptionHandlingCheckBox) }
                    row("Image Upload API:") { cell(generateImageUploadApiCheckBox) }
                    row("PDF Upload API:") { cell(generatePdfUploadApiCheckBox) }
                }
            }

            separator()

            row("If a file already exists:") { cell(conflictComboBox) }
            row {
                comment(
                    "Skip keeps your file. Replace overwrites it but keeps the text between " +
                            "'// region custom code' and '// endregion'. Merge adds only the missing methods, fields and imports."
                )
            }
            row { cell(previewCheckBox) }
        } as DialogPanel

        val scrollPane = JScrollPane(mainPanel as JComponent).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            border = null
            verticalScrollBar.unitIncrement = 10
            verticalScrollBar.blockIncrement = 40
        }
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }

    // ------------------------------------------------------------------------------------------- results

    fun getSelectedClasses(): List<PsiClass> = getAllClasses()
    fun wantsPreview(): Boolean = previewCheckBox.isSelected

    fun buildOptions(): GenerationOptions {
        val crud = generateCrudApiCheckBox.isSelected
        val liquibase = if (useLiquibaseCheckBox.isSelected) {
            LiquibaseOptions(
                liquibasePackagePathTextField.text.trim(),
                liquibaseFileNameTextField.text.trim().takeIf { classListModel.size() == 1 } ?: "",
                if (liquibaseFormatComboBox.selectedItem == "YAML") LiquibaseFormat.YAML else LiquibaseFormat.XML
            )
        } else null
        val options = GenerationOptions(
            basePackage = classPathTextField.text.trim(),
            layout = layout,
            crud = crud,
            lombok = useLombokCheckBox.isSelected,
            repositoryType = RepositoryType.fromLabel(repositoryTypeComboBox.selectedItem as? String),
            converter = ConverterType.fromLabel(converterTypeComboBox.selectedItem as? String),
            liquibase = liquibase,
            exceptionHandling = useExceptionHandlingCheckBox.isSelected,
            imageUpload = generateImageUploadApiCheckBox.isSelected,
            pdfUpload = generatePdfUploadApiCheckBox.isSelected,
            standards = Standards(
                validation = validationCheckBox.isSelected,
                pagination = paginationCheckBox.isSelected,
                standardResponse = standardResponseCheckBox.isSelected,
                softDelete = softDeleteCheckBox.isSelected,
                auditFields = auditFieldsCheckBox.isSelected,
                versioning = ApiVersioning.entries.getOrElse(versioningComboBox.selectedIndex) { ApiVersioning.PATH },
                apiVersion = (apiVersionSpinner.value as? Int) ?: 1,
                relationDtos = relationDtosCheckBox.isSelected
            ),
            tests = TestOptions(
                serviceTests = serviceTestsCheckBox.isSelected, controllerTests = controllerTestsCheckBox.isSelected,
                httpFile = httpFileCheckBox.isSelected, mapperTests = mapperTestsCheckBox.isSelected,
                integrationTests = integrationTestsCheckBox.isSelected, coverage = coverageCheckBox.isSelected
            ),
            openApi = OpenApiOptions(openApiAnnotationsCheckBox.isSelected, openApiSpecCheckBox.isSelected, openApiConfigCheckBox.isSelected),
            conflictPolicy = ConflictPolicy.entries.firstOrNull { it.label == conflictComboBox.selectedItem } ?: ConflictPolicy.SKIP
        )
        return if (testsOnly) options.copy(crud = false, liquibase = null, exceptionHandling = false, imageUpload = false, pdfUpload = false)
        else options
    }

    companion object {
        private const val PREVIEW_KEY = "damu.plugin.oneclickapi.previewBeforeGenerate"
    }
}

data class ClassItem(
    val psiClass: PsiClass,
    val displayName: String,
    val classType: String
) {
    override fun toString(): String = displayName
}
