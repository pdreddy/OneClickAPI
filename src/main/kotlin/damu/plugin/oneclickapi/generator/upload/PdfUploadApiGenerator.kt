// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator.upload

import com.intellij.psi.PsiClass

object PdfUploadApiGenerator {

    fun generateFiles(psiClass: PsiClass, packageName: String, useLombok: Boolean): Map<String, String> {
        val responseImport = getHttpServletResponseImport(psiClass)
        return mapOf(
            "controller/PdfUploadController.java" to generatePdfUploadControllerContent("$packageName.controller", packageName, useLombok, responseImport),
            "service/FilePdfService.java" to generatePdfServiceContent("$packageName.service", packageName, responseImport)
        )
    }

    private fun generatePdfUploadControllerContent(
        packageName: String,
        rootPackageName: String,
        useLombok: Boolean,
        httpServletResponseImport: String
    ): String {

        val classPackageName = packageName

        return """
            package $classPackageName;

            import $rootPackageName.dto.FileDto;
            import $rootPackageName.service.FilePdfService;
            $httpServletResponseImport
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.*;
            import org.springframework.web.multipart.MultipartFile;

            import java.util.List;
            import java.util.UUID;

            @RestController
            @RequestMapping("/api/v1/files/pdf")
            public class PdfUploadController {

                private final FilePdfService filePdfService;

                public PdfUploadController(FilePdfService filePdfService) {
                    this.filePdfService = filePdfService;
                }

                @GetMapping
                public ResponseEntity<List<FileDto>> getAllPdfs(
                        @RequestParam(required = false, defaultValue = "0") int page,
                        @RequestParam(required = false, defaultValue = "10") int size
                ) {
                    return ResponseEntity.ok(filePdfService.getAllPdfs(page, size));
                }

                @GetMapping(value = "/{id}", produces = "application/pdf")
                public void downloadPdf(@PathVariable UUID id,
                                        HttpServletResponse response) {
                    filePdfService.download(id, response);
                    response.setContentType("application/pdf");
                }

                @PostMapping
                public ResponseEntity<UUID> upload(@RequestParam("file") MultipartFile file) {
                    UUID pdf = filePdfService.upload(file);
                    return ResponseEntity.ok(pdf);
                }
            }
        """.trimIndent()
    }

    private fun generatePdfServiceContent(
        packageName: String,
        rootPackageName: String,
        httpServletResponseImport: String
    ): String {

        val classPackageName = packageName

        return """
            package $classPackageName;

            import $rootPackageName.dto.FileDto;
            $httpServletResponseImport
            import org.springframework.stereotype.Service;
            import org.springframework.web.multipart.MultipartFile;

            import java.io.ByteArrayOutputStream;
            import java.io.File;
            import java.io.IOException;
            import java.nio.charset.StandardCharsets;
            import java.nio.file.Files;
            import java.nio.file.Paths;
            import java.util.ArrayList;
            import java.util.List;
            import java.util.UUID;

            @Service
            public class FilePdfService {

                private static final String CATEGORY = "pdf";
                private final FileService fileService;

                private final static String pdfFilePath = System.getProperty("user.dir") + "/src/main/resources/";

                public FilePdfService(FileService fileService) {
                    this.fileService = fileService;
                }

                public void download(UUID id, HttpServletResponse response) {
                    try {
                        // This method should be replaced with your custom logic to retrieve the file path based on UUID.
                        // The following logic will vary depending on your application's needs.
                        String filePath = getFilePathById(id);  // Replace with your custom logic to fetch the file path by UUID

                        // Get the file as a ByteArrayOutputStream and send it in the response
                        ByteArrayOutputStream baos = fileService.getFileAsBaos(filePath);

                        // Set headers for file download
                        response.setHeader("Content-disposition", "attachment; filename=" + extractFileName(filePath));
                        baos.writeTo(response.getOutputStream());
                    } catch (IOException e) {
                        // Handle file download errors
                        throw new RuntimeException("Error downloading the file", e);
                    }
                }

                public void createPathForPdfFilesIfNotExist(String path) {
                    try {
                        // Ensure the directory for PDF files exists
                        Files.createDirectories(Paths.get(path));
                    } catch (IOException e) {
                        // Handle directory creation errors
                        throw new RuntimeException("Failed to create directory", e);
                    }
                }

                public UUID upload(MultipartFile file) {
                    // Ensure the directory exists before uploading the file
                    createPathForPdfFilesIfNotExist(pdfFilePath);

                    // Upload the file and get the file path
                    String pathToFile = fileService.uploadFile(CATEGORY, file);

                    // Example: Generate a UUID based on the file path or filename.
                    // This UUID will be used as an identifier for the uploaded file.
                    UUID fileId = UUID.nameUUIDFromBytes(pathToFile.getBytes(StandardCharsets.UTF_8));

                    // You may want to store the file path or UUID in a database or cache, depending on your needs.
                    // Store the file path or UUID wherever necessary, for example, in a simple map or in persistent storage.

                    return fileId;  // Return the generated UUID
                }

                public List<FileDto> getAllPdfs(int page, int size) {
                    // Create a pagination request (PageRequest) and fetch PDFs from the repository
                    // Map the fetched data into FileDto objects.
                    // You need to implement the logic for fetching and mapping the PDFs according to your business needs.
                    return new ArrayList<>();
                }

                private String getFilePathById(UUID id) {
                    // This method should be replaced with the logic to fetch the file path based on the UUID.
                    // Example (replace this placeholder with actual logic):
                    throw new UnsupportedOperationException("This method should be replaced with your custom file-fetching logic.");
                }

                private String extractFileName(String filePath) {
                    // Extracts the file name from the file path
                    // You can customize this logic if needed (e.g., extracting the file name from a URL or path).
                    return new File(filePath).getName();
                }
            }
        """.trimIndent()
    }

    private fun getHttpServletResponseImport(psiClass: PsiClass): String {
        val annotations = psiClass.annotations
        for (annotation in annotations) {
            if (annotation.qualifiedName == "javax.persistence.Entity" || annotation.qualifiedName == "jakarta.persistence.Entity") {
                return if (annotation.qualifiedName == "jakarta.persistence.Entity") {
                    "import jakarta.servlet.http.HttpServletResponse;"
                } else {
                    "import javax.servlet.http.HttpServletResponse;"
                }
            }
        }
        return "import jakarta.servlet.http.HttpServletResponse;" // Default (Spring Boot 3+) if no Entity annotation found
    }
}
