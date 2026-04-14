package com.insights.generator.repository;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Repository
public class SafeSqlExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SafeSqlExecutor.class);
    private final JdbcClient jdbcClient;

    public SafeSqlExecutor(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> executeReadOnlyQuery(String sql) {
        String cleanSql = sql.trim();

        // Hard security constraint: Only SELECT queries are allowed
        if (!cleanSql.toUpperCase().startsWith("SELECT")) {
            logger.warn("Attempted to execute non-SELECT query: {}", cleanSql);
            throw new SecurityException("Only SELECT queries are permitted.");
        }

        logger.debug("Executing GenAI SQL: {}", cleanSql);
        return jdbcClient.sql(cleanSql).query().listOfRows();
    }
}