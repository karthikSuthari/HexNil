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

-- ============================================================================
-- PERFORMANCE MONITORING TABLES (v3.5)
-- ============================================================================

-- Memory snapshots (RAM usage)
CREATE TABLE IF NOT EXISTS memory_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_memory_bytes BIGINT,
    available_memory_bytes BIGINT,
    used_memory_bytes BIGINT,
    used_memory_percent FLOAT,
    is_low_memory BOOLEAN DEFAULT FALSE,
    threshold BIGINT,
    running_app_count INT,
    top_memory_app VARCHAR(256),
    top_memory_app_bytes BIGINT,
    battery_level INT,
    temperature FLOAT,
    is_charging BOOLEAN DEFAULT FALSE,
    is_screen_on BOOLEAN DEFAULT FALSE,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_low_memory (is_low_memory),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- App launch metrics (startup performance)
CREATE TABLE IF NOT EXISTS app_launch_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    package_name VARCHAR(256),
    app_label VARCHAR(128),
    launch_time_ms FLOAT,
    launch_type VARCHAR(16),
    is_slow_launch BOOLEAN DEFAULT FALSE,
    app_version VARCHAR(32),
    android_version VARCHAR(32),
    battery_level INT,
    temperature FLOAT,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_package (package_name(64)),
    INDEX idx_launch_type (launch_type),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Frame metrics (jank and FPS)
CREATE TABLE IF NOT EXISTS frame_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    package_name VARCHAR(256),
    window_name VARCHAR(256),
    total_frames INT,
    slow_frames INT,
    frozen_frames INT,
    jank_percent FLOAT,
    avg_fps FLOAT,
    duration_ms BIGINT,
    is_low_memory_during_session BOOLEAN DEFAULT FALSE,
    max_frame_time_ms FLOAT,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_package (package_name(64)),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Stability events (Crash)
CREATE TABLE IF NOT EXISTS crash_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    package_name VARCHAR(256),
    process_name VARCHAR(256),
    reason VARCHAR(256),
    short_msg TEXT,
    long_msg TEXT,
    stack_trace_hash VARCHAR(64),
    is_foreground BOOLEAN DEFAULT FALSE,
    app_version VARCHAR(32),
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_package (package_name(64)),
    INDEX idx_hash (stack_trace_hash),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Stability events (ANR)
CREATE TABLE IF NOT EXISTS anr_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    package_name VARCHAR(256),
    process_name VARCHAR(256),
    reason VARCHAR(256),
    error_msg TEXT,
    stack_trace_hash VARCHAR(64),
    is_foreground BOOLEAN DEFAULT FALSE,
    app_version VARCHAR(32),
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_package (package_name(64)),
    INDEX idx_hash (stack_trace_hash),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Daily aggregates (Performance Rollups)
CREATE TABLE IF NOT EXISTS daily_aggregates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    aggregate_date DATE NOT NULL,
    package_name VARCHAR(256),
    app_label VARCHAR(128),
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Memory
    avg_memory_used_percent FLOAT,
    max_memory_used_percent FLOAT,
    low_memory_event_count INT,
    
    -- Launch
    launch_count INT,
    cold_launch_count INT,
    avg_cold_launch_ms FLOAT,
    slow_launch_count INT,
    
    -- Stability
    crash_count INT,
    anr_count INT,
    unique_crash_types INT,
    
    -- Thermal/Battery
    avg_temp FLOAT,
    max_temp FLOAT,
    total_battery_drain FLOAT,
    avg_drain_rate_per_hour FLOAT,
    
    -- Health
    health_score FLOAT,
    health_grade VARCHAR(4),
    
    app_version VARCHAR(32),
    payload JSON,
    
    INDEX idx_device_date (device_id, aggregate_date),
    INDEX idx_package (package_name(64)),
    UNIQUE KEY uk_device_date_pkg (device_id, aggregate_date, package_name),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Regression events (Performance Drops)
