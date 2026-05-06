CREATE TABLE IF NOT EXISTS network_metrics (
                                               timestamp TIMESTAMP,
                                               region_id VARCHAR(50),
    cell_id VARCHAR(50),
    avg_latency_ms NUMERIC,
    download_speed_mbps NUMERIC,
    upload_speed_mbps NUMERIC,
    packet_loss_pct NUMERIC,
    active_users INTEGER
    );



CREATE TABLE IF NOT EXISTS refined_network_metrics (
    "timestamp" TIMESTAMP,
    hour_of_day INTEGER,
    is_peak_hour INTEGER,
    region VARCHAR(50),
    state VARCHAR(10),
    city VARCHAR(100),
    network_band VARCHAR(20),
    environment_type VARCHAR(20),
    avg_latency_ms NUMERIC,
    download_speed_mbps NUMERIC,
    upload_speed_mbps NUMERIC,
    packet_loss_pct NUMERIC,
    active_users INTEGER,
    network_utilization_pct NUMERIC,
    congestion_level VARCHAR(20),
    dropped_calls INTEGER,
    weather_condition VARCHAR(20),
    quality_score NUMERIC,
    device_model VARCHAR(100),
    carrier VARCHAR(50)
);


CREATE TABLE IF NOT EXISTS anomaly_alerts (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    severity VARCHAR(20), -- e.g., 'CRITICAL', 'WARNING'
    title VARCHAR(100),   -- e.g., 'Severe Degradation in NY'
    message TEXT,         -- The LLM-generated executive summary
    raw_data JSONB,       -- Storing the SQL result that triggered it
    is_read BOOLEAN DEFAULT FALSE
);


CREATE TABLE realtime_hourly_metrics (

    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP,
    state VARCHAR(20),
    city VARCHAR(100),
    network_band VARCHAR(50),
    avg_latency_ms NUMERIC,
    avg_download_speed_mbps NUMERIC,
    avg_packet_loss_pct NUMERIC,
    avg_quality_score NUMERIC,
    total_active_users INTEGER,
    congestion_level VARCHAR(20),
    active_alerts INTEGER,
    severity VARCHAR(20),
    summary TEXT
);