// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator.upload

import com.intellij.psi.PsiClass

object FileServiceGenerator {

    fun generateFiles(psiClass: PsiClass, packageName: String): Map<String, String> = mapOf(
        "service/FileService.java" to generateFileServiceContent(
            "$packageName.service", packageName, getHttpServletResponseImport(psiClass)
        )
    )

    private fun generateFileServiceContent(
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
            import java.io.FileOutputStream;
            import java.io.IOException;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.nio.file.Paths;
            import java.util.List;
            import java.util.Objects;
            import java.util.UUID;
            import java.util.stream.Collectors;

            @Service
            public class FileService {

                // Base path for file storage. This points to the user's directory.
                private static final String FILE_STORAGE_BASE_PATH = System.getProperty("user.dir");

                // Method to upload a file using a MultipartFile object and a specified category.
                public String uploadFile(String category, MultipartFile file) {
                    String fileName = generateFileName(file); // Generate a unique file name.
                    Path pathToFile = createFilePath(category, fileName); // Generate the full file path.

                    try {
                        file.transferTo(pathToFile); // Save the file to the specified path.
                    } catch (IOException e) {
                        throw new RuntimeException("Error while uploading file", e); // Handle file upload errors.
                    }

                    return pathToFile.toString(); // Return the full file path.
                }

                // Method to upload a file represented by a byte array to a specified category.
                public String uploadFile(String category, byte[] file) {
                    String fileName = UUID.randomUUID() + ".jpg"; // Generate a unique file name (with a .jpg extension).
                    Path pathToFile = createFilePath(category, fileName); // Generate the full file path.

                    try (FileOutputStream fos = new FileOutputStream(pathToFile.toFile())) {
                        fos.write(file); // Write the byte array to the file.
                    } catch (IOException e) {
                        throw new RuntimeException("Error while uploading byte array file", e); // Handle file upload errors.
                    }

                    return pathToFile.toString(); // Return the full file path.
                }

                // Method to convert a file into a ByteArrayOutputStream based on the file's path.
                public ByteArrayOutputStream getFileAsBaos(String pathToFile) {
                    try {
                        byte[] file = Files.readAllBytes(Paths.get(pathToFile)); // Read the file into a byte array.
                        ByteArrayOutputStream baos = new ByteArrayOutputStream(file.length); // Create a ByteArrayOutputStream.
                        baos.write(file, 0, file.length); // Write the byte array to the output stream.
                        return baos; // Return the output stream containing the file data.
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read file", e); // Handle file read errors.
                    }
                }

                // Private helper method to generate a file path for storing the file.
                private Path createFilePath(String category, String fileName) {
                    Path path = Paths.get(FILE_STORAGE_BASE_PATH, category, fileName); // Create the full file path.
                    path.getParent().toFile().mkdirs(); // Ensure that the parent directory exists.
                    return path; // Return the full file path.
                }

                // Method to upload a file and automatically determine its category based on the file's content type.
                public String uploadFile(MultipartFile file) {
                    String category = Objects.requireNonNull(file.getContentType()).split("/")[1]; // Extract category from content type.
                    return uploadFile(category, file); // Upload the file using the extracted category.
                }

                // Method to save a file and return its UUID (throws an exception for database save logic).
                public UUID saveFile(MultipartFile file) {
                    String pathToFile = uploadFile(file); // Upload the file and get the file path.
                    // Here you would implement the logic to save the file's metadata to a database.
                    // For example, you can save the file details and return its database ID.
                    throw new UnsupportedOperationException("Database save logic not implemented."); // Placeholder.
                }

                // Method to download a file based on its UUID (currently throws an exception).
                public String download(UUID id, HttpServletResponse response) {
                    // You would implement the logic to retrieve file metadata from the database.
                    // For example, you would fetch the file by ID and stream it to the response output stream.
                    throw new UnsupportedOperationException("File download logic not implemented."); // Placeholder.
                }

                // Method to retrieve a list of files based on pagination (currently throws an exception).
                public List<FileDto> getAllFiles(int page, int size) {
                    // Here you would implement the logic to fetch files from a database with pagination.
                    // Example: fileRepository.findAll(PageRequest.of(page, size)) and map results to FileDto.
                    throw new UnsupportedOperationException("File retrieval logic not implemented."); // Placeholder.
                }

                // Method to save multiple files and return their UUIDs.
                public List<UUID> saveFiles(List<MultipartFile> files) {
                    return files.stream()
                            .map(this::saveFile) // Save each file and get its UUID.
                            .collect(Collectors.toList()); // Collect the UUIDs into a list.
                }

                // Private helper method to generate a unique file name based on the original file name.
                private String generateFileName(MultipartFile file) {
                    String originalFileName = Objects.requireNonNull(file.getOriginalFilename()); // Get the original file name.
                    String fileExtension = originalFileName.substring(originalFileName.lastIndexOf(".")); // Extract the file extension.
                    return UUID.randomUUID() + fileExtension; // Generate a unique file name with the same extension.
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