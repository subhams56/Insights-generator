package com.insights.generator.repository;

import com.insights.generator.model.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryLogRepository extends JpaRepository<QueryLog, Long> {
}