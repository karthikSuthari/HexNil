-- ============================================================================
-- Refresh Rate Intelligence Tables for HexNil
-- Run this to add the new tables to an existing hexnil database
-- ============================================================================

-- Refresh rate sessions (one row per foreground app/home session)
CREATE TABLE IF NOT EXISTS refresh_rate_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    session_type VARCHAR(16),
    package_name VARCHAR(256),
    app_name VARCHAR(256),
    start_time BIGINT NOT NULL,
    end_time BIGINT,
    duration_ms BIGINT DEFAULT 0,
    start_refresh_rate_hz FLOAT DEFAULT 0,
    end_refresh_rate_hz FLOAT DEFAULT 0,
    avg_refresh_rate_hz FLOAT DEFAULT 0,
    min_refresh_rate_hz FLOAT DEFAULT 0,
    max_refresh_rate_hz FLOAT DEFAULT 0,
    time_at_high_hz_ms BIGINT DEFAULT 0,
    time_at_low_hz_ms BIGINT DEFAULT 0,
    is_adaptive_display BOOLEAN DEFAULT FALSE,
    avg_brightness FLOAT DEFAULT 0,
    avg_cpu_usage FLOAT DEFAULT 0,
    avg_gpu_usage FLOAT DEFAULT 0,
    avg_battery_current FLOAT DEFAULT 0,
    avg_temperature FLOAT DEFAULT 0,
    estimated_battery_cost FLOAT DEFAULT 0,
    anomaly_count INT DEFAULT 0,
    has_strong_anomaly BOOLEAN DEFAULT FALSE,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_refresh_sessions_device (device_id),
    INDEX idx_refresh_sessions_start_time (start_time),
    INDEX idx_refresh_sessions_package (package_name(64)),
    INDEX idx_refresh_sessions_type (session_type),
    INDEX idx_refresh_sessions_anomaly (has_strong_anomaly),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Refresh rate anomalies (deterministic cause classification)
CREATE TABLE IF NOT EXISTS refresh_rate_anomalies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    linked_session_id BIGINT,
    package_name VARCHAR(256),
    session_type VARCHAR(16),
    detected_at BIGINT NOT NULL,
    anomaly_type VARCHAR(64),
    severity VARCHAR(16),
    confidence_score FLOAT DEFAULT 0,
    cause_category VARCHAR(64),
    cause_explanation TEXT,
    evidence_metrics_json TEXT,
    refresh_rate_hz FLOAT DEFAULT 0,
    cpu_usage FLOAT DEFAULT 0,
    gpu_usage FLOAT DEFAULT 0,
    temperature FLOAT DEFAULT 0,
    brightness FLOAT DEFAULT 0,
    battery_current FLOAT DEFAULT 0,
    was_charging BOOLEAN DEFAULT FALSE,
    screen_on BOOLEAN DEFAULT TRUE,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_refresh_anomalies_device (device_id),
    INDEX idx_refresh_anomalies_session (linked_session_id),
    INDEX idx_refresh_anomalies_detected_at (detected_at),
    INDEX idx_refresh_anomalies_type (anomaly_type),
    INDEX idx_refresh_anomalies_severity (severity),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

SELECT 'Refresh Rate tables created successfully!' AS status;
SHOW TABLES LIKE 'refresh_rate%';
