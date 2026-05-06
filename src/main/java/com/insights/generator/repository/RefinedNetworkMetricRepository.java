package com.insights.generator.repository;


import com.insights.generator.model.RefinedNetworkMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefinedNetworkMetricRepository extends JpaRepository<RefinedNetworkMetric, Long> {
}