CREATE TABLE IF NOT EXISTS regression_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    package_name VARCHAR(256),
    regression_type VARCHAR(32),
    severity VARCHAR(16),
    metric_name VARCHAR(64),
    current_value FLOAT,
    baseline_value FLOAT,
    deviation_percent FLOAT,
    is_resolved BOOLEAN DEFAULT FALSE,
    payload JSON,
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_type (regression_type),
    INDEX idx_severity (severity),
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
-- Localhost for local development
CREATE USER IF NOT EXISTS 'hexnil_backend'@'localhost' IDENTIFIED BY 'HexNil_Backend_2026!';
-- Wildcard host for CI/Docker environments
CREATE USER IF NOT EXISTS 'hexnil_backend'@'%' IDENTIFIED BY 'HexNil_Backend_2026!';

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

-- Same grants for wildcard host
GRANT SELECT, INSERT, UPDATE ON hexnil.devices TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.battery_snapshots TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.charging_sessions TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.thermal_events TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.drain_events TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.idle_drain_sessions TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.battery_health TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.safety_events TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.calibration_data TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.ai_analysis_results TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.sync_log TO 'hexnil_backend'@'%';

-- Create AI user (restricted - NO DELETE, NO DROP)
-- Localhost for local development
CREATE USER IF NOT EXISTS 'hexnil_ai'@'localhost' IDENTIFIED BY 'HexNil_AI_2026!';
-- Wildcard host for CI/Docker environments
CREATE USER IF NOT EXISTS 'hexnil_ai'@'%' IDENTIFIED BY 'HexNil_AI_2026!';

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

-- Same grants for wildcard host
GRANT SELECT ON hexnil.devices TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.battery_snapshots TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.charging_sessions TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.thermal_events TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.drain_events TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.idle_drain_sessions TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.battery_health TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.safety_events TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.calibration_data TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.ai_analysis_results TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.sync_log TO 'hexnil_ai'@'%';

-- AI can SELECT, INSERT, UPDATE on AI tables (NO DELETE)
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_hourly_metrics TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_daily_metrics TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_anomalies TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_risk_scores TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_recommendations TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_degradation_predictions TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT ON hexnil.ai_processing_log TO 'hexnil_ai'@'localhost';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_watermarks TO 'hexnil_ai'@'localhost';

-- Same grants for wildcard host
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_hourly_metrics TO 'hexnil_ai'@'%';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_daily_metrics TO 'hexnil_ai'@'%';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_anomalies TO 'hexnil_ai'@'%';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_risk_scores TO 'hexnil_ai'@'%';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_recommendations TO 'hexnil_ai'@'%';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_degradation_predictions TO 'hexnil_ai'@'%';
GRANT SELECT, INSERT ON hexnil.ai_processing_log TO 'hexnil_ai'@'%';
GRANT SELECT, INSERT, UPDATE ON hexnil.ai_watermarks TO 'hexnil_ai'@'%';

FLUSH PRIVILEGES;

-- ============================================================================
-- APP DATA TABLES (From Room DB - Per-App Battery Usage)
-- ============================================================================

-- App Battery Usage - detailed per-app battery statistics (from Room DB app_battery_usage)
CREATE TABLE IF NOT EXISTS app_battery_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    period_start BIGINT NOT NULL,
    period_end BIGINT NOT NULL,
    package_name VARCHAR(256),
    app_name VARCHAR(128),
    is_system_app BOOLEAN DEFAULT FALSE,
    is_preinstalled BOOLEAN DEFAULT FALSE,
    app_category VARCHAR(64),
    foreground_time BIGINT DEFAULT 0,
    background_time BIGINT DEFAULT 0,
    battery_percent_used DOUBLE DEFAULT 0,
    drain_per_hour DOUBLE DEFAULT 0,
    cpu_time_ms DOUBLE DEFAULT 0,
    network_bytes BIGINT DEFAULT 0,
    wakelock_count INT DEFAULT 0,
    wakelock_time BIGINT DEFAULT 0,
    partial_wakelock_time BIGINT DEFAULT 0,
    alarm_wake_count INT DEFAULT 0,
    network_wake_count INT DEFAULT 0,
    wakelock_drain_score DOUBLE DEFAULT 0,
    alarm_count INT DEFAULT 0,
    gps_requests INT DEFAULT 0,
    background_wakes INT DEFAULT 0,
    push_notifications INT DEFAULT 0,
    background_services INT DEFAULT 0,
    has_excessive_background BOOLEAN DEFAULT FALSE,
    category_average DOUBLE DEFAULT 0,
    deviation_from_average DOUBLE DEFAULT 0,
    efficiency_rating VARCHAR(16),
    drain_reasons TEXT,
    optimization_tips TEXT,
    needs_restriction BOOLEAN DEFAULT FALSE,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY idx_app_usage_device_pkg_period (device_id, package_name(100), period_start),
    INDEX idx_app_usage_device (device_id),
    INDEX idx_app_usage_timestamp (timestamp),
    INDEX idx_app_usage_package (package_name(100)),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- App Drain Ranks - top battery draining apps per time window (from Room DB app_drain_ranks)
