package com.insights.generator.repository;

import com.insights.generator.model.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QueryLogRepository extends JpaRepository<QueryLog, Long> {
    Optional<QueryLog> findFirstByQuestionOrderByCreatedAtDesc(String question);

    // This strips spaces and punctuation from the DB column and compares it to your cleaned input
    @Query(value = """
            SELECT * FROM query_logs 
            WHERE REGEXP_REPLACE(LOWER(question), '[^a-z0-9]', '', 'g') = :cleanQuestion 
            ORDER BY created_at DESC 
            LIMIT 1
            """, nativeQuery = true)
    Optional<QueryLog> findSmartLexicalMatch(@Param("cleanQuestion") String cleanQuestion);
}