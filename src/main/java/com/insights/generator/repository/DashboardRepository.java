package com.insights.generator.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public DashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 1. Top-level KPIs
    public Map<String, Object> getAggregateKPIs() {
        String sql = """
            SELECT 
                ROUND(AVG(quality_score), 2) AS avg_quality_score,
                SUM(dropped_calls) AS total_dropped_calls,
                ROUND(AVG(avg_latency_ms), 2) AS avg_latency_ms,
                ROUND(AVG(download_speed_mbps), 2) AS avg_download_speed
            FROM refined_network_metrics
            """;
        return jdbcTemplate.queryForMap(sql);
    }

    // 2. Hourly Utilization Trend
    public List<Map<String, Object>> getHourlyUtilizationTrend() {
        String sql = """
            SELECT 
                hour_of_day, 
                ROUND(AVG(network_utilization_pct), 2) AS avg_utilization,
                ROUND(AVG(active_users), 0) AS avg_active_users
            FROM refined_network_metrics
            GROUP BY hour_of_day
            ORDER BY hour_of_day ASC
            """;
        return jdbcTemplate.queryForList(sql);
    }

    // 3. Network Band Performance
    public List<Map<String, Object>> getBandPerformance() {
        String sql = """
            SELECT 
                network_band,
                ROUND(AVG(download_speed_mbps), 2) AS avg_download_speed,
                ROUND(AVG(packet_loss_pct), 2) AS avg_packet_loss,
                SUM(dropped_calls) AS total_dropped_calls
            FROM refined_network_metrics
            GROUP BY network_band
            ORDER BY avg_download_speed DESC
            """;
        return jdbcTemplate.queryForList(sql);
    }

    // 4. Worst Performing States (Top 5)
    public List<Map<String, Object>> getWorstPerformingStates() {
        String sql = """
            SELECT 
                state,
                SUM(dropped_calls) AS total_dropped_calls,
                ROUND(AVG(packet_loss_pct), 2) AS avg_packet_loss
            FROM refined_network_metrics
            GROUP BY state
            ORDER BY total_dropped_calls DESC
            LIMIT 5
            """;
        return jdbcTemplate.queryForList(sql);
    }

    // 5. NEW: Carrier Performance Breakdown
    public List<Map<String, Object>> getCarrierPerformance() {
        String sql = """
            SELECT 
                carrier,
                ROUND(AVG(quality_score), 2) AS avg_quality_score,
                ROUND(AVG(dropped_calls), 2) AS avg_dropped_calls,
                ROUND(AVG(download_speed_mbps), 2) AS avg_download_speed
            FROM refined_network_metrics
            GROUP BY carrier
            ORDER BY avg_quality_score DESC
            """;
        return jdbcTemplate.queryForList(sql);
    }
}