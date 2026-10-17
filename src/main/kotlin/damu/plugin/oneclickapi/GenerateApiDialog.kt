// Copyright 2026 Damohara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi

import damu.plugin.oneclickapi.util.PsiUtils
import com.intellij.ide.util.PackageChooserDialog
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*

class GenerateApiDialog(
    initialClasses: List<PsiClass>,
    private val project: Project,
    private val defaultOrmType: String
) : DialogWrapper(true) {

    private val classPathTextField = JTextField()
    private val classPathButton = JButton("...")

    // Determine if CRUD API should be enabled by default
    private val hasCrudEligibleClasses = initialClasses.any {
        PsiUtils.isEntityClass(it) || PsiUtils.isPojoClass(it)
    }

    // Generate CRUD API checkbox - enabled only if Entity or POJO classes are detected
    private val generateCrudApiCheckBox = JBCheckBox(
        "Generate CRUD API (Controller, Service, Repository)",
        hasCrudEligibleClasses // Default selected only if Entity/POJO classes exist
    )

    // Warning labels for entity class requirement
    private val crudWarningLabel = JLabel("⚠ Entity or POJO class required").apply {
        foreground = Color.RED
        isVisible = false
    }

    private val liquibaseWarningLabel = JLabel("⚠ Entity or POJO class required").apply {
        foreground = Color.RED
        isVisible = false
    }

    // Class list with add/remove functionality
    private val classListModel = DefaultListModel<ClassItem>()
    private val classList = JBList(classListModel).apply {
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }
    private val classScrollPane = JScrollPane(classList).apply {
        preferredSize = Dimension(450, 120)
    }
    private val addClassButton = JButton("Add Class")
    private val removeClassButton = JButton("Remove Selected")

    // ORM Type display label
    private val ormTypeLabel = JLabel()

    // Lets the user force the persistence style; "Auto-detect" keeps the per-class detection
    private val ormTypeComboBox = JComboBox(
        DefaultComboBoxModel(arrayOf(ORM_AUTO, ORM_JPA, ORM_MYBATIS))
    )

    private val repositoryTypeComboBox = JComboBox(
        DefaultComboBoxModel(
            arrayOf("JPA Repository", "CRUD Repository")
        )
    )
    private val basePathTextField = JTextField("/api/v1")
    private val useExceptionHandlingCheckBox = JBCheckBox("Add Global Exception Handling")
    private val useLombokCheckBox = JBCheckBox("Use Lombok")

    private val converterTypeComboBox = JComboBox(
        DefaultComboBoxModel(
            arrayOf("MapStruct", "Manual Conversion")
        )
    )

    private val useLiquibaseCheckBox = JBCheckBox("Generate Liquibase Scripts")
    private val liquibasePackagePathTextField = JTextField()
    private val liquibaseFileNameTextField = JTextField()
    private val liquibaseFormatComboBox = JComboBox(
        DefaultComboBoxModel(
            arrayOf("XML", "YAML", "JSON", "SQL")
        )
    )
    private val generateImageUploadApiCheckBox = JBCheckBox("Generate Image Upload API")
    private val generatePdfUploadApiCheckBox = JBCheckBox("Generate PDF Upload API")

    init {
        init()
        title = "OneClickAPI"

        // Populate initial classes
        for (psiClass in initialClasses) {
            addClassToList(psiClass)
        }

        populateClassPath()
        updateOrmTypeLabel()

        classPathButton.addActionListener { showPackageChooser() }
        ormTypeComboBox.addActionListener { updateRepositoryAndConverterVisibility() }

        // Add class button
        addClassButton.addActionListener {
            showClassChooser()
        }

        // Remove class button
        removeClassButton.addActionListener {
            val selectedIndices = classList.selectedIndices
            // Remove from end to start to avoid index issues
            for (i in selectedIndices.reversed()) {
                classListModel.remove(i)
            }
            updateOrmTypeLabel()
            updateLiquibaseFileName()
            updateWarningLabels()
        }

        // Update when selection changes
        classList.addListSelectionListener {
            updateOrmTypeLabel()
            updateLiquibaseFileName()
        }

        // Get current date and time
        val now = LocalDateTime.now()
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now)

        // Default Liquibase package path
        liquibasePackagePathTextField.text = "db/changelog/${now.year}/${now.monthValue.toString().padStart(2, '0')}"

        // Disable Liquibase fields by default
        liquibasePackagePathTextField.isEnabled = false
        liquibaseFileNameTextField.isEnabled = false
        liquibaseFormatComboBox.isEnabled = false

        // If single selection, set default file name
        if (initialClasses.size == 1) {
            val description = initialClasses.first().name?.lowercase()?.replace("entity", "")?.trim() ?: "changelog"
            val defaultFileName = "${timestamp}_${description}"
            liquibaseFileNameTextField.text = defaultFileName
        }

        // Enable/disable Liquibase fields based on checkbox state
        useLiquibaseCheckBox.addActionListener {
            val enabled = useLiquibaseCheckBox.isSelected
            liquibasePackagePathTextField.isEnabled = enabled
            liquibaseFileNameTextField.isEnabled = enabled && classListModel.size() == 1
            liquibaseFormatComboBox.isEnabled = enabled
            if (enabled) {
                updateLiquibaseFileName()
            }
            updateWarningLabels()
        }

        // File upload checkboxes
        generateImageUploadApiCheckBox.isSelected = false
        generatePdfUploadApiCheckBox.isSelected = false

        // Action listeners for file upload checkboxes
        generateImageUploadApiCheckBox.addActionListener {
            if (generateImageUploadApiCheckBox.isSelected) {
                showBusinessLogicPopup("Image Upload API")
            }
        }
        generatePdfUploadApiCheckBox.addActionListener {
            if (generatePdfUploadApiCheckBox.isSelected) {
                showBusinessLogicPopup("PDF Upload API")
            }
        }

        // Show/Hide Hibernate-specific fields based on ORM type
        updateRepositoryAndConverterVisibility()

        // Show/Hide CRUD API related fields based on checkbox
        generateCrudApiCheckBox.addActionListener {
            updateCrudApiFieldsVisibility()
            updateWarningLabels()
        }
        updateCrudApiFieldsVisibility()

        // Update Add Class button visibility
        updateAddClassButtonVisibility()

        // Initial warning labels update
        updateWarningLabels()
    }

    private fun updateWarningLabels() {
        val hasClasses = classListModel.size() > 0

        // Show CRUD warning if checkbox is selected but no classes
        crudWarningLabel.isVisible = generateCrudApiCheckBox.isSelected && !hasClasses

        // Show Liquibase warning if checkbox is selected but no classes
        liquibaseWarningLabel.isVisible = useLiquibaseCheckBox.isSelected && !hasClasses
    }

    private fun updateAddClassButtonVisibility() {
        addClassButton.isEnabled = generateCrudApiCheckBox.isSelected
    }

    private fun addClassToList(psiClass: PsiClass) {
        val classType = when {
            PsiUtils.isEntityClass(psiClass) -> "Entity"
            PsiUtils.isPojoClass(psiClass) -> "POJO"
            else -> null // Don't add other types
        }

        // Only add Entity or POJO classes
        if (classType == null) {
            return
        }

        val classItem = ClassItem(psiClass, "${psiClass.name} [$classType]", classType)

        // Check if already exists
        val exists = (0 until classListModel.size()).any {
            classListModel.getElementAt(it).psiClass == psiClass
        }

        if (!exists) {
            classListModel.addElement(classItem)
            updateWarningLabels()
        }
    }

    private fun showClassChooser() {
        val chooser = TreeClassChooserFactory.getInstance(project)
            .createAllProjectScopeChooser("Select Class to Add")

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

        // Update repository and converter visibility
        updateRepositoryAndConverterVisibility()
    }

    private fun updateRepositoryAndConverterVisibility() {
        val allClasses = getAllClasses()
        val usesJpa = when (ormTypeComboBox.selectedItem) {
            ORM_JPA -> true
            ORM_MYBATIS -> false
            else -> allClasses.any { PsiUtils.isEntityClass(it) }
        }
        val isCrudApiEnabled = generateCrudApiCheckBox.isSelected

        repositoryTypeComboBox.isVisible = usesJpa && isCrudApiEnabled
        converterTypeComboBox.isVisible = usesJpa && isCrudApiEnabled
    }

    private fun updateCrudApiFieldsVisibility() {
        val isCrudApiEnabled = generateCrudApiCheckBox.isSelected

        // Update all CRUD API related fields visibility
        repositoryTypeComboBox.isVisible = isCrudApiEnabled
        converterTypeComboBox.isVisible = isCrudApiEnabled
        useLombokCheckBox.isVisible = isCrudApiEnabled
        basePathTextField.isEnabled = isCrudApiEnabled

        // Update Add Class button
        addClassButton.isEnabled = isCrudApiEnabled
        removeClassButton.isEnabled = isCrudApiEnabled

        updateRepositoryAndConverterVisibility()
    }

    private fun updateLiquibaseFileName() {
        if (!useLiquibaseCheckBox.isSelected) return

        val allClasses = getAllClasses()
        if (allClasses.size == 1) {
            val now = LocalDateTime.now()
            val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now)
            val description = allClasses.first().name?.lowercase()?.replace("entity", "")?.trim()
                ?: "changelog"
            liquibaseFileNameTextField.text = "${timestamp}_${description}"
            liquibaseFileNameTextField.isEnabled = true
        } else if (allClasses.size > 1) {
            liquibaseFileNameTextField.text = "Multiple files will be generated"
            liquibaseFileNameTextField.isEnabled = false
        }
    }

    private fun getAllClasses(): List<PsiClass> {
        return (0 until classListModel.size()).map {
            classListModel.getElementAt(it).psiClass
        }
    }

    private fun populateClassPath() {
        val allClasses = getAllClasses()
        val packageName = allClasses.firstOrNull()?.qualifiedName?.substringBeforeLast('.')
        if (packageName != null) {
            classPathTextField.text = packageName
        }
    }

    private fun showPackageChooser() {
        val packageChooser = PackageChooserDialog("Select Destination Package", project)
        if (packageChooser.showAndGet()) {
            val selectedPackage = packageChooser.selectedPackage
            if (selectedPackage != null) {
                classPathTextField.text = selectedPackage.qualifiedName
            }
        }
    }

    private fun showBusinessLogicPopup(apiType: String) {
        val message = "You need to write business logic for the $apiType in your service class."
        val title = "$apiType Business Logic"
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(550, 600)
            minimumSize = Dimension(550, 400)
        }

        val mainPanel = panel {

            // Generate CRUD API checkbox - FIRST OPTION
            row("Generate CRUD API:") {
                cell(generateCrudApiCheckBox)
            }

            row("Lombok:") {
                cell(useLombokCheckBox)
            }

            row("Base Path:") {
                cell(basePathTextField)
                    .align(AlignX.FILL)
                    .comment("Controller URL prefix, e.g. /api/v1 gives /api/v1/product")
            }

            row {
                cell(crudWarningLabel)
            }

            // Class selection section
            group("Selected Classes") {
                row {
                    cell(classScrollPane)
                        .align(Align.FILL)
                }.resizableRow()
                row {
                    cell(addClassButton)
                    cell(removeClassButton)
                }
                if (classListModel.isEmpty) {
                    row {
                        label("No classes selected. Add classes using the button above.")
                            .component.foreground = java.awt.Color.GRAY
                    }
                }
            }

            row("Class Path:") {
                cell(classPathTextField)
                    .align(AlignX.FILL)
                    .resizableColumn()
                cell(classPathButton)
            }

            row("ORM Type:") {
                cell(ormTypeComboBox)
                    .align(AlignX.FILL)
            }

            row("Detected from classes:") {
                cell(ormTypeLabel)
                    .bold()
            }

            row("Repository Type:") {
                cell(repositoryTypeComboBox)
                    .align(AlignX.FILL)
            }

            row("Converter Type:") {
                cell(converterTypeComboBox)
                    .align(AlignX.FILL)
            }

            separator()

            row("Liquibase:") {
                cell(useLiquibaseCheckBox)
            }
            row {
                cell(liquibaseWarningLabel)
            }

            row("Liquibase Package Path:") {
                cell(liquibasePackagePathTextField)
                    .align(AlignX.FILL)
            }

            row("Liquibase File Name:") {
                cell(liquibaseFileNameTextField)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }

            row("Liquibase Format:") {
                cell(liquibaseFormatComboBox)
                    .align(AlignX.FILL)
            }

            separator()

            row("Exception Handling:") {
                cell(useExceptionHandlingCheckBox)
            }

            separator()

            row("Image Upload API:") {
                cell(generateImageUploadApiCheckBox)
            }

            row("PDF Upload API:") {
                cell(generatePdfUploadApiCheckBox)
            }

        } as DialogPanel

        // Wrap the main panel in a JScrollPane for scrolling support
