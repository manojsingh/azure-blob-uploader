package com.example.uploader.service;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlobHttpHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class AzureBlobService {
    
    private static final Logger logger = LoggerFactory.getLogger(AzureBlobService.class);
    
    @Autowired
    private BlobContainerClient blobContainerClient;
    
    /**
     * Upload a file from local filesystem to Azure Blob Storage
     * 
     * @param localFilePath Path to the file on the local filesystem
     * @param blobName Name to be given to the blob (if null, uses the filename)
     * @return URL of the uploaded blob
     * @throws IOException If the file cannot be read
     * @throws IllegalArgumentException If the file path is invalid
     */
    public String uploadFile(String localFilePath, String blobName) throws IOException, IllegalArgumentException {
        logger.info("Uploading file from local path: {}", localFilePath);
        
        // Validate input
        if (!StringUtils.hasText(localFilePath)) {
            throw new IllegalArgumentException("Local file path cannot be empty");
        }
        
        Path path = Paths.get(localFilePath);
        File file = path.toFile();
        
        // Validate file exists and is readable
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + localFilePath);
        }
        
        if (!file.canRead()) {
            throw new IllegalArgumentException("Cannot read file: " + localFilePath);
        }
        
        // If blobName is not provided, use the filename
        if (!StringUtils.hasText(blobName)) {
            blobName = file.getName();
        }
        
        try {
            // Get a reference to the blob
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            
            // Upload the file
            blobClient.uploadFromFile(localFilePath, true);
            
            logger.info("File successfully uploaded to blob: {}", blobName);
            return blobClient.getBlobUrl();
        } catch (BlobStorageException e) {
            logger.error("Failed to upload file to Azure Blob Storage", e);
            throw new IOException("Failed to upload file to Azure Blob Storage: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads data from an InputStream to Azure Blob Storage.
     *
     * @param data The InputStream containing the data to upload.
     * @param blobName The name to give the blob in Azure Storage.
     * @param contentType The MIME type of the content being uploaded.
     * @return URL of the uploaded blob.
     * @throws IOException If an I/O error occurs during upload.
     * @throws IllegalArgumentException If blobName is empty.
     */
    public String uploadFile(InputStream data, String blobName, String contentType) throws IOException, IllegalArgumentException {
        logger.info("Uploading stream to blob: {}. ContentType: {}", blobName, contentType);
        
        if (!StringUtils.hasText(blobName)) {
            throw new IllegalArgumentException("Blob name cannot be empty for stream upload.");
        }

        try {
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            
            BlobHttpHeaders blobHttpHeaders = null;
            if (StringUtils.hasText(contentType)) {
                blobHttpHeaders = new BlobHttpHeaders().setContentType(contentType);
            }

            // Upload the stream. The 'true' flag means overwrite if exists.
            blobClient.upload(data, data.available(), true); 
            
            // Set HTTP headers after the upload is complete.
            if (blobHttpHeaders != null) {
                blobClient.setHttpHeaders(blobHttpHeaders);
            }

            logger.info("Stream successfully uploaded to blob: {}", blobName);
            return blobClient.getBlobUrl();
        } catch (BlobStorageException e) {
            logger.error("Failed to upload stream to Azure Blob Storage. Blob: {}, Error: {}", blobName, e.getMessage(), e);
            throw new IOException("Failed to upload stream to Azure Blob Storage: " + e.getMessage(), e);
        }
    }
    
    /**
     * List all blobs in the container
     * 
     * @return List of blob names
     */
    public List<String> listBlobs() {
        logger.info("Listing blobs in container: {}", blobContainerClient.getBlobContainerName());
        
        List<String> blobNames = new ArrayList<>();
        try {
            // List all blobs and add their names to the list
            for (BlobItem blobItem : blobContainerClient.listBlobs()) {
                blobNames.add(blobItem.getName());
            }
            
            logger.info("Found {} blobs in container", blobNames.size());
            return blobNames;
        } catch (BlobStorageException e) {
            logger.error("Failed to list blobs", e);
            return blobNames; // Return empty list in case of error
        }
    }
    
    /**
     * Delete a blob from the container
     * 
     * @param blobName Name of the blob to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteBlob(String blobName) {
        logger.info("Deleting blob: {}", blobName);
        
        // Validate input
        if (!StringUtils.hasText(blobName)) {
            logger.error("Blob name cannot be empty");
            return false;
        }
        
        try {
            // Get a reference to the blob and delete it
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            
            if (!blobClient.exists()) {
                logger.warn("Blob does not exist: {}", blobName);
                return false;
            }
            
            blobClient.delete();
            logger.info("Blob successfully deleted: {}", blobName);
            return true;
        } catch (BlobStorageException e) {
            logger.error("Failed to delete blob: {}", blobName, e);
            return false;
        }
    }
    
    /**
     * Download a blob to the local filesystem
     * 
     * @param blobName Name of the blob to download
     * @param destinationPath Local path where the blob should be downloaded
     * @return true if download was successful, false otherwise
     */
    public boolean downloadBlob(String blobName, String destinationPath) {
        logger.info("Downloading blob: {} to path: {}", blobName, destinationPath);
        
        // Validate input
        if (!StringUtils.hasText(blobName) || !StringUtils.hasText(destinationPath)) {
            logger.error("Blob name and destination path cannot be empty");
            return false;
        }
        
        try {
            // Get a reference to the blob
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            
            if (!blobClient.exists()) {
                logger.warn("Blob does not exist: {}", blobName);
                return false;
            }
            
            // Create the destination directory if it doesn't exist
            Path destinationFile = Paths.get(destinationPath);
            Files.createDirectories(destinationFile.getParent());
            
            // Download the blob
            blobClient.downloadToFile(destinationPath, true);
            
            logger.info("Blob successfully downloaded to: {}", destinationPath);
            return true;
        } catch (BlobStorageException | IOException e) {
            logger.error("Failed to download blob: {}", blobName, e);
            return false;
        }
    }
    
    /**
     * Get the content of a blob as a string
     * 
     * @param blobName Name of the blob
     * @return The content of the blob as a string, or null if an error occurs
     */
    public String getBlobContent(String blobName) {
        logger.info("Getting content of blob: {}", blobName);
        
        // Validate input
        if (!StringUtils.hasText(blobName)) {
            logger.error("Blob name cannot be empty");
            return null;
        }
        
        try {
            // Get a reference to the blob
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            
            if (!blobClient.exists()) {
                logger.warn("Blob does not exist: {}", blobName);
                return null;
            }
            
            // Download the blob and convert to string
            BinaryData blobData = blobClient.downloadContent();
            return blobData.toString();
        } catch (BlobStorageException e) {
            logger.error("Failed to get blob content: {}", blobName, e);
            return null;
        }
    }
}
