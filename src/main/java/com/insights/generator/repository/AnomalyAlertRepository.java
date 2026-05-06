package com.insights.generator.repository;

import com.insights.generator.model.AnomalyAlert;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AnomalyAlertRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnomalyAlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(AnomalyAlert alert) {
        String sql = "INSERT INTO anomaly_alerts (severity, title, message, raw_data) VALUES (?, ?, ?, ?::jsonb)";
        jdbcTemplate.update(sql, alert.getSeverity(), alert.getTitle(), alert.getMessage(), alert.getRawData());
    }

    public List<Map<String, Object>> getUnreadAlerts() {
        String sql = "SELECT * FROM anomaly_alerts WHERE is_read = false ORDER BY created_at DESC";
        return jdbcTemplate.queryForList(sql);
    }

    public void markAsRead(Long id) {
        String sql = "UPDATE anomaly_alerts SET is_read = true WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    // In AnomalyAlertRepository.java
    public boolean recentAlertExistsForTopic(String titleKeyword, int hoursToSuppress) {
        String sql = """
        SELECT COUNT(*) FROM anomaly_alerts 
        WHERE title LIKE ? 
        AND created_at > NOW() - (INTERVAL '1 hour' * ?)
        """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, "%" + titleKeyword + "%", hoursToSuppress);
        return count != null && count > 0;
    }
}