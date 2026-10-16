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
6. Click **Generate**, and the necessary files (Controller, Service, Repository, DTOs, etc.) will be created automatically.

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
