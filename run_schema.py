import mysql.connector

conn = mysql.connector.connect(host='localhost', user='root', password='248r1a05aq', database='hexnil')
cursor = conn.cursor()
print('Connected to MySQL!')

tables = [
    ('thermal_heat_buckets', '''CREATE TABLE IF NOT EXISTS thermal_heat_buckets (
        id BIGINT AUTO_INCREMENT PRIMARY KEY, device_id VARCHAR(64) NOT NULL,
        start_time BIGINT NOT NULL, end_time BIGINT NOT NULL, temp_bucket VARCHAR(16), hour_block INT NOT NULL,
        sample_count INT DEFAULT 0, duration_ms BIGINT DEFAULT 0, max_temp FLOAT, avg_temp FLOAT, min_temp FLOAT,
        temp_variance FLOAT, charging_count INT DEFAULT 0, charging_percent FLOAT DEFAULT 0, avg_temp_charging FLOAT,
        avg_temp_not_charging FLOAT, avg_charge_current FLOAT, fast_charging_count INT DEFAULT 0,
        high_cpu_count INT DEFAULT 0, high_gpu_count INT DEFAULT 0, avg_cpu_load FLOAT, avg_gpu_load FLOAT,
        avg_temp_high_cpu FLOAT, avg_temp_high_gpu FLOAT, top_foreground_app VARCHAR(256),
        screen_on_count INT DEFAULT 0, screen_on_percent FLOAT DEFAULT 0, app_temp_contribution_json TEXT,
        gaming_count INT DEFAULT 0, camera_count INT DEFAULT 0, navigation_count INT DEFAULT 0, network_type VARCHAR(32),
        spike_count INT DEFAULT 0, sustained_heat_count INT DEFAULT 0, warning_event_count INT DEFAULT 0,
        critical_event_count INT DEFAULT 0, avg_battery_level FLOAT, battery_drain FLOAT, degradation_risk FLOAT,
        thermal_score FLOAT, exceeds_oem_limit BOOLEAN DEFAULT FALSE, oem_compliance_status VARCHAR(16) DEFAULT 'pass',
        received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_thb_dev (device_id))'''),
    ('stress_test_results', '''CREATE TABLE IF NOT EXISTS stress_test_results (
        id BIGINT AUTO_INCREMENT PRIMARY KEY, device_id VARCHAR(64) NOT NULL, timestamp BIGINT NOT NULL,
        test_start_time BIGINT NOT NULL, test_end_time BIGINT, test_type VARCHAR(32), test_name VARCHAR(128),
        test_duration_minutes INT, test_parameters TEXT, high_temperature_scenario BOOLEAN DEFAULT FALSE,
        start_level INT, end_level INT, drain_amount FLOAT, drain_rate FLOAT, peak_drain_rate FLOAT,
        start_temperature FLOAT, max_temperature FLOAT, end_temperature FLOAT, avg_temperature FLOAT,
        thermal_throttle_events INT DEFAULT 0, thermal_throttle_time BIGINT DEFAULT 0, safety_trigger_count INT DEFAULT 0,
        safety_notes TEXT, avg_cpu_usage FLOAT, max_cpu_usage FLOAT, avg_gpu_usage FLOAT, max_gpu_usage FLOAT,
        avg_memory_usage FLOAT, max_memory_usage FLOAT, charge_gained INT DEFAULT 0, avg_charging_current FLOAT,
        max_charging_current FLOAT, charging_interruptions INT DEFAULT 0, test_completed BOOLEAN DEFAULT FALSE,
        crash_count INT DEFAULT 0, restart_count INT DEFAULT 0, error_count INT DEFAULT 0, failure_reasons TEXT,
        drain_score FLOAT, thermal_score FLOAT, stability_score FLOAT, thermal_resilience_score FLOAT,
        charging_stability_score FLOAT, overall_score FLOAT, baseline_drain FLOAT, drain_variance FLOAT,
        baseline_thermal FLOAT, thermal_variance FLOAT, passed BOOLEAN DEFAULT FALSE, verdict VARCHAR(16),
        verdict_details TEXT, test_logs TEXT, recommendations TEXT, received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_str_dev (device_id))'''),
    ('update_comparisons', '''CREATE TABLE IF NOT EXISTS update_comparisons (
        id BIGINT AUTO_INCREMENT PRIMARY KEY, device_id VARCHAR(64) NOT NULL, timestamp BIGINT NOT NULL,
        previous_version VARCHAR(64), current_version VARCHAR(64), update_timestamp BIGINT, update_type VARCHAR(32),
        pre_drain_rate FLOAT, post_drain_rate FLOAT, drain_rate_delta FLOAT, drain_rate_change FLOAT,
        thermal_peak_delta FLOAT, overall_impact VARCHAR(16), impact_score FLOAT, impact_details TEXT,
        has_drain_regression BOOLEAN DEFAULT FALSE, has_charging_regression BOOLEAN DEFAULT FALSE,
        has_thermal_regression BOOLEAN DEFAULT FALSE, has_idle_regression BOOLEAN DEFAULT FALSE,
        recommendation VARCHAR(32), analysis_notes TEXT, confidence_level FLOAT, pre_series_json TEXT,
        post_series_json TEXT, received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_uc_dev (device_id))'''),
    ('compliance_reports', '''CREATE TABLE IF NOT EXISTS compliance_reports (
        id BIGINT AUTO_INCREMENT PRIMARY KEY, device_id VARCHAR(64) NOT NULL, timestamp BIGINT NOT NULL,
        period_start BIGINT, period_end BIGINT, report_type VARCHAR(32), safety_compliant BOOLEAN DEFAULT TRUE,
        safety_score FLOAT, temp_compliant BOOLEAN DEFAULT TRUE, voltage_compliant BOOLEAN DEFAULT TRUE,
        current_compliant BOOLEAN DEFAULT TRUE, certification_readiness FLOAT, review_status VARCHAR(16) DEFAULT 'pending',
        received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_cr_dev (device_id))'''),
    ('compliance_evidence', '''CREATE TABLE IF NOT EXISTS compliance_evidence (
        id BIGINT AUTO_INCREMENT PRIMARY KEY, device_id VARCHAR(64) NOT NULL, timestamp BIGINT NOT NULL,
        rule_id VARCHAR(64), metric_value FLOAT, threshold_value FLOAT, source_module VARCHAR(64),
        severity VARCHAR(16), category VARCHAR(32), source_event_id BIGINT DEFAULT 0, notes TEXT,
        received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_ce_dev (device_id))'''),
    ('device_info_sync', '''CREATE TABLE IF NOT EXISTS device_info_sync (
        id BIGINT AUTO_INCREMENT PRIMARY KEY, device_id VARCHAR(64) NOT NULL, timestamp BIGINT NOT NULL,
        first_recorded BIGINT, manufacturer VARCHAR(64), model VARCHAR(128), android_version VARCHAR(32),
        hexnil_version VARCHAR(32), battery_technology VARCHAR(32), design_capacity_mah FLOAT,
        total_snapshots INT DEFAULT 0, total_analyses INT DEFAULT 0, received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY idx_dis_dev (device_id))'''),
    ('anomaly_records', '''CREATE TABLE IF NOT EXISTS anomaly_records (
        id BIGINT AUTO_INCREMENT PRIMARY KEY, device_id VARCHAR(64) NOT NULL, detected_at BIGINT NOT NULL,
        anomaly_type VARCHAR(32), severity VARCHAR(16), confidence_score FLOAT, source_app VARCHAR(256),
        source_app_name VARCHAR(128), title VARCHAR(256), description TEXT, expected_value FLOAT, actual_value FLOAT,
        deviation_percent FLOAT, metric_name VARCHAR(64), battery_level INT, was_charging BOOLEAN DEFAULT FALSE,
        screen_on BOOLEAN DEFAULT FALSE, recommendation TEXT, acknowledged BOOLEAN DEFAULT FALSE,
        resolved BOOLEAN DEFAULT FALSE, occurrence_count INT DEFAULT 1, is_recurring BOOLEAN DEFAULT FALSE,
        received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_ar_dev (device_id))'''),
]

