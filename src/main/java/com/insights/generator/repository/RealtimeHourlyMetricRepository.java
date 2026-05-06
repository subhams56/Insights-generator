package com.insights.generator.repository;

import com.insights.generator.model.RealtimeHourlyMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RealtimeHourlyMetricRepository
        extends JpaRepository<RealtimeHourlyMetric, Long> {

    @Query(value = """
            SELECT *
            FROM realtime_hourly_metrics
            WHERE severity != 'NORMAL'
            ORDER BY created_at DESC
            LIMIT 20
            """, nativeQuery = true)
    List<RealtimeHourlyMetric> getLatestRealtimeAlerts();

    @Query(value = """
        SELECT COUNT(DISTINCT city)
        FROM realtime_hourly_metrics
        """, nativeQuery = true)
    Long countTotalRegions();

    @Query(value = """
        SELECT COUNT(*)
        FROM realtime_hourly_metrics
        WHERE severity = 'CRITICAL'
        """, nativeQuery = true)
    Long countCriticalRegions();

    @Query(value = """
        SELECT COUNT(*)
        FROM realtime_hourly_metrics
        WHERE severity = 'WARNING'
        """, nativeQuery = true)
    Long countWarningRegions();

    @Query(value = """
        SELECT COUNT(*)
        FROM realtime_hourly_metrics
        WHERE severity = 'NORMAL'
        """, nativeQuery = true)
    Long countNormalRegions();

    @Query(value = """
        SELECT COALESCE(SUM(active_alerts), 0)
        FROM realtime_hourly_metrics
        """, nativeQuery = true)
    Long countActiveAlerts();

    @Query(value = """
        SELECT AVG(avg_quality_score)
        FROM realtime_hourly_metrics
        """, nativeQuery = true)
    Double getAverageNetworkHealth();

    @Query(value = """
        SELECT city
        FROM realtime_hourly_metrics
        ORDER BY active_alerts DESC
        LIMIT 1
        """, nativeQuery = true)
    String getTopAffectedRegion();

    @Query(value = """
        SELECT MAX(created_at)
        FROM realtime_hourly_metrics
        """, nativeQuery = true)
    LocalDateTime getLastUpdated();
}