CREATE TABLE IF NOT EXISTS app_drain_ranks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    window_label VARCHAR(32),
    window_start BIGINT NOT NULL,
    window_end BIGINT NOT NULL,
    rank_position INT NOT NULL,
    package_name VARCHAR(256),
    drain_score DOUBLE DEFAULT 0,
    contribution_pct DOUBLE DEFAULT 0,
    screen_time_ms BIGINT DEFAULT 0,
    foreground_time_ms BIGINT DEFAULT 0,
    background_time_ms BIGINT DEFAULT 0,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY idx_drain_ranks_device_window_rank (device_id, window_start, rank_position),
    INDEX idx_drain_ranks_device (device_id),
    INDEX idx_drain_ranks_package (package_name(100)),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Grant permissions to backend user for App Data tables
GRANT SELECT, INSERT ON hexnil.app_battery_usage TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.app_drain_ranks TO 'hexnil_backend'@'localhost';

-- Same grants for wildcard host
GRANT SELECT, INSERT ON hexnil.app_battery_usage TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.app_drain_ranks TO 'hexnil_backend'@'%';

-- Grant read access to AI user for App Data tables
GRANT SELECT ON hexnil.app_battery_usage TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.app_drain_ranks TO 'hexnil_ai'@'localhost';

-- Same grants for wildcard host
GRANT SELECT ON hexnil.app_battery_usage TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.app_drain_ranks TO 'hexnil_ai'@'%';

FLUSH PRIVILEGES;

-- ============================================================================
-- ADDITIONAL ROOM DB TABLES (Complete Sync)
-- ============================================================================

-- Thermal Heat Buckets - Aggregated thermal patterns
CREATE TABLE IF NOT EXISTS thermal_heat_buckets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NOT NULL,
    temp_bucket VARCHAR(16),
    hour_block INT NOT NULL,
    sample_count INT DEFAULT 0,
    duration_ms BIGINT DEFAULT 0,
    max_temp FLOAT,
    avg_temp FLOAT,
    min_temp FLOAT,
    temp_variance FLOAT,
    charging_count INT DEFAULT 0,
    charging_percent FLOAT DEFAULT 0,
    avg_temp_charging FLOAT,
    avg_temp_not_charging FLOAT,
    avg_charge_current FLOAT,
    fast_charging_count INT DEFAULT 0,
    high_cpu_count INT DEFAULT 0,
    high_gpu_count INT DEFAULT 0,
    avg_cpu_load FLOAT,
    avg_gpu_load FLOAT,
    avg_temp_high_cpu FLOAT,
    avg_temp_high_gpu FLOAT,
    top_foreground_app VARCHAR(256),
    screen_on_count INT DEFAULT 0,
    screen_on_percent FLOAT DEFAULT 0,
    app_temp_contribution_json TEXT,
    gaming_count INT DEFAULT 0,
    camera_count INT DEFAULT 0,
    navigation_count INT DEFAULT 0,
    network_type VARCHAR(32),
    spike_count INT DEFAULT 0,
    sustained_heat_count INT DEFAULT 0,
    warning_event_count INT DEFAULT 0,
    critical_event_count INT DEFAULT 0,
    avg_battery_level FLOAT,
    battery_drain FLOAT,
    degradation_risk FLOAT,
    thermal_score FLOAT,
    exceeds_oem_limit BOOLEAN DEFAULT FALSE,
    oem_compliance_status VARCHAR(16) DEFAULT 'pass',
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_thermal_buckets_device (device_id),
    INDEX idx_thermal_buckets_start_time (start_time),
    INDEX idx_thermal_buckets_temp_bucket (temp_bucket),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Stress Test Results - Battery stress test data
