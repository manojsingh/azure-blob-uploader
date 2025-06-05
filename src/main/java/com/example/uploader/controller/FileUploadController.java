package com.example.uploader.controller;

import com.example.uploader.service.AzureBlobService;
import com.example.uploader.dto.Base64UploadRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils; 
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; 

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {
    
    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);
    
    @Autowired
    private AzureBlobService azureBlobService;
    
    /**
     * Upload a file via Multipart Form Data to Azure Blob Storage
     * 
     * @param file The file to upload
     * @param blobName Optional custom name for the blob (if not provided, the original filename will be used)
     * @return ResponseEntity with upload result
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String blobName) {
        
        logger.info("Received request to upload file: {}, Size: {} bytes", file.getOriginalFilename(), file.getSize());
        Map<String, Object> response = new HashMap<>();
        
        if (file.isEmpty()) {
            logger.warn("Upload request with empty file: {}", file.getOriginalFilename());
            response.put("success", false);
            response.put("message", "File cannot be empty.");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            String effectiveBlobName = StringUtils.hasText(blobName) ? blobName.trim() : file.getOriginalFilename();
            // Basic sanitization: replace spaces with hyphens, could be more robust
            effectiveBlobName = effectiveBlobName.replaceAll("\\s+", "-");

            logger.info("Effective blob name: {}", effectiveBlobName);

            String blobUrl = azureBlobService.uploadFile(
                file.getInputStream(),
                effectiveBlobName,
                file.getContentType()
            );
            
            response.put("success", true);
            response.put("message", "File uploaded successfully.");
            response.put("blobUrl", blobUrl);
            response.put("blobName", effectiveBlobName);
            response.put("contentType", file.getContentType());
            response.put("size", file.getSize());
            
            logger.info("File upload successful: {}. URL: {}", effectiveBlobName, blobUrl);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for file upload. File: {}, Error: {}", file.getOriginalFilename(), e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (IOException e) {
            logger.error("Failed to upload file. File: {}, Error: {}", file.getOriginalFilename(), e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to upload file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (Exception e) { // Catch-all for other unexpected errors
            logger.error("Unexpected error during file upload. File: {}, Error: {}", file.getOriginalFilename(), e.getMessage(), e);
            response.put("success", false);
            response.put("message", "An unexpected error occurred during upload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Upload a file using base64 encoded content
     * 
     * @param request The upload request containing base64 encoded file content
     * @return ResponseEntity with upload result
     */
    @PostMapping("/upload/base64")
    public ResponseEntity<Map<String, Object>> uploadBase64(
            @RequestBody Base64UploadRequest request) {
        
        logger.info("Received base64 upload request for file: {}", request.getFileName());
        Map<String, Object> response = new HashMap<>();
        
        if (!StringUtils.hasText(request.getBase64Content())) {
            logger.warn("Base64 content is empty for file: {}", request.getFileName());
            response.put("success", false);
            response.put("message", "Base64 content cannot be empty.");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            // Decode base64 content
            byte[] decodedContent = Base64.getDecoder().decode(request.getBase64Content());
            
            String effectiveBlobName = request.getFileName();
            if (!StringUtils.hasText(effectiveBlobName)) {
                effectiveBlobName = "uploaded-" + System.currentTimeMillis();
            }
            // Basic sanitization: replace spaces with hyphens
            effectiveBlobName = effectiveBlobName.replaceAll("\\s+", "-");
            
            String contentType = request.getContentType();
            if (!StringUtils.hasText(contentType)) {
                contentType = "application/octet-stream";
            }
            
            logger.info("Processing base64 upload. Blob name: {}, Content type: {}, Content size: {} bytes", 
                    effectiveBlobName, contentType, decodedContent.length);
            
            String blobUrl = azureBlobService.uploadFile(
                new ByteArrayInputStream(decodedContent),
                effectiveBlobName,
                contentType
            );
            
            response.put("success", true);
            response.put("message", "File uploaded successfully.");
            response.put("blobUrl", blobUrl);
            response.put("blobName", effectiveBlobName);
            response.put("contentType", contentType);
            response.put("size", decodedContent.length);
            
            logger.info("Base64 upload successful: {}. URL: {}", effectiveBlobName, blobUrl);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid base64 content for file: {}, Error: {}", request.getFileName(), e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Invalid base64 content: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (IOException e) {
            logger.error("Failed to upload base64 content for file: {}, Error: {}", request.getFileName(), e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to upload file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (Exception e) {
            logger.error("Unexpected error during base64 upload for file: {}, Error: {}", request.getFileName(), e.getMessage(), e);
            response.put("success", false);
            response.put("message", "An unexpected error occurred during upload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * List all blobs in the container
     * 
     * @return ResponseEntity with list of blob names
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listBlobs() {
        logger.info("Received request to list all blobs");
        
        Map<String, Object> response = new HashMap<>();
        List<String> blobs = azureBlobService.listBlobs();
        
        response.put("success", true);
        response.put("blobs", blobs);
        response.put("count", blobs.size());
        
        logger.info("Returned list of {} blobs", blobs.size());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Delete a blob from the container
     * 
     * @param blobName Name of the blob to delete
     * @return ResponseEntity with deletion result
     */
    @DeleteMapping("/{blobName}")
    public ResponseEntity<Map<String, Object>> deleteBlob(@PathVariable String blobName) {
        logger.info("Received request to delete blob: {}", blobName);
        
        Map<String, Object> response = new HashMap<>();
        boolean deleted = azureBlobService.deleteBlob(blobName);
        
        if (deleted) {
            response.put("success", true);
            response.put("message", "Blob deleted successfully");
            logger.info("Blob deleted successfully: {}", blobName);
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Failed to delete blob or blob does not exist");
            logger.warn("Failed to delete blob: {}", blobName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }
    
    /**
     * Download a blob to the local filesystem
     * 
     * @param blobName Name of the blob to download
     * @param destinationPath Local path where the blob should be downloaded
     * @return ResponseEntity with download result
     */
    @GetMapping("/download")
    public ResponseEntity<Map<String, Object>> downloadBlob(
            @RequestParam String blobName,
            @RequestParam String destinationPath) {
        
        logger.info("Received request to download blob: {} to path: {}", blobName, destinationPath);
        
        Map<String, Object> response = new HashMap<>();
        boolean downloaded = azureBlobService.downloadBlob(blobName, destinationPath);
        
        if (downloaded) {
            response.put("success", true);
            response.put("message", "Blob downloaded successfully");
            response.put("destinationPath", destinationPath);
            logger.info("Blob downloaded successfully to: {}", destinationPath);
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Failed to download blob or blob does not exist");
            logger.warn("Failed to download blob: {}", blobName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }
    
    /**
     * Get the content of a blob as a string
     * 
     * @param blobName Name of the blob
     * @return ResponseEntity with blob content
     */
    @GetMapping("/content/{blobName}")
    public ResponseEntity<Map<String, Object>> getBlobContent(@PathVariable String blobName) {
        logger.info("Received request to get content of blob: {}", blobName);
        
        Map<String, Object> response = new HashMap<>();
        String content = azureBlobService.getBlobContent(blobName);
        
        if (content != null) {
            response.put("success", true);
            response.put("blobName", blobName);
            response.put("content", content);
            logger.info("Successfully retrieved content for blob: {}", blobName);
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "Failed to get blob content or blob does not exist");
            logger.warn("Failed to get content for blob: {}", blobName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }
}
