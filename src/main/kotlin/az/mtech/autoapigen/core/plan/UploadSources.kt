package az.mtech.autoapigen.core.plan

/**
 * Source text of the file-upload endpoints (image / PDF). These are intentionally plain strings: the generated
 * code is a starting point that the user is expected to edit (the service methods say where).
 */
internal object UploadSources {

    fun fileDto(classPackageName: String): String {
        return """
            package $classPackageName;

            public class FileDto {
                private String id;
                private String contentType;
                private String name;

                public FileDto(String id, String contentType, String name) {
                    this.id = id;
                    this.contentType = contentType;
                    this.name = name;
                }

                public String getId() {
                    return id;
                }

                public void setId(String id) {
                    this.id = id;
                }

                public String getContentType() {
                    return contentType;
                }

                public void setContentType(String contentType) {
                    this.contentType = contentType;
                }

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }
            }
        """.trimIndent()
    }

    fun fileService(classPackageName: String, dtoPackage: String, httpServletResponseImport: String): String {

        return """
            package $classPackageName;

            import $dtoPackage.FileDto;
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

    fun imageController(controllerPackage: String, servicePackage: String, httpServletResponseImport: String): String {

        return """
            package $controllerPackage;

            import $servicePackage.ImageService;
            $httpServletResponseImport
            import org.springframework.http.MediaType;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.*;
            import org.springframework.web.multipart.MultipartFile;

            import java.util.List;
            import java.util.UUID;

            @RestController
            @RequestMapping("/api/v1/files/image")
            public class ImageUploadController {

                private final ImageService imageService;

                public ImageUploadController(ImageService imageService) {
                    this.imageService = imageService;
                }

                @PostMapping
                public ResponseEntity<UUID> upload(@RequestParam("file") MultipartFile file) {
                    UUID imageId = imageService.upload(file);
                    return ResponseEntity.ok(imageId);
                }

                @PostMapping("/base64")
                public ResponseEntity<List<UUID>> uploadFiles(@RequestBody List<String> images) {
                    List<UUID> uuids = imageService.uploadBase64Images(images);
                    return ResponseEntity.ok(uuids);
                }

                @GetMapping(value = "/{id}", produces = MediaType.IMAGE_PNG_VALUE)
                public void downloadPhoto(@PathVariable UUID id,
                                          HttpServletResponse response) {
                    imageService.downloadImage(id, response);
                    response.setContentType(MediaType.IMAGE_PNG_VALUE);
                }
            }
        """.trimIndent()
    }

    fun imageService(servicePackage: String, httpServletResponseImport: String): String {

        return """
            package $servicePackage;

            $httpServletResponseImport
            import org.springframework.stereotype.Service;
            import org.springframework.web.multipart.MultipartFile;

            import java.io.ByteArrayOutputStream;
            import java.io.IOException;
            import java.nio.charset.StandardCharsets;
            import java.util.ArrayList;
            import java.util.Base64;
            import java.util.List;
            import java.util.UUID;

            @Service
            public class ImageService {

                private static final String CATEGORY = "image";

                private final FileService fileService;

                public ImageService(FileService fileService) {
                    this.fileService = fileService;
                }

                public UUID upload(MultipartFile file) {
                    // Upload the file and generate a unique UUID based on the file path.
                    // You may modify this logic depending on how you want to store the file.
                    String pathToFile = fileService.uploadFile(CATEGORY, file);
                    UUID uuid = UUID.nameUUIDFromBytes(pathToFile.getBytes(StandardCharsets.UTF_8));
                    return uuid;
                }

                public List<UUID> uploadBase64Images(List<String> base64Images) {
                    List<UUID> uploadedFiles = new ArrayList<>();

                    // Loop through the list of base64 images and process each one
                    for (String base64Image : base64Images) {
                        // Decode the base64 image to byte array
                        byte[] image = Base64.getDecoder().decode(base64Image);
                        // Upload the decoded image file and store the path
                        String pathToFile = fileService.uploadFile(CATEGORY, image);
                        // Generate a UUID based on the path of the uploaded file
                        UUID uuid = UUID.nameUUIDFromBytes(pathToFile.getBytes(StandardCharsets.UTF_8));
                        uploadedFiles.add(uuid);
                    }

                    return uploadedFiles;
                }

                public void downloadImage(UUID id, HttpServletResponse response) {
                    try {
                        // This method retrieves the file path by UUID and sends the image in the response.
                        // Replace the logic with your custom business logic if necessary.
                        String pathToFile = getFilePathByUUID(id);
                        ByteArrayOutputStream baos = fileService.getFileAsBaos(pathToFile);
                        baos.writeTo(response.getOutputStream());
                    } catch (IOException e) {
                        // Handle errors during image download
                        throw new RuntimeException("Error while downloading image", e);
                    }
                }

                private String getFilePathByUUID(UUID id) {
                    // Implement custom logic to map UUID to the actual file path (e.g., lookup from a database or cache)
                    return "path/to/file/for/uuid/" + id.toString() + ".jpg";  // Modify as needed
                }
            }
        """.trimIndent()
    }

    fun pdfController(classPackageName: String, dtoPackage: String, servicePackage: String, httpServletResponseImport: String): String {

        return """
            package $classPackageName;

            import $dtoPackage.FileDto;
            import $servicePackage.FilePdfService;
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

    fun pdfService(classPackageName: String, dtoPackage: String, httpServletResponseImport: String): String {

        return """
            package $classPackageName;

            import $dtoPackage.FileDto;
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
}