CREATE TABLE IF NOT EXISTS stress_test_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    test_start_time BIGINT NOT NULL,
    test_end_time BIGINT,
    test_type VARCHAR(32),
    test_name VARCHAR(128),
    test_duration_minutes INT,
    test_parameters TEXT,
    high_temperature_scenario BOOLEAN DEFAULT FALSE,
    start_level INT,
    end_level INT,
    drain_amount FLOAT,
    drain_rate FLOAT,
    peak_drain_rate FLOAT,
    start_temperature FLOAT,
    max_temperature FLOAT,
    end_temperature FLOAT,
    avg_temperature FLOAT,
    thermal_throttle_events INT DEFAULT 0,
    thermal_throttle_time BIGINT DEFAULT 0,
    safety_trigger_count INT DEFAULT 0,
    safety_notes TEXT,
    avg_cpu_usage FLOAT,
    max_cpu_usage FLOAT,
    avg_gpu_usage FLOAT,
    max_gpu_usage FLOAT,
    avg_memory_usage FLOAT,
    max_memory_usage FLOAT,
    charge_gained INT DEFAULT 0,
    avg_charging_current FLOAT,
    max_charging_current FLOAT,
    charging_interruptions INT DEFAULT 0,
    test_completed BOOLEAN DEFAULT FALSE,
    crash_count INT DEFAULT 0,
    restart_count INT DEFAULT 0,
    error_count INT DEFAULT 0,
    failure_reasons TEXT,
    drain_score FLOAT,
    thermal_score FLOAT,
    stability_score FLOAT,
    thermal_resilience_score FLOAT,
    charging_stability_score FLOAT,
    overall_score FLOAT,
    baseline_drain FLOAT,
    drain_variance FLOAT,
    baseline_thermal FLOAT,
    thermal_variance FLOAT,
    passed BOOLEAN DEFAULT FALSE,
    verdict VARCHAR(16),
    verdict_details TEXT,
    test_logs TEXT,
    recommendations TEXT,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_stress_test_device (device_id),
    INDEX idx_stress_test_timestamp (timestamp),
    INDEX idx_stress_test_type (test_type),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Update Comparisons - OTA update impact analysis
CREATE TABLE IF NOT EXISTS update_comparisons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    previous_version VARCHAR(64),
    current_version VARCHAR(64),
    update_timestamp BIGINT,
    update_type VARCHAR(32),
    pre_drain_rate FLOAT,
    pre_avg_drain_screen_on FLOAT,
    pre_avg_drain_screen_off FLOAT,
    pre_avg_charging_time FLOAT,
    pre_avg_temperature FLOAT,
    pre_max_temperature FLOAT,
    pre_idle_drain FLOAT,
    pre_battery_health FLOAT,
    post_drain_rate FLOAT,
    post_avg_drain_screen_on FLOAT,
    post_avg_drain_screen_off FLOAT,
    post_avg_charging_time FLOAT,
    post_avg_temperature FLOAT,
    post_max_temperature FLOAT,
    post_idle_drain FLOAT,
    post_battery_health FLOAT,
    drain_rate_delta FLOAT,
    screen_on_drain_delta FLOAT,
    screen_off_drain_delta FLOAT,
    charging_time_delta FLOAT,
    temperature_delta FLOAT,
    thermal_peak_delta FLOAT,
    idle_drain_delta FLOAT,
    drain_rate_change FLOAT,
    screen_on_drain_change FLOAT,
    screen_off_drain_change FLOAT,
    charging_time_change FLOAT,
    temperature_change FLOAT,
    thermal_peak_change FLOAT,
    idle_drain_change FLOAT,
    overall_impact VARCHAR(16),
    impact_score FLOAT,
    impact_details TEXT,
    has_drain_regression BOOLEAN DEFAULT FALSE,
    has_charging_regression BOOLEAN DEFAULT FALSE,
    has_thermal_regression BOOLEAN DEFAULT FALSE,
    has_idle_regression BOOLEAN DEFAULT FALSE,
    recommendation VARCHAR(32),
    analysis_notes TEXT,
    confidence_level FLOAT,
    pre_series_json TEXT,
    post_series_json TEXT,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_update_comp_device (device_id),
    INDEX idx_update_comp_timestamp (timestamp),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Compliance Reports - OEM regulatory compliance
