package com.insights.generator.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnomalyAlert {
    private Long id;
    private LocalDateTime createdAt;
    private String severity;
    private String title;
    private String message;
    private String rawData;
    private boolean isRead;

    public AnomalyAlert(String severity, String title, String message, String rawData) {
        this.createdAt = LocalDateTime.now();
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.rawData = rawData;
        this.isRead = false;
    }

//    public Long getId() { return id; }
//    public void setId(Long id) { this.id = id; }
//    public LocalDateTime getCreatedAt() { return createdAt; }
//    public String getSeverity() { return severity; }
//    public String getTitle() { return title; }
//    public String getMessage() { return message; }
//    public String getRawData() { return rawData; }
//    public boolean isRead() { return isRead; }
//    public void setRead(boolean read) { isRead = read; }
}