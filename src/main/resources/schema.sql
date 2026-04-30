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
        timestamp TIMESTAMP,
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
        quality_score NUMERIC
    );