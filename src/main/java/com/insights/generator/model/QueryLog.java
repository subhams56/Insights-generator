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
    private String response;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Default constructor for Hibernate
    public QueryLog() {}

    public QueryLog(String question, String response) {
        this.question = question;
        this.response = response;
    }

    // JACKSON NEEDS THESE TO SEE THE DATA:
    public Long getId() { return id; }
    public String getQuestion() { return question; }
    public String getResponse() { return response; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}