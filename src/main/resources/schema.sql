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