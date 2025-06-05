package com.example.uploader.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Base64UploadRequest {
    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("contentType")
    private String contentType;

    @JsonProperty("base64Content")
    private String base64Content;

    // Getters and setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getBase64Content() {
        return base64Content;
    }

    public void setBase64Content(String base64Content) {
        this.base64Content = base64Content;
    }
}