CREATE TABLE IF NOT EXISTS compliance_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    period_start BIGINT,
    period_end BIGINT,
    report_type VARCHAR(32),
    report_version VARCHAR(16),
    generated_by VARCHAR(32),
    device_model VARCHAR(128),
    device_serial VARCHAR(64),
    firmware_version VARCHAR(64),
    battery_part_number VARCHAR(64),
    safety_compliant BOOLEAN DEFAULT TRUE,
    safety_violation_count INT DEFAULT 0,
    safety_violations TEXT,
    safety_score FLOAT,
    max_operating_temp FLOAT,
    max_charging_temp FLOAT,
    temp_compliant BOOLEAN DEFAULT TRUE,
    temp_violation_count INT DEFAULT 0,
    temp_violation_details TEXT,
    min_voltage FLOAT,
    max_voltage FLOAT,
    voltage_compliant BOOLEAN DEFAULT TRUE,
    voltage_violation_count INT DEFAULT 0,
    voltage_violation_details TEXT,
    max_charging_current FLOAT,
    max_discharge_current FLOAT,
    current_compliant BOOLEAN DEFAULT TRUE,
    current_violation_count INT DEFAULT 0,
    current_violation_details TEXT,
    lowest_voltage FLOAT,
    over_discharge_protection_working BOOLEAN DEFAULT TRUE,
    over_discharge_events INT DEFAULT 0,
    highest_voltage FLOAT,
    overcharge_protection_working BOOLEAN DEFAULT TRUE,
    overcharge_events INT DEFAULT 0,
    thermal_protection_working BOOLEAN DEFAULT TRUE,
    thermal_shutdown_events INT DEFAULT 0,
    thermal_throttle_events INT DEFAULT 0,
    iec_compliant BOOLEAN DEFAULT TRUE,
    ul_compliant BOOLEAN DEFAULT TRUE,
    un_compliant BOOLEAN DEFAULT TRUE,
    standards_notes TEXT,
    certification_readiness FLOAT,
    certification_gaps TEXT,
    certification_recommendations TEXT,
    evidence_summary TEXT,
    evidence_ids TEXT,
    evidence_count INT DEFAULT 0,
    audit_trail TEXT,
    data_points_analyzed INT DEFAULT 0,
    data_quality FLOAT,
    executive_summary TEXT,
    technical_details TEXT,
    compliance_matrix TEXT,
    attachments TEXT,
    review_status VARCHAR(16) DEFAULT 'pending',
    review_notes TEXT,
    review_timestamp BIGINT,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_compliance_reports_device (device_id),
    INDEX idx_compliance_reports_timestamp (timestamp),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Compliance Evidence - Rule violation evidence records
CREATE TABLE IF NOT EXISTS compliance_evidence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    rule_id VARCHAR(64),
    metric_value FLOAT,
    threshold_value FLOAT,
    source_module VARCHAR(64),
    severity VARCHAR(16),
    category VARCHAR(32),
    source_event_id BIGINT DEFAULT 0,
    notes TEXT,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_compliance_evidence_device (device_id),
    INDEX idx_compliance_evidence_timestamp (timestamp),
    INDEX idx_compliance_evidence_rule (rule_id),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Device Info Sync - Detailed device metadata from Room DB
