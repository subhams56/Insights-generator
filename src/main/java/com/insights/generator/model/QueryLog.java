package com.insights.generator.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "query_logs")
public class QueryLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String response; // This stores the NL "Answer"

    @Column(columnDefinition = "TEXT")
    private String rawData; // This stores the JSON results string

    private LocalDateTime createdAt = LocalDateTime.now();

    public QueryLog() {}

    public QueryLog(String question, String response, String rawData) {
        this.question = question;
        this.response = response;
        this.rawData = rawData;
    }

    public Long getId() { return id; }
    public String getQuestion() { return question; }
    public String getResponse() { return response; }
    public String getRawData() { return rawData; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}