for name, sql in tables:
    try:
        cursor.execute(sql)
        print(f'Created: {name}')
    except Exception as e:
        print(f'Error {name}: {e}')

grants = [
    "GRANT SELECT, INSERT ON hexnil.thermal_heat_buckets TO 'hexnil_backend'@'localhost'",
    "GRANT SELECT, INSERT ON hexnil.stress_test_results TO 'hexnil_backend'@'localhost'",
    "GRANT SELECT, INSERT ON hexnil.update_comparisons TO 'hexnil_backend'@'localhost'",
    "GRANT SELECT, INSERT ON hexnil.compliance_reports TO 'hexnil_backend'@'localhost'",
    "GRANT SELECT, INSERT ON hexnil.compliance_evidence TO 'hexnil_backend'@'localhost'",
    "GRANT SELECT, INSERT ON hexnil.device_info_sync TO 'hexnil_backend'@'localhost'",
    "GRANT SELECT, INSERT ON hexnil.anomaly_records TO 'hexnil_backend'@'localhost'",
]
for g in grants:
    try: cursor.execute(g)
    except: pass
cursor.execute('FLUSH PRIVILEGES')
conn.commit()

cursor.execute('SHOW TABLES')
print('\nAll tables:')
for (t,) in cursor: print(f'  {t}')
cursor.close()
conn.close()
print('\nDone!')