//        burda xett dusenleri yenile
        val scrollPane = JScrollPane(mainPanel as JComponent).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            border = null

            // Increase scroll speed for better touchpad/mouse wheel experience
            verticalScrollBar.unitIncrement = 10  // Default is 1
            verticalScrollBar.blockIncrement = 40 // Default is 10
        }

        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    // Getter methods
    fun getSelectedClasses(): List<PsiClass> {
        return getAllClasses()
    }

    fun useExceptionHandling(): Boolean = useExceptionHandlingCheckBox.isSelected
    fun getBasePath(): String = basePathTextField.text
    fun getPath(): String = classPathTextField.text
    fun getLiquibasePackagePath(): String = liquibasePackagePathTextField.text
    fun getLiquibaseFileName(): String = liquibaseFileNameTextField.text
    fun getRepoType(): String = repositoryTypeComboBox.selectedItem as String
    fun useLombok(): Boolean = useLombokCheckBox.isSelected
    fun useLiquibase(): Boolean = useLiquibaseCheckBox.isSelected
    fun getLiquibaseFormat(): String = liquibaseFormatComboBox.selectedItem as String
    fun getConverterType(): String = converterTypeComboBox.selectedItem as String
    fun generateImageUploadApi(): Boolean = generateImageUploadApiCheckBox.isSelected
    fun generatePdfUploadApi(): Boolean = generatePdfUploadApiCheckBox.isSelected
    fun getOrmType(): String = ormTypeLabel.text ?: "JPA/Hibernate"

    /** The ORM the user forced, or null to detect it per class. */
    fun getOrmOverride(): String? = when (ormTypeComboBox.selectedItem) {
        ORM_JPA -> "JPA/Hibernate"
        ORM_MYBATIS -> "MyBatis"
        else -> null
    }
    fun generateCrudApi(): Boolean = generateCrudApiCheckBox.isSelected
}

data class ClassItem(
    val psiClass: PsiClass,
    val displayName: String,
    val classType: String
) {
    override fun toString(): String = displayName
}

private const val ORM_AUTO = "Auto-detect"
private const val ORM_JPA = "JPA/Hibernate"
private const val ORM_MYBATIS = "MyBatis"
