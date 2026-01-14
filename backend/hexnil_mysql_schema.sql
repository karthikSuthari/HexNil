-- ============================================================================
-- HexNil MySQL Schema - Production Grade
-- Database: hexnil
-- Created: 2026-01-13
-- ============================================================================

-- Create database
CREATE DATABASE IF NOT EXISTS hexnil
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE hexnil;

-- ============================================================================
-- RAW DATA TABLES (Never modified by AI)
-- ============================================================================

-- Device registration table
CREATE TABLE IF NOT EXISTS devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL UNIQUE,
    device_model VARCHAR(128),
    android_version VARCHAR(32),
    app_version VARCHAR(32),
    status VARCHAR(16) DEFAULT 'ACTIVE',
    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_device_id (device_id),
    INDEX idx_last_seen (last_seen),
    INDEX idx_status (status)
) ENGINE=InnoDB;

-- Battery snapshots (periodic battery state readings)
CREATE TABLE IF NOT EXISTS battery_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    battery_level INT NOT NULL,
    temperature FLOAT,
    voltage FLOAT,
    current_now FLOAT,
    is_charging BOOLEAN DEFAULT FALSE,
    charge_type VARCHAR(32),
    screen_on BOOLEAN DEFAULT FALSE,
    power_save_mode BOOLEAN DEFAULT FALSE,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_timestamp (timestamp),
    INDEX idx_received_at (received_at),
    UNIQUE KEY uk_device_timestamp (device_id, timestamp),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Charging sessions (start to end of charging)
CREATE TABLE IF NOT EXISTS charging_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    start_level INT NOT NULL,
    end_level INT,
    start_time BIGINT NOT NULL,
    end_time BIGINT,
    charge_type VARCHAR(32),
    max_temperature FLOAT,
    avg_temperature FLOAT,
    energy_added_mah FLOAT,
    charging_speed_ma FLOAT,
    is_complete BOOLEAN DEFAULT FALSE,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_session_id (session_id),
    INDEX idx_received_at (received_at),
    UNIQUE KEY uk_device_session (device_id, session_id),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Thermal events (temperature spikes/anomalies)
CREATE TABLE IF NOT EXISTS thermal_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    temperature FLOAT NOT NULL,
    threshold_exceeded FLOAT,
    duration_seconds INT,
    trigger_source VARCHAR(64),
    cpu_usage FLOAT,
    foreground_app VARCHAR(128),
    severity VARCHAR(16),
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_severity (severity),
    INDEX idx_received_at (received_at),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Drain events (rapid battery drain incidents)
CREATE TABLE IF NOT EXISTS drain_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    start_level INT NOT NULL,
    end_level INT NOT NULL,
    duration_minutes INT NOT NULL,
    drain_rate_per_hour FLOAT NOT NULL,
    screen_on_time_minutes INT,
    suspected_cause VARCHAR(128),
    top_apps JSON,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_drain_rate (drain_rate_per_hour),
    INDEX idx_received_at (received_at),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Idle drain sessions (overnight/idle battery drain)
CREATE TABLE IF NOT EXISTS idle_drain_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    start_level INT NOT NULL,
    end_level INT NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NOT NULL,
    total_drain INT NOT NULL,
    drain_rate_per_hour FLOAT NOT NULL,
    screen_off_duration_minutes INT,
    doze_time_minutes INT,
    wake_locks_count INT,
    network_activity_mb FLOAT,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_session_id (session_id),
    INDEX idx_drain_rate (drain_rate_per_hour),
    INDEX idx_received_at (received_at),
    UNIQUE KEY uk_device_session (device_id, session_id),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Battery health records
