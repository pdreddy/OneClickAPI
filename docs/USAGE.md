# Usage Guide for OneClickAPI

This guide will walk you through how to use **OneClickAPI** in **IntelliJ IDEA** to quickly generate APIs for your **Spring Boot** projects.

---

## Getting Started

After installing the **OneClickAPI** plugin, follow these steps to generate APIs in your project.

### ** Generating API for a Single Class**
You can generate APIs for a single `@Entity` or **POJO** class by following these steps:

1. Open your **Spring Boot** project in **IntelliJ IDEA**.
2. Navigate to the class file you want to generate an API for.
3. **Right-click** on the class in the **Edit view**.
4. Select **Generate > Generate API** from the context menu.
5. A configuration window will appear where you can customize the API generation settings.
6. Click **Generate** to preview the proposed files, then click **Apply selected files** to create or replace only the files you checked.

---

### ** Generating API for Multiple Classes**
If you want to generate APIs for multiple classes at once:

1. In the **Project Explorer**, select multiple `@Entity` or **POJO** classes by holding `Ctrl` (Windows/Linux) or `Cmd` (macOS).
2. **Right-click** on the selected classes.
3. Choose **New > Generate API** from the context menu.
4. Configure the API settings in the popup window.
5. Click **Generate** to create APIs for all selected classes simultaneously.

---

## Customization Options
When generating APIs, you will be able to configure several options, including:

- **Repository Type:** Choose between JPA, MyBatis, or a custom repository.
- **DTO Generation:** Enable or disable automatic DTO creation.
- **Lombok Support:** Use **@Data**, **@Getter**, or **@Setter** to simplify model classes.
- **Liquibase Integration:** Generate Liquibase changelogs for database migrations.
- **File Upload API:** Automatically generate API endpoints for image or PDF file uploads.
- **Exception Handling:** Add global exception handling for better error management.

## Preview and selective updates

After choosing your generation options, review the proposed files in the preview dialog.

- Select a file in the tree to compare the current text with the generated text. The current side includes unsaved editor changes.
- New files are checked by default. Existing files are unchecked; check an individual file only when you want to replace its entire contents. Unchanged files cannot be selected.
- Click a file's checkbox or press Space to toggle it. **Select new** restores the default selection; **Clear selection** unchecks everything.
- Click **Apply selected files** to write only the checked files. Selecting only part of an API may require dependencies that already exist in your project.
- Cancel closes the preview without creating files or directories. Applied changes are grouped into one **Generate API files** undo action.

If a selected file changes after preview, generation stops before writing any selected files. Open Generate API again to review the latest text. Conflicting output from multiple classes targeting the same file is reported before preview; choose separate packages or generate those classes separately.