CREATE TABLE IF NOT EXISTS device_info_sync (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    first_recorded BIGINT,
    manufacturer VARCHAR(64),
    model VARCHAR(128),
    product VARCHAR(64),
    brand VARCHAR(64),
    device VARCHAR(64),
    board VARCHAR(64),
    hardware VARCHAR(64),
    android_version VARCHAR(32),
    sdk_version INT,
    build_number VARCHAR(64),
    security_patch VARCHAR(32),
    kernel_version VARCHAR(128),
    firmware_version VARCHAR(64),
    battery_technology VARCHAR(32),
    design_capacity_mah FLOAT,
    battery_part_number VARCHAR(64),
    battery_manufacturer VARCHAR(64),
    battery_batch_id VARCHAR(64),
    processor VARCHAR(128),
    cpu_cores INT,
    ram_size BIGINT,
    storage_size BIGINT,
    screen_width INT,
    screen_height INT,
    screen_density FLOAT,
    oem_sku VARCHAR(64),
    region_code VARCHAR(16),
    carrier_code VARCHAR(32),
    production_date VARCHAR(32),
    warranty_status VARCHAR(32),
    hexnil_install_date BIGINT,
    hexnil_version VARCHAR(32),
    total_snapshots INT DEFAULT 0,
    total_analyses INT DEFAULT 0,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY idx_device_info_sync_device (device_id),
    INDEX idx_device_info_sync_timestamp (timestamp),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Anomaly Records - AI-detected anomalies
CREATE TABLE IF NOT EXISTS anomaly_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    detected_at BIGINT NOT NULL,
    anomaly_type VARCHAR(32),
    severity VARCHAR(16),
    confidence_score FLOAT,
    source_app VARCHAR(256),
    source_app_name VARCHAR(128),
    source_component VARCHAR(64),
    title VARCHAR(256),
    description TEXT,
    technical_details TEXT,
    expected_value FLOAT,
    actual_value FLOAT,
    deviation_percent FLOAT,
    metric_name VARCHAR(64),
    metric_unit VARCHAR(16),
    battery_level INT,
    was_charging BOOLEAN DEFAULT FALSE,
    screen_on BOOLEAN DEFAULT FALSE,
    device_state VARCHAR(16),
    period_start BIGINT,
    period_end BIGINT,
    data_points_analyzed INT DEFAULT 0,
    recommendation TEXT,
    root_cause TEXT,
    impact TEXT,
    acknowledged BOOLEAN DEFAULT FALSE,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at BIGINT,
    resolution TEXT,
    related_anomaly_id BIGINT,
    occurrence_count INT DEFAULT 1,
    is_recurring BOOLEAN DEFAULT FALSE,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_anomaly_records_device (device_id),
    INDEX idx_anomaly_records_detected_at (detected_at),
    INDEX idx_anomaly_records_type (anomaly_type),
    INDEX idx_anomaly_records_severity (severity),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================================
-- REFRESH RATE INTELLIGENCE TABLES
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

-- ============================================================================
-- CPU/GPU LOAD SAMPLES (Continuous stress monitoring)
-- ============================================================================

CREATE TABLE IF NOT EXISTS cpu_gpu_load_samples (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- App Attribution
    app_name VARCHAR(128),
    package_name VARCHAR(256),
    pid INT DEFAULT 0,
    uid INT DEFAULT 0,
    is_system_app BOOLEAN DEFAULT FALSE,
    
    -- CPU Metrics
    system_cpu_percent INT DEFAULT -1,
    app_cpu_percent INT DEFAULT -1,
    
    -- GPU Metrics
    gpu_percent INT DEFAULT -1,
    is_gpu_available BOOLEAN DEFAULT TRUE,
    
    -- System Load
    load_1m FLOAT DEFAULT 0,
    load_5m FLOAT DEFAULT 0,
    load_15m FLOAT DEFAULT 0,
    runnable_processes INT DEFAULT 0,
    
    -- Context
    temperature FLOAT DEFAULT 0,
    battery_level INT DEFAULT 0,
    drain_rate_per_hour FLOAT DEFAULT 0,
    is_throttled BOOLEAN DEFAULT FALSE,
    throttle_level INT DEFAULT 0,
    thermal_status INT DEFAULT 0,
    
    -- Stress Analysis
    stress_score FLOAT DEFAULT 0,
    stress_category VARCHAR(16) DEFAULT 'LOW',
    stress_explanation TEXT,
    inferred_load VARCHAR(16) DEFAULT 'LOW',
    confidence_level VARCHAR(16) DEFAULT 'LOW',
    detection_method VARCHAR(64) DEFAULT 'Direct hardware counters',
    observed_signals TEXT,
    
    -- Sample Metadata
    source_type VARCHAR(32) DEFAULT 'system',
    validation_status VARCHAR(16) DEFAULT 'unchecked',
    validation_notes TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Indices
    INDEX idx_cpu_gpu_device_timestamp (device_id, timestamp),
    INDEX idx_cpu_gpu_timestamp (timestamp),
    INDEX idx_cpu_gpu_package_name (package_name(100)),
    INDEX idx_cpu_gpu_stress_category (stress_category),
    INDEX idx_cpu_gpu_source_type_timestamp (source_type, timestamp),
    FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Grant permissions for new tables
GRANT SELECT, INSERT ON hexnil.thermal_heat_buckets TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.stress_test_results TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.update_comparisons TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.compliance_reports TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.compliance_evidence TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.device_info_sync TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.anomaly_records TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.refresh_rate_sessions TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.refresh_rate_anomalies TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.cpu_gpu_load_samples TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.memory_snapshots TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.app_launch_metrics TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.frame_metrics TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.crash_events TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.anr_events TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.daily_aggregates TO 'hexnil_backend'@'localhost';
GRANT SELECT, INSERT ON hexnil.regression_events TO 'hexnil_backend'@'localhost';

-- Same grants for wildcard host
GRANT SELECT, INSERT ON hexnil.thermal_heat_buckets TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.stress_test_results TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.update_comparisons TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.compliance_reports TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.compliance_evidence TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.device_info_sync TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.anomaly_records TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.refresh_rate_sessions TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.refresh_rate_anomalies TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.cpu_gpu_load_samples TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.memory_snapshots TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.app_launch_metrics TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.frame_metrics TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.crash_events TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.anr_events TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.daily_aggregates TO 'hexnil_backend'@'%';
GRANT SELECT, INSERT ON hexnil.regression_events TO 'hexnil_backend'@'%';

GRANT SELECT ON hexnil.thermal_heat_buckets TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.stress_test_results TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.update_comparisons TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.compliance_reports TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.compliance_evidence TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.device_info_sync TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.anomaly_records TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.refresh_rate_sessions TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.refresh_rate_anomalies TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.cpu_gpu_load_samples TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.memory_snapshots TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.app_launch_metrics TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.frame_metrics TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.crash_events TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.anr_events TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.daily_aggregates TO 'hexnil_ai'@'localhost';
GRANT SELECT ON hexnil.regression_events TO 'hexnil_ai'@'localhost';

-- Same grants for wildcard host
GRANT SELECT ON hexnil.thermal_heat_buckets TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.stress_test_results TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.update_comparisons TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.compliance_reports TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.compliance_evidence TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.device_info_sync TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.anomaly_records TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.refresh_rate_sessions TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.refresh_rate_anomalies TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.cpu_gpu_load_samples TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.memory_snapshots TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.app_launch_metrics TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.frame_metrics TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.crash_events TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.anr_events TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.daily_aggregates TO 'hexnil_ai'@'%';
GRANT SELECT ON hexnil.regression_events TO 'hexnil_ai'@'%';

FLUSH PRIVILEGES;

-- ============================================================================
-- VERIFICATION
-- ============================================================================
SELECT 'HexNil MySQL Schema Created Successfully!' AS status;
SHOW TABLES;