CREATE TABLE IF NOT EXISTS battery_health (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    health_percentage FLOAT,
    cycle_count INT,
    design_capacity_mah INT,
    current_capacity_mah INT,
    charge_full_design INT,
    charge_full INT,
    technology VARCHAR(32),
    manufacture_date VARCHAR(32),
    first_use_date BIGINT,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_health (health_percentage),
    INDEX idx_received_at (received_at),
    UNIQUE KEY uk_device_timestamp (device_id, timestamp),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Safety events (critical warnings)
CREATE TABLE IF NOT EXISTS safety_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    temperature FLOAT,
    voltage FLOAT,
    current_ma FLOAT,
    description TEXT,
    action_taken VARCHAR(128),
    user_notified BOOLEAN DEFAULT FALSE,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_event_type (event_type),
    INDEX idx_severity (severity),
    INDEX idx_received_at (received_at),
    UNIQUE KEY uk_device_event (device_id, timestamp, event_type, severity),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Calibration data
CREATE TABLE IF NOT EXISTS calibration_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    calibration_type VARCHAR(64) NOT NULL,
    old_value FLOAT,
    new_value FLOAT,
    accuracy_score FLOAT,
    reference_source VARCHAR(64),
    is_automatic BOOLEAN DEFAULT TRUE,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_calibration_type (calibration_type),
    INDEX idx_received_at (received_at),
    UNIQUE KEY uk_device_timestamp_type (device_id, timestamp, calibration_type),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- AI analysis results from Android (on-device AI)
CREATE TABLE IF NOT EXISTS ai_analysis_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    analysis_type VARCHAR(64) NOT NULL,
    confidence_score FLOAT,
    prediction JSON,
    features_used JSON,
    model_version VARCHAR(32),
    execution_time_ms INT,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_analysis_type (analysis_type),
    INDEX idx_received_at (received_at),
    UNIQUE KEY uk_device_analysis (device_id, timestamp, analysis_type, model_version),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Data sync tracking
CREATE TABLE IF NOT EXISTS sync_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    sync_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_type VARCHAR(64) NOT NULL,
    records_synced INT NOT NULL,
    bytes_transferred INT,
    sync_duration_ms INT,
    status VARCHAR(16) NOT NULL,
    error_message TEXT,
    INDEX idx_device_timestamp (device_id, sync_timestamp),
    INDEX idx_data_type (data_type),
    INDEX idx_status (status),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================================
-- AI DERIVED TABLES (AI can write to these)
-- ============================================================================

-- Hourly aggregated metrics
CREATE TABLE IF NOT EXISTS ai_hourly_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    hour_timestamp TIMESTAMP NOT NULL,
    computed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    avg_battery_level FLOAT,
    min_battery_level INT,
    max_battery_level INT,
    total_drain INT,
    drain_rate_per_hour FLOAT,
    avg_temperature FLOAT,
    max_temperature FLOAT,
    screen_on_minutes INT,
    charging_minutes INT,
    snapshot_count INT,
    thermal_events_count INT,
    drain_events_count INT,
    INDEX idx_device_hour (device_id, hour_timestamp),
    UNIQUE KEY uk_device_hour (device_id, hour_timestamp),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Daily aggregated metrics
CREATE TABLE IF NOT EXISTS ai_daily_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    date_timestamp DATE NOT NULL,
    computed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    avg_battery_level FLOAT,
    min_battery_level INT,
    max_battery_level INT,
    total_drain INT,
    avg_drain_rate_per_hour FLOAT,
    avg_temperature FLOAT,
    max_temperature FLOAT,
    screen_on_time_hours FLOAT,
    charging_time_hours FLOAT,
    full_charges INT,
    partial_charges INT,
    thermal_events_count INT,
    drain_events_count INT,
    safety_events_count INT,
    estimated_health_score FLOAT,
    INDEX idx_device_date (device_id, date_timestamp),
    UNIQUE KEY uk_device_date (device_id, date_timestamp),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Anomaly detection results
CREATE TABLE IF NOT EXISTS ai_anomalies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    anomaly_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    confidence_score FLOAT NOT NULL,
    source_table VARCHAR(64),
    source_id BIGINT,
    metric_name VARCHAR(64),
    expected_value FLOAT,
    actual_value FLOAT,
    deviation_percent FLOAT,
    description TEXT,
    is_acknowledged BOOLEAN DEFAULT FALSE,
    INDEX idx_device_detected (device_id, detected_at),
    INDEX idx_anomaly_type (anomaly_type),
    INDEX idx_severity (severity),
    UNIQUE KEY uk_anomaly_source (device_id, anomaly_type, source_table, source_id),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Device risk scores
CREATE TABLE IF NOT EXISTS ai_risk_scores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    computed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    overall_risk_score FLOAT NOT NULL,
    thermal_risk_score FLOAT,
    drain_risk_score FLOAT,
    health_risk_score FLOAT,
    safety_risk_score FLOAT,
    charging_risk_score FLOAT,
    risk_level VARCHAR(16) NOT NULL,
    contributing_factors JSON,
    INDEX idx_device_computed (device_id, computed_at),
    INDEX idx_risk_level (risk_level),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- AI recommendations
CREATE TABLE IF NOT EXISTS ai_recommendations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    recommendation_type VARCHAR(64) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT NOT NULL,
    action_items JSON,
    expected_benefit VARCHAR(256),
    related_anomaly_id BIGINT,
    is_sent BOOLEAN DEFAULT FALSE,
    is_dismissed BOOLEAN DEFAULT FALSE,
    INDEX idx_device_created (device_id, created_at),
    INDEX idx_priority (priority),
    INDEX idx_type (recommendation_type),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
    FOREIGN KEY (related_anomaly_id) REFERENCES ai_anomalies(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- Battery degradation predictions
CREATE TABLE IF NOT EXISTS ai_degradation_predictions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    predicted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    current_health_percent FLOAT,
    predicted_health_30d FLOAT,
    predicted_health_90d FLOAT,
    predicted_health_180d FLOAT,
    days_to_80_percent INT,
    days_to_70_percent INT,
    degradation_rate_per_month FLOAT,
    confidence_score FLOAT,
    model_version VARCHAR(32),
    factors_json JSON,
    INDEX idx_device_predicted (device_id, predicted_at),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- AI processing log
CREATE TABLE IF NOT EXISTS ai_processing_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    process_type VARCHAR(64) NOT NULL,
    device_id VARCHAR(64),
    records_processed INT DEFAULT 0,
    records_created INT DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    error_message TEXT,
    execution_time_ms INT,
    INDEX idx_started_at (started_at),
    INDEX idx_process_type (process_type),
    INDEX idx_status (status)
) ENGINE=InnoDB;

-- Watermarks so AI can deterministically process only new raw data
CREATE TABLE IF NOT EXISTS ai_watermarks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    source_table VARCHAR(64) NOT NULL,
    last_processed_id BIGINT DEFAULT 0,
    last_processed_ts BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_source (device_id, source_table),
    INDEX idx_updated_at (updated_at),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================================
-- USER MANAGEMENT (Security)
-- ============================================================================

-- Create backend user (full access to raw tables)
CREATE USER IF NOT EXISTS 'hexnil_backend'@'localhost' IDENTIFIED BY 'HexNil_Backend_2026!';

GRANT SELECT, INSERT, UPDATE ON hexnil.devices TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.battery_snapshots TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.charging_sessions TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.thermal_events TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.drain_events TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.idle_drain_sessions TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.battery_health TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.safety_events TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.calibration_data TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.ai_analysis_results TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.sync_log TO 'hexnil_backend'@'localhost';

-- Create AI user (restricted - NO DELETE, NO DROP)
CREATE USER IF NOT EXISTS 'hexnil_ai'@'localhost' IDENTIFIED BY 'HexNil_AI_2026!';

-- AI can only SELECT from raw tables
GRANT SELECT ON hexnil.devices TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.battery_snapshots TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.charging_sessions TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.thermal_events TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.drain_events TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.idle_drain_sessions TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.battery_health TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.safety_events TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.calibration_data TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.ai_analysis_results TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.sync_log TO 'hexnil_ai'@'localhost';

-- AI can SELECT, INSERT, UPDATE on AI tables (NO DELETE)
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_hourly_metrics TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_daily_metrics TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_anomalies TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_risk_scores TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_recommendations TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_degradation_predictions TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT ON hexnil.ai_processing_log TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_watermarks TO 'hexnil_ai'@'localhost';

FLUSH PRIVILEGES;

-- ============================================================================
-- VERIFICATION
-- ============================================================================
SELECT 'HexNil MySQL Schema Created Successfully!' AS status;
SHOW TABLES;
