// Copyright 2026 Damodhara Reddy Palavali
// SPDX-License-Identifier: Apache-2.0

package damu.plugin.oneclickapi.generator.upload

import com.intellij.psi.PsiClass

object ImageUploadApiGenerator {

    fun generateFiles(psiClass: PsiClass, packageName: String, useLombok: Boolean): Map<String, String> {
        val responseImport = getHttpServletResponseImport(psiClass)
        return mapOf(
            "controller/ImageUploadController.java" to generateImageUploadControllerContent(packageName, useLombok, responseImport),
            "service/ImageService.java" to generateImageServiceContent(packageName, responseImport)
        )
    }

    private fun generateImageUploadControllerContent(
        packageName: String,
        useLombok: Boolean,
        httpServletResponseImport: String
    ): String {
        val controllerPackage = "$packageName.controller"
        val servicePackage = "$packageName.service"

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

    private fun generateImageServiceContent(
        packageName: String,
        httpServletResponseImport: String
    ): String {

        val servicePackage = "$packageName.service"

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
