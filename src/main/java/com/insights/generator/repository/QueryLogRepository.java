package com.insights.generator.repository;

import com.insights.generator.model.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QueryLogRepository extends JpaRepository<QueryLog, Long> {
    Optional<QueryLog> findFirstByQuestionOrderByCreatedAtDesc(String question);
}