"""
Apply HexNil MySQL Schema - Creates the 7 new tables
Run this script once with your MySQL root password
"""
import mysql.connector
import getpass

print("HexNil MySQL Schema Updater")
print("=" * 40)
print("\nThis will create 7 new tables for complete Room DB sync.")
print("Enter your MySQL root password:")

password = getpass.getpass("Root Password: ")

try:
    conn = mysql.connector.connect(
        host='localhost',
        user='root',
        password=password,
        database='hexnil'
    )
    cursor = conn.cursor()
    print("\n✓ Connected to MySQL")

    # Create tables
    tables = [
        ("thermal_heat_buckets", """
CREATE TABLE IF NOT EXISTS thermal_heat_buckets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NOT NULL,
    temp_bucket VARCHAR(16),
    hour_block INT NOT NULL,
    sample_count INT DEFAULT 0,
    duration_ms BIGINT DEFAULT 0,
    max_temp FLOAT, avg_temp FLOAT, min_temp FLOAT, temp_variance FLOAT,
    charging_count INT DEFAULT 0, charging_percent FLOAT DEFAULT 0,
    avg_temp_charging FLOAT, avg_temp_not_charging FLOAT, avg_charge_current FLOAT,
    fast_charging_count INT DEFAULT 0, high_cpu_count INT DEFAULT 0, high_gpu_count INT DEFAULT 0,
    avg_cpu_load FLOAT, avg_gpu_load FLOAT, avg_temp_high_cpu FLOAT, avg_temp_high_gpu FLOAT,
    top_foreground_app VARCHAR(256), screen_on_count INT DEFAULT 0, screen_on_percent FLOAT DEFAULT 0,
    app_temp_contribution_json TEXT, gaming_count INT DEFAULT 0, camera_count INT DEFAULT 0,
    navigation_count INT DEFAULT 0, network_type VARCHAR(32), spike_count INT DEFAULT 0,
    sustained_heat_count INT DEFAULT 0, warning_event_count INT DEFAULT 0, critical_event_count INT DEFAULT 0,
    avg_battery_level FLOAT, battery_drain FLOAT, degradation_risk FLOAT, thermal_score FLOAT,
    exceeds_oem_limit BOOLEAN DEFAULT FALSE, oem_compliance_status VARCHAR(16) DEFAULT 'pass',
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_thermal_buckets_device (device_id), INDEX idx_thermal_buckets_start_time (start_time)
)"""),
        ("stress_test_results", """
CREATE TABLE IF NOT EXISTS stress_test_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    test_start_time BIGINT NOT NULL, test_end_time BIGINT,
    test_type VARCHAR(32), test_name VARCHAR(128), test_duration_minutes INT, test_parameters TEXT,
    high_temperature_scenario BOOLEAN DEFAULT FALSE,
    start_level INT, end_level INT, drain_amount FLOAT, drain_rate FLOAT, peak_drain_rate FLOAT,
    start_temperature FLOAT, max_temperature FLOAT, end_temperature FLOAT, avg_temperature FLOAT,
    thermal_throttle_events INT DEFAULT 0, thermal_throttle_time BIGINT DEFAULT 0,
    safety_trigger_count INT DEFAULT 0, safety_notes TEXT,
    avg_cpu_usage FLOAT, max_cpu_usage FLOAT, avg_gpu_usage FLOAT, max_gpu_usage FLOAT,
    avg_memory_usage FLOAT, max_memory_usage FLOAT, charge_gained INT DEFAULT 0,
    avg_charging_current FLOAT, max_charging_current FLOAT, charging_interruptions INT DEFAULT 0,
    test_completed BOOLEAN DEFAULT FALSE, crash_count INT DEFAULT 0, restart_count INT DEFAULT 0,
    error_count INT DEFAULT 0, failure_reasons TEXT,
    drain_score FLOAT, thermal_score FLOAT, stability_score FLOAT, thermal_resilience_score FLOAT,
    charging_stability_score FLOAT, overall_score FLOAT, baseline_drain FLOAT, drain_variance FLOAT,
    baseline_thermal FLOAT, thermal_variance FLOAT, passed BOOLEAN DEFAULT FALSE,
    verdict VARCHAR(16), verdict_details TEXT, test_logs TEXT, recommendations TEXT,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_stress_test_device (device_id)
)"""),
        ("update_comparisons", """
CREATE TABLE IF NOT EXISTS update_comparisons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    previous_version VARCHAR(64), current_version VARCHAR(64), update_timestamp BIGINT, update_type VARCHAR(32),
    pre_drain_rate FLOAT, pre_avg_drain_screen_on FLOAT, pre_avg_drain_screen_off FLOAT,
    pre_avg_charging_time FLOAT, pre_avg_temperature FLOAT, pre_max_temperature FLOAT,
    pre_idle_drain FLOAT, pre_battery_health FLOAT,
    post_drain_rate FLOAT, post_avg_drain_screen_on FLOAT, post_avg_drain_screen_off FLOAT,
    post_avg_charging_time FLOAT, post_avg_temperature FLOAT, post_max_temperature FLOAT,
    post_idle_drain FLOAT, post_battery_health FLOAT,
    drain_rate_delta FLOAT, screen_on_drain_delta FLOAT, screen_off_drain_delta FLOAT, charging_time_delta FLOAT,
    temperature_delta FLOAT, thermal_peak_delta FLOAT, idle_drain_delta FLOAT, drain_rate_change FLOAT,
    screen_on_drain_change FLOAT, screen_off_drain_change FLOAT, charging_time_change FLOAT,
    temperature_change FLOAT, thermal_peak_change FLOAT, idle_drain_change FLOAT,
    overall_impact VARCHAR(16), impact_score FLOAT, impact_details TEXT,
    has_drain_regression BOOLEAN DEFAULT FALSE, has_charging_regression BOOLEAN DEFAULT FALSE,
    has_thermal_regression BOOLEAN DEFAULT FALSE, has_idle_regression BOOLEAN DEFAULT FALSE,
    recommendation VARCHAR(32), analysis_notes TEXT, confidence_level FLOAT,
    pre_series_json TEXT, post_series_json TEXT,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_update_comp_device (device_id)
)"""),
        ("compliance_reports", """
CREATE TABLE IF NOT EXISTS compliance_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    period_start BIGINT, period_end BIGINT, report_type VARCHAR(32), report_version VARCHAR(16),
    generated_by VARCHAR(32), device_model VARCHAR(128), device_serial VARCHAR(64),
    firmware_version VARCHAR(64), battery_part_number VARCHAR(64),
    safety_compliant BOOLEAN DEFAULT TRUE, safety_violation_count INT DEFAULT 0, safety_violations TEXT,
    safety_score FLOAT, max_operating_temp FLOAT, max_charging_temp FLOAT,
    temp_compliant BOOLEAN DEFAULT TRUE, temp_violation_count INT DEFAULT 0, temp_violation_details TEXT,
    min_voltage FLOAT, max_voltage FLOAT, voltage_compliant BOOLEAN DEFAULT TRUE,
    voltage_violation_count INT DEFAULT 0, voltage_violation_details TEXT,
    max_charging_current FLOAT, max_discharge_current FLOAT, current_compliant BOOLEAN DEFAULT TRUE,
    current_violation_count INT DEFAULT 0, current_violation_details TEXT,
    lowest_voltage FLOAT, over_discharge_protection_working BOOLEAN DEFAULT TRUE, over_discharge_events INT DEFAULT 0,
    highest_voltage FLOAT, overcharge_protection_working BOOLEAN DEFAULT TRUE, overcharge_events INT DEFAULT 0,
    thermal_protection_working BOOLEAN DEFAULT TRUE, thermal_shutdown_events INT DEFAULT 0,
    thermal_throttle_events INT DEFAULT 0, iec_compliant BOOLEAN DEFAULT TRUE, ul_compliant BOOLEAN DEFAULT TRUE,
    un_compliant BOOLEAN DEFAULT TRUE, standards_notes TEXT, certification_readiness FLOAT,
    certification_gaps TEXT, certification_recommendations TEXT, evidence_summary TEXT,
    evidence_ids TEXT, evidence_count INT DEFAULT 0, audit_trail TEXT, data_points_analyzed INT DEFAULT 0,
    data_quality FLOAT, executive_summary TEXT, technical_details TEXT, compliance_matrix TEXT, attachments TEXT,
    review_status VARCHAR(16) DEFAULT 'pending', review_notes TEXT, review_timestamp BIGINT,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_compliance_reports_device (device_id)
)"""),
        ("compliance_evidence", """
CREATE TABLE IF NOT EXISTS compliance_evidence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    rule_id VARCHAR(64), metric_value FLOAT, threshold_value FLOAT, source_module VARCHAR(64),
    severity VARCHAR(16), category VARCHAR(32), source_event_id BIGINT DEFAULT 0, notes TEXT,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_compliance_evidence_device (device_id)
)"""),
        ("device_info_sync", """
CREATE TABLE IF NOT EXISTS device_info_sync (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    timestamp BIGINT NOT NULL,
    first_recorded BIGINT, manufacturer VARCHAR(64), model VARCHAR(128), product VARCHAR(64),
    brand VARCHAR(64), device VARCHAR(64), board VARCHAR(64), hardware VARCHAR(64),
    android_version VARCHAR(32), sdk_version INT, build_number VARCHAR(64), security_patch VARCHAR(32),
    kernel_version VARCHAR(128), firmware_version VARCHAR(64), battery_technology VARCHAR(32),
    design_capacity_mah FLOAT, battery_part_number VARCHAR(64), battery_manufacturer VARCHAR(64),
    battery_batch_id VARCHAR(64), processor VARCHAR(128), cpu_cores INT, ram_size BIGINT,
    storage_size BIGINT, screen_width INT, screen_height INT, screen_density FLOAT,
    oem_sku VARCHAR(64), region_code VARCHAR(16), carrier_code VARCHAR(32), production_date VARCHAR(32),
    warranty_status VARCHAR(32), hexnil_install_date BIGINT, hexnil_version VARCHAR(32),
    total_snapshots INT DEFAULT 0, total_analyses INT DEFAULT 0,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY idx_device_info_sync_device (device_id)
)"""),
        ("anomaly_records", """
CREATE TABLE IF NOT EXISTS anomaly_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    detected_at BIGINT NOT NULL,
    anomaly_type VARCHAR(32), severity VARCHAR(16), confidence_score FLOAT,
    source_app VARCHAR(256), source_app_name VARCHAR(128), source_component VARCHAR(64),
    title VARCHAR(256), description TEXT, technical_details TEXT,
    expected_value FLOAT, actual_value FLOAT, deviation_percent FLOAT,
    metric_name VARCHAR(64), metric_unit VARCHAR(16), battery_level INT,
    was_charging BOOLEAN DEFAULT FALSE, screen_on BOOLEAN DEFAULT FALSE, device_state VARCHAR(16),
    period_start BIGINT, period_end BIGINT, data_points_analyzed INT DEFAULT 0,
    recommendation TEXT, root_cause TEXT, impact TEXT,
    acknowledged BOOLEAN DEFAULT FALSE, resolved BOOLEAN DEFAULT FALSE, resolved_at BIGINT,
    resolution TEXT, related_anomaly_id BIGINT, occurrence_count INT DEFAULT 1, is_recurring BOOLEAN DEFAULT FALSE,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_anomaly_records_device (device_id)
)"""),
    ]

    for name, sql in tables:
        try:
            cursor.execute(sql)
            print(f"✓ Created table: {name}")
        except Exception as e:
            print(f"✗ Error creating {name}: {e}")

    # Grant permissions
    grants = [
        "GRANT SELECT, INSERT ON hexnil.thermal_heat_buckets TO 'hexnil_backend'@'localhost'",
        "GRANT SELECT, INSERT ON hexnil.stress_test_results TO 'hexnil_backend'@'localhost'",
        "GRANT SELECT, INSERT ON hexnil.update_comparisons TO 'hexnil_backend'@'localhost'",
        "GRANT SELECT, INSERT ON hexnil.compliance_reports TO 'hexnil_backend'@'localhost'",
        "GRANT SELECT, INSERT ON hexnil.compliance_evidence TO 'hexnil_backend'@'localhost'",
        "GRANT SELECT, INSERT ON hexnil.device_info_sync TO 'hexnil_backend'@'localhost'",
        "GRANT SELECT, INSERT ON hexnil.anomaly_records TO 'hexnil_backend'@'localhost'",
    ]
    
    print("\nGranting permissions...")
    for grant in grants:
        try:
            cursor.execute(grant)
        except:
            pass
    cursor.execute("FLUSH PRIVILEGES")
    
    conn.commit()
    
    # List all tables
    print("\n" + "=" * 40)
    print("All tables in hexnil database:")
    cursor.execute("SHOW TABLES")
    for (table,) in cursor:
        print(f"  ✓ {table}")
    
    cursor.close()
    conn.close()
    print("\n✅ Schema applied successfully!")

except Exception as e:
    print(f"\n✗ Error: {e}")
