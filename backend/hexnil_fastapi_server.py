"""
HexNil FastAPI Backend Server
Production-grade data ingestion API for Android battery monitoring app.
Receives JSON batches from Android devices and stores in MySQL.

Author: HexNil Team
Created: 2026-01-13
"""

import os
import json
import logging
import asyncio
import time
from datetime import datetime, timezone
from typing import Optional, List, Dict, Any
from contextlib import asynccontextmanager

import mysql.connector
from mysql.connector import pooling
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
import uvicorn

try:
    # When run as `python -m uvicorn backend.hexnil_fastapi_server:app`
    from backend.ingest_spool import (
        init_spool,
        enqueue,
        claim_batch,
        mark_done,
        mark_failed,
        pending_counts,
    )
except Exception:
    # When run from within backend folder
    from ingest_spool import init_spool, enqueue, claim_batch, mark_done, mark_failed, pending_counts

# ============================================================================
# CONFIGURATION
# ============================================================================

class Config:
    """Server configuration - edit these values for your environment."""
    
    # MySQL Connection
    MYSQL_HOST = os.getenv("HEXNIL_DB_HOST", "localhost")
    MYSQL_PORT = int(os.getenv("HEXNIL_DB_PORT", "3306"))
    MYSQL_DATABASE = os.getenv("HEXNIL_DB_NAME", "hexnil")
    MYSQL_USER = os.getenv("HEXNIL_DB_USER", "hexnil_backend")
    MYSQL_PASSWORD = os.getenv("HEXNIL_DB_PASSWORD", "HexNil_Backend_2026!")
    
    # Connection Pool
    POOL_SIZE = int(os.getenv("HEXNIL_POOL_SIZE", "10"))
    POOL_NAME = "hexnil_pool"
    
    # Server
    HOST = os.getenv("HEXNIL_HOST", "0.0.0.0")
    PORT = int(os.getenv("HEXNIL_PORT", "8000"))
    DEBUG = os.getenv("HEXNIL_DEBUG", "false").lower() == "true"
    
    # Logging
    LOG_LEVEL = os.getenv("HEXNIL_LOG_LEVEL", "INFO")

    # Spool / Background worker
    SPOOL_BATCH_SIZE = int(os.getenv("HEXNIL_SPOOL_BATCH_SIZE", "50"))
    SPOOL_POLL_INTERVAL_MS = int(os.getenv("HEXNIL_SPOOL_POLL_INTERVAL_MS", "250"))
    MAX_BATCH_SIZE = int(os.getenv("HEXNIL_MAX_BATCH_SIZE", "10000"))

# ============================================================================
# LOGGING SETUP
# ============================================================================

logging.basicConfig(
    level=getattr(logging, Config.LOG_LEVEL),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler('hexnil_server.log')
    ]
)
logger = logging.getLogger("hexnil.server")

# ============================================================================
# DATABASE CONNECTION POOL
# ============================================================================

db_pool: Optional[pooling.MySQLConnectionPool] = None

def init_db_pool():
    """Initialize MySQL connection pool."""
    global db_pool
    try:
        db_pool = pooling.MySQLConnectionPool(
            pool_name=Config.POOL_NAME,
            pool_size=Config.POOL_SIZE,
            pool_reset_session=True,
            host=Config.MYSQL_HOST,
            port=Config.MYSQL_PORT,
            database=Config.MYSQL_DATABASE,
            user=Config.MYSQL_USER,
            password=Config.MYSQL_PASSWORD,
            charset='utf8mb4',
            collation='utf8mb4_unicode_ci',
            autocommit=False
        )
        logger.info(f"Database pool initialized: {Config.POOL_SIZE} connections")
        return True
    except Exception as e:
        logger.error(f"Failed to initialize database pool: {e}")
        return False

def get_db_connection():
    """Get a connection from the pool."""
    if db_pool is None:
        raise HTTPException(status_code=503, detail="Database not available")
    return db_pool.get_connection()

# ============================================================================
# PYDANTIC MODELS
# ============================================================================

class DeviceInfo(BaseModel):
    """Device registration information."""
    device_id: str = Field(..., min_length=1, max_length=64)
    device_model: Optional[str] = Field(None, max_length=128)
    android_version: Optional[str] = Field(None, max_length=32)
    app_version: Optional[str] = Field(None, max_length=32)

class BaseDataRecord(BaseModel):
    """Base model for all data records."""
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(..., gt=0)
    payload: Optional[Dict[str, Any]] = None

class BatterySnapshot(BaseDataRecord):
    """Battery snapshot data."""
    battery_level: int = Field(..., ge=0, le=100)
    temperature: Optional[float] = None
    voltage: Optional[float] = None
    current_now: Optional[float] = None
    is_charging: bool = False
    charge_type: Optional[str] = None
    screen_on: bool = False
    power_save_mode: bool = False

class ChargingSession(BaseDataRecord):
    """Charging session data."""
    session_id: str = Field(..., min_length=1, max_length=64)
    start_level: int = Field(..., ge=0, le=100)
    end_level: Optional[int] = Field(None, ge=0, le=100)
    start_time: int = Field(..., gt=0)
    end_time: Optional[int] = None
    charge_type: Optional[str] = None
    max_temperature: Optional[float] = None
    avg_temperature: Optional[float] = None
    energy_added_mah: Optional[float] = None
    charging_speed_ma: Optional[float] = None
    is_complete: bool = False

class ThermalEvent(BaseDataRecord):
    """Thermal event data."""
    temperature: float = Field(...)
    threshold_exceeded: Optional[float] = None
    duration_seconds: Optional[int] = None
    trigger_source: Optional[str] = None
    cpu_usage: Optional[float] = None
    foreground_app: Optional[str] = None
    severity: Optional[str] = None

class DrainEvent(BaseDataRecord):
    """Battery drain event data."""
    start_level: int = Field(..., ge=0, le=100)
    end_level: int = Field(..., ge=0, le=100)
    duration_minutes: int = Field(..., gt=0)
    drain_rate_per_hour: float = Field(...)
    screen_on_time_minutes: Optional[int] = None
    suspected_cause: Optional[str] = None
    top_apps: Optional[List[Dict[str, Any]]] = None

class IdleDrainSession(BaseDataRecord):
    """Idle drain session data."""
    session_id: str = Field(..., min_length=1, max_length=64)
    start_level: int = Field(..., ge=0, le=100)
    end_level: int = Field(..., ge=0, le=100)
    start_time: int = Field(..., gt=0)
    end_time: int = Field(..., gt=0)
    total_drain: int = Field(...)
    drain_rate_per_hour: float = Field(...)
    screen_off_duration_minutes: Optional[int] = None
    doze_time_minutes: Optional[int] = None
    wake_locks_count: Optional[int] = None
    network_activity_mb: Optional[float] = None

class BatteryHealth(BaseDataRecord):
    """Battery health data."""
    health_percentage: Optional[float] = None
    cycle_count: Optional[int] = None
    design_capacity_mah: Optional[int] = None
    current_capacity_mah: Optional[int] = None
    charge_full_design: Optional[int] = None
    charge_full: Optional[int] = None
    technology: Optional[str] = None
    manufacture_date: Optional[str] = None
    first_use_date: Optional[int] = None

class SafetyEvent(BaseDataRecord):
    """Safety event data."""
    event_type: str = Field(..., min_length=1, max_length=64)
    severity: str = Field(..., min_length=1, max_length=16)
    temperature: Optional[float] = None
    voltage: Optional[float] = None
    current_ma: Optional[float] = None
    description: Optional[str] = None
    action_taken: Optional[str] = None
    user_notified: bool = False

class CalibrationData(BaseDataRecord):
    """Calibration data."""
    calibration_type: Optional[str] = None
    old_value: Optional[float] = None
    new_value: Optional[float] = None
    accuracy_score: Optional[float] = None
    reference_source: Optional[str] = None
    is_automatic: bool = True

class AIAnalysisResult(BaseDataRecord):
    """AI analysis result from on-device processing."""
    analysis_type: str = Field(..., min_length=1, max_length=64)
    confidence_score: Optional[float] = None
    prediction: Optional[Dict[str, Any]] = None
    features_used: Optional[List[str]] = None
    model_version: Optional[str] = None
    execution_time_ms: Optional[int] = None

# ============================================================================
# APP DATA MODELS (From Room DB)
# ============================================================================

class AppBatteryUsageRecord(BaseModel):
    """App battery usage data from Room DB app_battery_usage table."""
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(..., gt=0)
    period_start: int = Field(..., gt=0)
    period_end: int = Field(..., gt=0)
    package_name: Optional[str] = None
    app_name: Optional[str] = None
    is_system_app: bool = False
    is_preinstalled: bool = False
    app_category: Optional[str] = None
    foreground_time: int = 0
    background_time: int = 0
    battery_percent_used: float = 0
    drain_per_hour: float = 0
    cpu_time_ms: float = 0
    network_bytes: int = 0
    wakelock_count: int = 0
    wakelock_time: int = 0
    partial_wakelock_time: int = 0
    alarm_wake_count: int = 0
    network_wake_count: int = 0
    wakelock_drain_score: float = 0
    alarm_count: int = 0
    gps_requests: int = 0
    background_wakes: int = 0
    push_notifications: int = 0
    background_services: int = 0
    has_excessive_background: bool = False
    category_average: float = 0
    deviation_from_average: float = 0
    efficiency_rating: Optional[str] = None
    drain_reasons: Optional[str] = None
    optimization_tips: Optional[str] = None
    needs_restriction: bool = False

class AppDrainRankRecord(BaseModel):
    """App drain rank data from Room DB app_drain_ranks table."""
    device_id: str = Field(..., min_length=1, max_length=64)
    window_label: Optional[str] = None
    window_start: int = Field(..., gt=0)
    window_end: int = Field(..., gt=0)
    rank_position: int = Field(..., ge=0)
    package_name: Optional[str] = None
    drain_score: float = 0
    contribution_pct: float = 0
    screen_time_ms: int = 0
    foreground_time_ms: int = 0
    background_time_ms: int = 0

# ============================================================================
# ADDITIONAL ROOM DB MODELS (Complete Sync)
# ============================================================================

class ThermalHeatBucketRecord(BaseModel):
    """Thermal heat bucket data from Room DB."""
    device_id: str = Field(..., min_length=1, max_length=64)
    start_time: int = Field(..., gt=0)
    end_time: int = Field(..., gt=0)
    temp_bucket: Optional[str] = None
    hour_block: int = 0
    sample_count: int = 0
    duration_ms: int = 0
    max_temp: Optional[float] = None
    avg_temp: Optional[float] = None
    min_temp: Optional[float] = None
    temp_variance: Optional[float] = None
    charging_count: int = 0
    charging_percent: float = 0
    avg_temp_charging: Optional[float] = None
    avg_temp_not_charging: Optional[float] = None
    avg_charge_current: Optional[float] = None
    fast_charging_count: int = 0
    high_cpu_count: int = 0
    high_gpu_count: int = 0
    avg_cpu_load: Optional[float] = None
    avg_gpu_load: Optional[float] = None
    avg_temp_high_cpu: Optional[float] = None
    avg_temp_high_gpu: Optional[float] = None
    top_foreground_app: Optional[str] = None
    screen_on_count: int = 0
    screen_on_percent: float = 0
    app_temp_contribution_json: Optional[str] = None
    gaming_count: int = 0
    camera_count: int = 0
    navigation_count: int = 0
    network_type: Optional[str] = None
    spike_count: int = 0
    sustained_heat_count: int = 0
    warning_event_count: int = 0
    critical_event_count: int = 0
    avg_battery_level: Optional[float] = None
    battery_drain: Optional[float] = None
    degradation_risk: Optional[float] = None
    thermal_score: Optional[float] = None
    exceeds_oem_limit: bool = False
    oem_compliance_status: Optional[str] = None

class StressTestResultRecord(BaseModel):
    """Stress test result data from Room DB."""
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(..., gt=0)
    test_start_time: int = Field(..., gt=0)
    test_end_time: Optional[int] = None
    test_type: Optional[str] = None
    test_name: Optional[str] = None
    test_duration_minutes: Optional[int] = None
    test_parameters: Optional[str] = None
    high_temperature_scenario: bool = False
    start_level: Optional[int] = None
    end_level: Optional[int] = None
    drain_amount: Optional[float] = None
    drain_rate: Optional[float] = None
    peak_drain_rate: Optional[float] = None
    start_temperature: Optional[float] = None
    max_temperature: Optional[float] = None
    end_temperature: Optional[float] = None
    avg_temperature: Optional[float] = None
    thermal_throttle_events: int = 0
    thermal_throttle_time: int = 0
    safety_trigger_count: int = 0
    safety_notes: Optional[str] = None
    avg_cpu_usage: Optional[float] = None
    max_cpu_usage: Optional[float] = None
    avg_gpu_usage: Optional[float] = None
    max_gpu_usage: Optional[float] = None
    avg_memory_usage: Optional[float] = None
    max_memory_usage: Optional[float] = None
    charge_gained: int = 0
    avg_charging_current: Optional[float] = None
    max_charging_current: Optional[float] = None
    charging_interruptions: int = 0
    test_completed: bool = False
    crash_count: int = 0
    restart_count: int = 0
    error_count: int = 0
    failure_reasons: Optional[str] = None
    drain_score: Optional[float] = None
    thermal_score: Optional[float] = None
    stability_score: Optional[float] = None
    thermal_resilience_score: Optional[float] = None
    charging_stability_score: Optional[float] = None
    overall_score: Optional[float] = None
    baseline_drain: Optional[float] = None
    drain_variance: Optional[float] = None
    baseline_thermal: Optional[float] = None
    thermal_variance: Optional[float] = None
    passed: bool = False
    verdict: Optional[str] = None
    verdict_details: Optional[str] = None
    test_logs: Optional[str] = None
    recommendations: Optional[str] = None

class UpdateComparisonRecord(BaseModel):
    """Update comparison data from Room DB."""
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(..., gt=0)
    previous_version: Optional[str] = None
    current_version: Optional[str] = None
    update_timestamp: Optional[int] = None
    update_type: Optional[str] = None
    pre_drain_rate: Optional[float] = None
    pre_avg_drain_screen_on: Optional[float] = None
    pre_avg_drain_screen_off: Optional[float] = None
    pre_avg_charging_time: Optional[float] = None
    pre_avg_temperature: Optional[float] = None
    pre_max_temperature: Optional[float] = None
    pre_idle_drain: Optional[float] = None
    pre_battery_health: Optional[float] = None
    post_drain_rate: Optional[float] = None
    post_avg_drain_screen_on: Optional[float] = None
    post_avg_drain_screen_off: Optional[float] = None
    post_avg_charging_time: Optional[float] = None
    post_avg_temperature: Optional[float] = None
    post_max_temperature: Optional[float] = None
    post_idle_drain: Optional[float] = None
    post_battery_health: Optional[float] = None
    drain_rate_delta: Optional[float] = None
    screen_on_drain_delta: Optional[float] = None
    screen_off_drain_delta: Optional[float] = None
    charging_time_delta: Optional[float] = None
    temperature_delta: Optional[float] = None
    thermal_peak_delta: Optional[float] = None
    idle_drain_delta: Optional[float] = None
    drain_rate_change: Optional[float] = None
    screen_on_drain_change: Optional[float] = None
    screen_off_drain_change: Optional[float] = None
    charging_time_change: Optional[float] = None
    temperature_change: Optional[float] = None
    thermal_peak_change: Optional[float] = None
    idle_drain_change: Optional[float] = None
    overall_impact: Optional[str] = None
    impact_score: Optional[float] = None
    impact_details: Optional[str] = None
    has_drain_regression: bool = False
    has_charging_regression: bool = False
    has_thermal_regression: bool = False
    has_idle_regression: bool = False
    recommendation: Optional[str] = None
    analysis_notes: Optional[str] = None
    confidence_level: Optional[float] = None
    pre_series_json: Optional[str] = None
    post_series_json: Optional[str] = None

class ComplianceReportRecord(BaseModel):
    """Compliance report data from Room DB."""
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(..., gt=0)
    period_start: Optional[int] = None
    period_end: Optional[int] = None
    report_type: Optional[str] = None
    report_version: Optional[str] = None
    generated_by: Optional[str] = None
    device_model: Optional[str] = None
    device_serial: Optional[str] = None
    firmware_version: Optional[str] = None
    battery_part_number: Optional[str] = None
    safety_compliant: bool = True
    safety_violation_count: int = 0
    safety_violations: Optional[str] = None
    safety_score: Optional[float] = None
    max_operating_temp: Optional[float] = None
    max_charging_temp: Optional[float] = None
    temp_compliant: bool = True
    temp_violation_count: int = 0
    temp_violation_details: Optional[str] = None
    min_voltage: Optional[float] = None
    max_voltage: Optional[float] = None
    voltage_compliant: bool = True
    voltage_violation_count: int = 0
    voltage_violation_details: Optional[str] = None
    max_charging_current: Optional[float] = None
    max_discharge_current: Optional[float] = None
    current_compliant: bool = True
    current_violation_count: int = 0
    current_violation_details: Optional[str] = None
    lowest_voltage: Optional[float] = None
    over_discharge_protection_working: bool = True
    over_discharge_events: int = 0
    highest_voltage: Optional[float] = None
    overcharge_protection_working: bool = True
    overcharge_events: int = 0
    thermal_protection_working: bool = True
    thermal_shutdown_events: int = 0
    thermal_throttle_events: int = 0
    iec_compliant: bool = True
    ul_compliant: bool = True
    un_compliant: bool = True
    standards_notes: Optional[str] = None
    certification_readiness: Optional[float] = None
    certification_gaps: Optional[str] = None
    certification_recommendations: Optional[str] = None
    evidence_summary: Optional[str] = None
    evidence_ids: Optional[str] = None
    evidence_count: int = 0
    audit_trail: Optional[str] = None
    data_points_analyzed: int = 0
    data_quality: Optional[float] = None
    executive_summary: Optional[str] = None
    technical_details: Optional[str] = None
    compliance_matrix: Optional[str] = None
    attachments: Optional[str] = None
    review_status: Optional[str] = None
    review_notes: Optional[str] = None
    review_timestamp: Optional[int] = None

class ComplianceEvidenceRecord(BaseModel):
    """Compliance evidence data from Room DB."""
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(..., gt=0)
    rule_id: Optional[str] = None
    metric_value: Optional[float] = None
    threshold_value: Optional[float] = None
    source_module: Optional[str] = None
    severity: Optional[str] = None
    category: Optional[str] = None
    source_event_id: int = 0
    notes: Optional[str] = None

class DeviceInfoSyncRecord(BaseModel):
    """Device info data from Room DB."""
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(..., gt=0)
    first_recorded: Optional[int] = None
    manufacturer: Optional[str] = None
    model: Optional[str] = None
    product: Optional[str] = None
    brand: Optional[str] = None
    device: Optional[str] = None
    board: Optional[str] = None
    hardware: Optional[str] = None
    android_version: Optional[str] = None
    sdk_version: Optional[int] = None
    build_number: Optional[str] = None
    security_patch: Optional[str] = None
    kernel_version: Optional[str] = None
    firmware_version: Optional[str] = None
    battery_technology: Optional[str] = None
    design_capacity_mah: Optional[float] = None
    battery_part_number: Optional[str] = None
    battery_manufacturer: Optional[str] = None
    battery_batch_id: Optional[str] = None
    processor: Optional[str] = None
    cpu_cores: Optional[int] = None
    ram_size: Optional[int] = None
    storage_size: Optional[int] = None
    screen_width: Optional[int] = None
    screen_height: Optional[int] = None
    screen_density: Optional[float] = None
    oem_sku: Optional[str] = None
    region_code: Optional[str] = None
    carrier_code: Optional[str] = None
    production_date: Optional[str] = None
    warranty_status: Optional[str] = None
    hexnil_install_date: Optional[int] = None
    hexnil_version: Optional[str] = None
    total_snapshots: int = 0
    total_analyses: int = 0

class AnomalyRecordRecord(BaseModel):
    """Anomaly record data from Room DB."""
    device_id: str = Field(..., min_length=1, max_length=64)
    detected_at: int = Field(..., gt=0)
    anomaly_type: Optional[str] = None
    severity: Optional[str] = None
    confidence_score: Optional[float] = None
    source_app: Optional[str] = None
    source_app_name: Optional[str] = None
    source_component: Optional[str] = None
    title: Optional[str] = None
    description: Optional[str] = None
    technical_details: Optional[str] = None
    expected_value: Optional[float] = None
    actual_value: Optional[float] = None
    deviation_percent: Optional[float] = None
    metric_name: Optional[str] = None
    metric_unit: Optional[str] = None
    battery_level: Optional[int] = None
    was_charging: bool = False
    screen_on: bool = False
    device_state: Optional[str] = None
    period_start: Optional[int] = None
    period_end: Optional[int] = None
    data_points_analyzed: int = 0
    recommendation: Optional[str] = None
    root_cause: Optional[str] = None
    impact: Optional[str] = None
    acknowledged: bool = False
    resolved: bool = False
    resolved_at: Optional[int] = None
    resolution: Optional[str] = None
    related_anomaly_id: Optional[int] = None
    occurrence_count: int = 1
    is_recurring: bool = False

class RefreshRateSessionRecord(BaseModel):
    """Refresh rate session data from Room DB."""
    device_id: str = Field(..., min_length=1, max_length=64)
    session_id: Optional[str] = None
    session_type: Optional[str] = None
    package_name: Optional[str] = None
    app_name: Optional[str] = None
    start_time: int = 0
    end_time: Optional[int] = None
    duration_ms: int = 0
    start_refresh_rate_hz: float = 0
    end_refresh_rate_hz: float = 0
    avg_refresh_rate_hz: float = 0
    min_refresh_rate_hz: float = 0
    max_refresh_rate_hz: float = 0
    time_at_high_hz_ms: int = 0
    time_at_low_hz_ms: int = 0
    is_adaptive_display: bool = False
    avg_brightness: float = 0
    avg_cpu_usage: float = 0
    avg_gpu_usage: float = 0
    avg_battery_current: float = 0
    avg_temperature: float = 0
    estimated_battery_cost: float = 0
    anomaly_count: int = 0
    has_strong_anomaly: bool = False

class RefreshRateAnomalyRecord(BaseModel):
    """Refresh rate anomaly data from Room DB."""
    device_id: str = Field(..., min_length=1, max_length=64)
    linked_session_id: int = 0
    package_name: Optional[str] = None
    app_name: Optional[str] = None
    session_type: Optional[str] = None
    detected_at: int = 0
    anomaly_type: Optional[str] = None
    severity: Optional[str] = None
    confidence_score: float = 0
    cause_category: Optional[str] = None
    cause_explanation: Optional[str] = None
    evidence_metrics_json: Optional[str] = None
    refresh_rate_hz: float = 0
    cpu_usage: float = 0
    gpu_usage: float = 0
    temperature: float = 0
    brightness: float = 0
    battery_current: float = 0
    was_charging: bool = False
    screen_on: bool = True

class CpuGpuLoadSampleRecord(BaseModel):
    """CPU/GPU load sample data from Room DB cpu_gpu_load_samples table."""
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(..., gt=0)
    app_name: Optional[str] = None
    package_name: Optional[str] = None
    pid: int = 0
    uid: int = 0
    is_system_app: bool = False
    system_cpu_percent: int = -1
    app_cpu_percent: int = -1
    gpu_percent: int = -1
    is_gpu_available: bool = True
    load_1m: float = 0
    load_5m: float = 0
    load_15m: float = 0
    runnable_processes: int = 0
    temperature: float = 0
    battery_level: int = 0
    drain_rate_per_hour: float = 0
    is_throttled: bool = False
    throttle_level: int = 0
    thermal_status: int = 0
    stress_score: float = 0
    stress_category: str = "LOW"
    stress_explanation: Optional[str] = None
    inferred_load: str = "LOW"
    confidence_level: str = "LOW"
    detection_method: str = "Direct hardware counters"
    observed_signals: Optional[str] = None
    source_type: str = "system"
    validation_status: str = "unchecked"
    validation_notes: Optional[str] = None
    is_active: bool = True

# Performance Tables
class MemorySnapshotRecord(BaseModel):
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(..., gt=0)
    total_memory_bytes: int = 0
    available_memory_bytes: int = 0
    used_memory_bytes: int = 0
    used_memory_percent: float = 0
    is_low_memory: bool = False
    threshold: int = 0
    running_app_count: int = 0
    top_memory_app: Optional[str] = None
    top_memory_app_bytes: int = 0
    battery_level: int = 0
    temperature: float = 0
    is_charging: bool = False
    is_screen_on: bool = False

class AppLaunchMetricRecord(BaseModel):
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(..., gt=0)
    package_name: str
    app_label: Optional[str] = None
    launch_type: str
    launch_duration_ms: int = 0
    ttid_ms: int = 0
    ttfd_ms: int = 0
    before_level: int = 0
    after_level: int = 0
    before_temp: float = 0
    after_temp: float = 0
    is_first_launch_today: bool = False
    session_context: Optional[str] = None
    app_version_code: int = 0
    app_version_name: Optional[str] = None
    memory_usage_percent: float = 0
    is_charging: bool = False
    thermal_headroom: float = 0

class FrameMetricRecord(BaseModel):
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(...)
    session_start: int = 0
    session_end: int = 0
    package_name: str
    total_frames: int = 0
    slow_frames: int = 0
    frozen_frames: int = 0
    jank_percentage: float = 0
    avg_frame_duration_ms: float = 0
    max_frame_duration_ms: float = 0
    dropped_frame_count: int = 0
    fps: float = 0
    session_duration_ms: int = 0
    activity_name: Optional[str] = None
    temperature: float = 0
    memory_usage_percent: float = 0
    was_thermal_throttling: bool = False

class CrashEventRecord(BaseModel):
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(...)
    package_name: str
    app_label: Optional[str] = None
    crash_type: str
    exception_type: Optional[str] = None
    exception_message: Optional[str] = None
    stack_trace_hash: Optional[str] = None
    process_name: Optional[str] = None
    is_foreground: bool = False
    app_version_name: Optional[str] = None
    payload: Optional[Dict[str, Any]] = None

class AnrEventRecord(BaseModel):
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(...)
    package_name: str
    app_label: Optional[str] = None
    anr_type: str
    duration_ms: int = 0
    trace_hash: Optional[str] = None
    is_foreground: bool = False
    app_version_name: Optional[str] = None

class DailyAggregateRecord(BaseModel):
    device_id: str = Field(..., min_length=1, max_length=64)
    aggregate_date: str
    package_name: Optional[str] = None
    app_label: Optional[str] = None
    avg_memory_used_percent: float = 0
    max_memory_used_percent: float = 0
    low_memory_event_count: int = 0
    launch_count: int = 0
    cold_launch_count: int = 0
    avg_cold_launch_ms: float = 0
    slow_launch_count: int = 0
    crash_count: int = 0
    anr_count: int = 0
    unique_crash_types: int = 0
    avg_temp: float = 0
    max_temp: float = 0
    total_battery_drain: float = 0
    avg_drain_rate_per_hour: float = 0
    health_score: float = 0
    health_grade: Optional[str] = None
    app_version: Optional[str] = None

class RegressionEventRecord(BaseModel):
    device_id: str = Field(..., min_length=1, max_length=64)
    timestamp: int = Field(...)
    package_name: Optional[str] = None
    regression_type: str
    severity: str
    metric_name: Optional[str] = None
    current_value: float = 0
    baseline_value: float = 0
    deviation_percent: float = 0
    is_resolved: bool = False

class BatchSyncRequest(BaseModel):
    """Batch sync request containing multiple data types."""
    device_info: DeviceInfo
    battery_snapshots: Optional[List[BatterySnapshot]] = []
    charging_sessions: Optional[List[ChargingSession]] = []
    thermal_events: Optional[List[ThermalEvent]] = []
    drain_events: Optional[List[DrainEvent]] = []
    idle_drain_sessions: Optional[List[IdleDrainSession]] = []
    battery_health: Optional[List[BatteryHealth]] = []
    safety_events: Optional[List[SafetyEvent]] = []
    calibration_data: Optional[List[CalibrationData]] = []
    ai_analysis_results: Optional[List[AIAnalysisResult]] = []
    # App data (from Room DB)
    app_battery_usage: Optional[List[AppBatteryUsageRecord]] = []
    app_drain_ranks: Optional[List[AppDrainRankRecord]] = []
    # Additional Room DB tables (complete sync)
    thermal_heat_buckets: Optional[List[ThermalHeatBucketRecord]] = []
    stress_test_results: Optional[List[StressTestResultRecord]] = []
    update_comparisons: Optional[List[UpdateComparisonRecord]] = []
    compliance_reports: Optional[List[ComplianceReportRecord]] = []
    compliance_evidence: Optional[List[ComplianceEvidenceRecord]] = []
    device_info_sync: Optional[List[DeviceInfoSyncRecord]] = []
    anomaly_records: Optional[List[AnomalyRecordRecord]] = []
    # Refresh rate intelligence (new tables)
    refresh_rate_sessions: Optional[List[RefreshRateSessionRecord]] = []
    refresh_rate_anomalies: Optional[List[RefreshRateAnomalyRecord]] = []
    # CPU/GPU load stress monitoring
    cpu_gpu_load_samples: Optional[List[CpuGpuLoadSampleRecord]] = []
    # Performance Monitoring (v3.5)
    memory_snapshots: Optional[List[MemorySnapshotRecord]] = []
    app_launch_metrics: Optional[List[AppLaunchMetricRecord]] = []
    frame_metrics: Optional[List[FrameMetricRecord]] = []
    crash_events: Optional[List[CrashEventRecord]] = []
    anr_events: Optional[List[AnrEventRecord]] = []
    daily_aggregates: Optional[List[DailyAggregateRecord]] = []
    regression_events: Optional[List[RegressionEventRecord]] = []


class SyncResponse(BaseModel):
    """Response for sync operations."""
    success: bool
    message: str
    device_id: str
    records_received: Dict[str, int]
    records_stored: Dict[str, int]
    errors: List[str] = []
    sync_id: Optional[int] = None

# ============================================================================
# DATABASE OPERATIONS
# ============================================================================

def ensure_device_exists(conn, device_info: DeviceInfo) -> bool:
    """Ensure device is registered in the database."""
    cursor = conn.cursor()
    try:
        # Try to insert, update on duplicate
        cursor.execute("""
            INSERT INTO devices (device_id, device_model, android_version, app_version)
            VALUES (%s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
                device_model = VALUES(device_model),
                android_version = VALUES(android_version),
                app_version = VALUES(app_version),
                last_seen = CURRENT_TIMESTAMP
        """, (
            device_info.device_id,
            device_info.device_model,
            device_info.android_version,
            device_info.app_version
        ))
        conn.commit()
        return True
    except Exception as e:
        logger.error(f"Error ensuring device exists: {e}")
        conn.rollback()
        return False
    finally:
        cursor.close()

def insert_battery_snapshots(conn, records: List[BatterySnapshot]) -> int:
    """Insert battery snapshot records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO battery_snapshots 
            (device_id, timestamp, battery_level, temperature, voltage, current_now,
             is_charging, charge_type, screen_on, power_save_mode, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.battery_level, r.temperature, r.voltage,
             r.current_now, r.is_charging, r.charge_type, r.screen_on, 
             r.power_save_mode, json.dumps(r.payload) if r.payload else None)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting battery snapshots: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_charging_sessions(conn, records: List[ChargingSession]) -> int:
    """Insert charging session records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO charging_sessions 
            (device_id, session_id, timestamp, start_level, end_level, start_time, end_time,
             charge_type, max_temperature, avg_temperature, energy_added_mah, 
             charging_speed_ma, is_complete, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.session_id, r.timestamp, r.start_level, r.end_level,
             r.start_time, r.end_time, r.charge_type, r.max_temperature, 
             r.avg_temperature, r.energy_added_mah, r.charging_speed_ma,
             r.is_complete, json.dumps(r.payload) if r.payload else None)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting charging sessions: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_thermal_events(conn, records: List[ThermalEvent]) -> int:
    """Insert thermal event records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT INTO thermal_events 
            (device_id, timestamp, temperature, threshold_exceeded, duration_seconds,
             trigger_source, cpu_usage, foreground_app, severity, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.temperature, r.threshold_exceeded,
             r.duration_seconds, r.trigger_source, r.cpu_usage, r.foreground_app,
             r.severity, json.dumps(r.payload) if r.payload else None)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting thermal events: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_drain_events(conn, records: List[DrainEvent]) -> int:
    """Insert drain event records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT INTO drain_events 
            (device_id, timestamp, start_level, end_level, duration_minutes,
             drain_rate_per_hour, screen_on_time_minutes, suspected_cause, top_apps, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.start_level, r.end_level, r.duration_minutes,
             r.drain_rate_per_hour, r.screen_on_time_minutes, r.suspected_cause,
             json.dumps(r.top_apps) if r.top_apps else None,
             json.dumps(r.payload) if r.payload else None)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting drain events: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_idle_drain_sessions(conn, records: List[IdleDrainSession]) -> int:
    """Insert idle drain session records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO idle_drain_sessions 
            (device_id, session_id, timestamp, start_level, end_level, start_time, end_time,
             total_drain, drain_rate_per_hour, screen_off_duration_minutes, doze_time_minutes,
             wake_locks_count, network_activity_mb, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.session_id, r.timestamp, r.start_level, r.end_level,
             r.start_time, r.end_time, r.total_drain, r.drain_rate_per_hour,
             r.screen_off_duration_minutes, r.doze_time_minutes, r.wake_locks_count,
             r.network_activity_mb, json.dumps(r.payload) if r.payload else None)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting idle drain sessions: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_battery_health(conn, records: List[BatteryHealth]) -> int:
    """Insert battery health records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO battery_health 
            (device_id, timestamp, health_percentage, cycle_count, design_capacity_mah,
             current_capacity_mah, charge_full_design, charge_full, technology,
             manufacture_date, first_use_date, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.health_percentage, r.cycle_count,
             r.design_capacity_mah, r.current_capacity_mah, r.charge_full_design,
             r.charge_full, r.technology, r.manufacture_date, r.first_use_date,
             json.dumps(r.payload) if r.payload else None)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting battery health: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_safety_events(conn, records: List[SafetyEvent]) -> int:
    """Insert safety event records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO safety_events 
            (device_id, timestamp, event_type, severity, temperature, voltage,
             current_ma, description, action_taken, user_notified, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.event_type, r.severity, r.temperature,
             r.voltage, r.current_ma, r.description, r.action_taken, r.user_notified,
             json.dumps(r.payload) if r.payload else None)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting safety events: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_calibration_data(conn, records: List[CalibrationData]) -> int:
    """Insert calibration data records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO calibration_data 
            (device_id, timestamp, calibration_type, old_value, new_value,
             accuracy_score, reference_source, is_automatic, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.calibration_type, r.old_value, r.new_value,
             r.accuracy_score, r.reference_source, r.is_automatic,
             json.dumps(r.payload) if r.payload else None)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting calibration data: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_ai_analysis_results(conn, records: List[AIAnalysisResult]) -> int:
    """Insert AI analysis result records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO ai_analysis_results 
            (device_id, timestamp, analysis_type, confidence_score, prediction,
             features_used, model_version, execution_time_ms, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.analysis_type, r.confidence_score,
             json.dumps(r.prediction) if r.prediction else None,
             json.dumps(r.features_used) if r.features_used else None,
             r.model_version, r.execution_time_ms,
             json.dumps(r.payload) if r.payload else None)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting AI analysis results: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

# ============================================================================
# APP DATA INSERT FUNCTIONS (From Room DB)
# ============================================================================

def insert_app_battery_usage(conn, records: List['AppBatteryUsageRecord']) -> int:
    """Insert app battery usage records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO app_battery_usage 
            (device_id, timestamp, period_start, period_end, package_name, app_name,
             is_system_app, is_preinstalled, app_category, foreground_time, background_time,
             battery_percent_used, drain_per_hour, cpu_time_ms, network_bytes, wakelock_count,
             wakelock_time, partial_wakelock_time, alarm_wake_count, network_wake_count,
             wakelock_drain_score, alarm_count, gps_requests, background_wakes, push_notifications,
             background_services, has_excessive_background, category_average, deviation_from_average,
             efficiency_rating, drain_reasons, optimization_tips, needs_restriction)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.period_start, r.period_end, r.package_name, r.app_name,
             r.is_system_app, r.is_preinstalled, r.app_category, r.foreground_time, r.background_time,
             r.battery_percent_used, r.drain_per_hour, r.cpu_time_ms, r.network_bytes, r.wakelock_count,
             r.wakelock_time, r.partial_wakelock_time, r.alarm_wake_count, r.network_wake_count,
             r.wakelock_drain_score, r.alarm_count, r.gps_requests, r.background_wakes, r.push_notifications,
             r.background_services, r.has_excessive_background, r.category_average, r.deviation_from_average,
             r.efficiency_rating, r.drain_reasons, r.optimization_tips, r.needs_restriction)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting app battery usage: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_app_drain_ranks(conn, records: List['AppDrainRankRecord']) -> int:
    """Insert app drain rank records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO app_drain_ranks 
            (device_id, window_label, window_start, window_end, rank_position, package_name,
             drain_score, contribution_pct, screen_time_ms, foreground_time_ms, background_time_ms)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.window_label, r.window_start, r.window_end, r.rank_position, r.package_name,
             r.drain_score, r.contribution_pct, r.screen_time_ms, r.foreground_time_ms, r.background_time_ms)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting app drain ranks: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

# ============================================================================
# INSERT FUNCTIONS FOR ADDITIONAL ROOM DB TABLES (Complete Sync)
# ============================================================================

def insert_thermal_heat_buckets(conn, records: List['ThermalHeatBucketRecord']) -> int:
    """Insert thermal heat bucket records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO thermal_heat_buckets 
            (device_id, start_time, end_time, temp_bucket, hour_block, sample_count, duration_ms,
             max_temp, avg_temp, min_temp, temp_variance, charging_count, charging_percent,
             avg_temp_charging, avg_temp_not_charging, avg_charge_current, fast_charging_count,
             high_cpu_count, high_gpu_count, avg_cpu_load, avg_gpu_load, avg_temp_high_cpu,
             avg_temp_high_gpu, top_foreground_app, screen_on_count, screen_on_percent,
             app_temp_contribution_json, gaming_count, camera_count, navigation_count, network_type,
             spike_count, sustained_heat_count, warning_event_count, critical_event_count,
             avg_battery_level, battery_drain, degradation_risk, thermal_score, exceeds_oem_limit,
             oem_compliance_status)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.start_time, r.end_time, r.temp_bucket, r.hour_block, r.sample_count,
             r.duration_ms, r.max_temp, r.avg_temp, r.min_temp, r.temp_variance, r.charging_count,
             r.charging_percent, r.avg_temp_charging, r.avg_temp_not_charging, r.avg_charge_current,
             r.fast_charging_count, r.high_cpu_count, r.high_gpu_count, r.avg_cpu_load, r.avg_gpu_load,
             r.avg_temp_high_cpu, r.avg_temp_high_gpu, r.top_foreground_app, r.screen_on_count,
             r.screen_on_percent, r.app_temp_contribution_json, r.gaming_count, r.camera_count,
             r.navigation_count, r.network_type, r.spike_count, r.sustained_heat_count,
             r.warning_event_count, r.critical_event_count, r.avg_battery_level, r.battery_drain,
             r.degradation_risk, r.thermal_score, r.exceeds_oem_limit, r.oem_compliance_status)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting thermal heat buckets: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_stress_test_results(conn, records: List['StressTestResultRecord']) -> int:
    """Insert stress test result records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO stress_test_results 
            (device_id, timestamp, test_start_time, test_end_time, test_type, test_name,
             test_duration_minutes, test_parameters, high_temperature_scenario, start_level, end_level,
             drain_amount, drain_rate, peak_drain_rate, start_temperature, max_temperature,
             end_temperature, avg_temperature, thermal_throttle_events, thermal_throttle_time,
             safety_trigger_count, safety_notes, avg_cpu_usage, max_cpu_usage, avg_gpu_usage,
             max_gpu_usage, avg_memory_usage, max_memory_usage, charge_gained, avg_charging_current,
             max_charging_current, charging_interruptions, test_completed, crash_count, restart_count,
             error_count, failure_reasons, drain_score, thermal_score, stability_score,
             thermal_resilience_score, charging_stability_score, overall_score, baseline_drain,
             drain_variance, baseline_thermal, thermal_variance, passed, verdict, verdict_details,
             test_logs, recommendations)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.test_start_time, r.test_end_time, r.test_type, r.test_name,
             r.test_duration_minutes, r.test_parameters, r.high_temperature_scenario, r.start_level,
             r.end_level, r.drain_amount, r.drain_rate, r.peak_drain_rate, r.start_temperature,
             r.max_temperature, r.end_temperature, r.avg_temperature, r.thermal_throttle_events,
             r.thermal_throttle_time, r.safety_trigger_count, r.safety_notes, r.avg_cpu_usage,
             r.max_cpu_usage, r.avg_gpu_usage, r.max_gpu_usage, r.avg_memory_usage, r.max_memory_usage,
             r.charge_gained, r.avg_charging_current, r.max_charging_current, r.charging_interruptions,
             r.test_completed, r.crash_count, r.restart_count, r.error_count, r.failure_reasons,
             r.drain_score, r.thermal_score, r.stability_score, r.thermal_resilience_score,
             r.charging_stability_score, r.overall_score, r.baseline_drain, r.drain_variance,
             r.baseline_thermal, r.thermal_variance, r.passed, r.verdict, r.verdict_details,
             r.test_logs, r.recommendations)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting stress test results: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_update_comparisons(conn, records: List['UpdateComparisonRecord']) -> int:
    """Insert update comparison records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO update_comparisons 
            (device_id, timestamp, previous_version, current_version, update_timestamp, update_type,
             pre_drain_rate, pre_avg_drain_screen_on, pre_avg_drain_screen_off, pre_avg_charging_time,
             pre_avg_temperature, pre_max_temperature, pre_idle_drain, pre_battery_health,
             post_drain_rate, post_avg_drain_screen_on, post_avg_drain_screen_off, post_avg_charging_time,
             post_avg_temperature, post_max_temperature, post_idle_drain, post_battery_health,
             drain_rate_delta, screen_on_drain_delta, screen_off_drain_delta, charging_time_delta,
             temperature_delta, thermal_peak_delta, idle_drain_delta, drain_rate_change,
             screen_on_drain_change, screen_off_drain_change, charging_time_change, temperature_change,
             thermal_peak_change, idle_drain_change, overall_impact, impact_score, impact_details,
             has_drain_regression, has_charging_regression, has_thermal_regression, has_idle_regression,
             recommendation, analysis_notes, confidence_level, pre_series_json, post_series_json)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.previous_version, r.current_version, r.update_timestamp,
             r.update_type, r.pre_drain_rate, r.pre_avg_drain_screen_on, r.pre_avg_drain_screen_off,
             r.pre_avg_charging_time, r.pre_avg_temperature, r.pre_max_temperature, r.pre_idle_drain,
             r.pre_battery_health, r.post_drain_rate, r.post_avg_drain_screen_on, r.post_avg_drain_screen_off,
             r.post_avg_charging_time, r.post_avg_temperature, r.post_max_temperature, r.post_idle_drain,
             r.post_battery_health, r.drain_rate_delta, r.screen_on_drain_delta, r.screen_off_drain_delta,
             r.charging_time_delta, r.temperature_delta, r.thermal_peak_delta, r.idle_drain_delta,
             r.drain_rate_change, r.screen_on_drain_change, r.screen_off_drain_change, r.charging_time_change,
             r.temperature_change, r.thermal_peak_change, r.idle_drain_change, r.overall_impact,
             r.impact_score, r.impact_details, r.has_drain_regression, r.has_charging_regression,
             r.has_thermal_regression, r.has_idle_regression, r.recommendation, r.analysis_notes,
             r.confidence_level, r.pre_series_json, r.post_series_json)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting update comparisons: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_compliance_reports(conn, records: List['ComplianceReportRecord']) -> int:
    """Insert compliance report records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO compliance_reports 
            (device_id, timestamp, period_start, period_end, report_type, report_version, generated_by,
             device_model, device_serial, firmware_version, battery_part_number, safety_compliant,
             safety_violation_count, safety_violations, safety_score, max_operating_temp, max_charging_temp,
             temp_compliant, temp_violation_count, temp_violation_details, min_voltage, max_voltage,
             voltage_compliant, voltage_violation_count, voltage_violation_details, max_charging_current,
             max_discharge_current, current_compliant, current_violation_count, current_violation_details,
             lowest_voltage, over_discharge_protection_working, over_discharge_events, highest_voltage,
             overcharge_protection_working, overcharge_events, thermal_protection_working,
             thermal_shutdown_events, thermal_throttle_events, iec_compliant, ul_compliant, un_compliant,
             standards_notes, certification_readiness, certification_gaps, certification_recommendations,
             evidence_summary, evidence_ids, evidence_count, audit_trail, data_points_analyzed,
             data_quality, executive_summary, technical_details, compliance_matrix, attachments,
             review_status, review_notes, review_timestamp)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.period_start, r.period_end, r.report_type, r.report_version,
             r.generated_by, r.device_model, r.device_serial, r.firmware_version, r.battery_part_number,
             r.safety_compliant, r.safety_violation_count, r.safety_violations, r.safety_score,
             r.max_operating_temp, r.max_charging_temp, r.temp_compliant, r.temp_violation_count,
             r.temp_violation_details, r.min_voltage, r.max_voltage, r.voltage_compliant,
             r.voltage_violation_count, r.voltage_violation_details, r.max_charging_current,
             r.max_discharge_current, r.current_compliant, r.current_violation_count,
             r.current_violation_details, r.lowest_voltage, r.over_discharge_protection_working,
             r.over_discharge_events, r.highest_voltage, r.overcharge_protection_working,
             r.overcharge_events, r.thermal_protection_working, r.thermal_shutdown_events,
             r.thermal_throttle_events, r.iec_compliant, r.ul_compliant, r.un_compliant,
             r.standards_notes, r.certification_readiness, r.certification_gaps,
             r.certification_recommendations, r.evidence_summary, r.evidence_ids, r.evidence_count,
             r.audit_trail, r.data_points_analyzed, r.data_quality, r.executive_summary,
             r.technical_details, r.compliance_matrix, r.attachments, r.review_status,
             r.review_notes, r.review_timestamp)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting compliance reports: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_compliance_evidence(conn, records: List['ComplianceEvidenceRecord']) -> int:
    """Insert compliance evidence records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO compliance_evidence 
            (device_id, timestamp, rule_id, metric_value, threshold_value, source_module,
             severity, category, source_event_id, notes)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.rule_id, r.metric_value, r.threshold_value,
             r.source_module, r.severity, r.category, r.source_event_id, r.notes)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting compliance evidence: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_device_info_sync(conn, records: List['DeviceInfoSyncRecord']) -> int:
    """Insert device info sync records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT INTO device_info_sync 
            (device_id, timestamp, first_recorded, manufacturer, model, product, brand, device, board,
             hardware, android_version, sdk_version, build_number, security_patch, kernel_version,
             firmware_version, battery_technology, design_capacity_mah, battery_part_number,
             battery_manufacturer, battery_batch_id, processor, cpu_cores, ram_size, storage_size,
             screen_width, screen_height, screen_density, oem_sku, region_code, carrier_code,
             production_date, warranty_status, hexnil_install_date, hexnil_version, total_snapshots,
             total_analyses)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
                timestamp = VALUES(timestamp),
                manufacturer = VALUES(manufacturer),
                model = VALUES(model),
                android_version = VALUES(android_version),
                hexnil_version = VALUES(hexnil_version),
                total_snapshots = VALUES(total_snapshots),
                total_analyses = VALUES(total_analyses)
        """
        data = [
            (r.device_id, r.timestamp, r.first_recorded, r.manufacturer, r.model, r.product, r.brand,
             r.device, r.board, r.hardware, r.android_version, r.sdk_version, r.build_number,
             r.security_patch, r.kernel_version, r.firmware_version, r.battery_technology,
             r.design_capacity_mah, r.battery_part_number, r.battery_manufacturer, r.battery_batch_id,
             r.processor, r.cpu_cores, r.ram_size, r.storage_size, r.screen_width, r.screen_height,
             r.screen_density, r.oem_sku, r.region_code, r.carrier_code, r.production_date,
             r.warranty_status, r.hexnil_install_date, r.hexnil_version, r.total_snapshots,
             r.total_analyses)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting device info sync: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_anomaly_records(conn, records: List['AnomalyRecordRecord']) -> int:
    """Insert anomaly record records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO anomaly_records 
            (device_id, detected_at, anomaly_type, severity, confidence_score, source_app,
             source_app_name, source_component, title, description, technical_details,
             expected_value, actual_value, deviation_percent, metric_name, metric_unit,
             battery_level, was_charging, screen_on, device_state, period_start, period_end,
             data_points_analyzed, recommendation, root_cause, impact, acknowledged, resolved,
             resolved_at, resolution, related_anomaly_id, occurrence_count, is_recurring)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.detected_at, r.anomaly_type, r.severity, r.confidence_score,
             r.source_app, r.source_app_name, r.source_component, r.title, r.description,
             r.technical_details, r.expected_value, r.actual_value, r.deviation_percent,
             r.metric_name, r.metric_unit, r.battery_level, r.was_charging, r.screen_on,
             r.device_state, r.period_start, r.period_end, r.data_points_analyzed,
             r.recommendation, r.root_cause, r.impact, r.acknowledged, r.resolved,
             r.resolved_at, r.resolution, r.related_anomaly_id, r.occurrence_count, r.is_recurring)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting anomaly records: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_refresh_rate_sessions(conn, records: List['RefreshRateSessionRecord']) -> int:
    """Insert refresh rate session records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO refresh_rate_sessions 
            (device_id, session_id, session_type, package_name, app_name, start_time, end_time,
             duration_ms, start_refresh_rate_hz, end_refresh_rate_hz, avg_refresh_rate_hz,
             min_refresh_rate_hz, max_refresh_rate_hz, time_at_high_hz_ms, time_at_low_hz_ms,
             is_adaptive_display, avg_brightness, avg_cpu_usage, avg_gpu_usage, avg_battery_current,
             avg_temperature, estimated_battery_cost, anomaly_count, has_strong_anomaly)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.session_id, r.session_type, r.package_name, r.app_name,
             r.start_time, r.end_time, r.duration_ms, r.start_refresh_rate_hz,
             r.end_refresh_rate_hz, r.avg_refresh_rate_hz, r.min_refresh_rate_hz,
             r.max_refresh_rate_hz, r.time_at_high_hz_ms, r.time_at_low_hz_ms,
             r.is_adaptive_display, r.avg_brightness, r.avg_cpu_usage, r.avg_gpu_usage,
             r.avg_battery_current, r.avg_temperature, r.estimated_battery_cost,
             r.anomaly_count, r.has_strong_anomaly)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting refresh rate sessions: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_refresh_rate_anomalies(conn, records: List['RefreshRateAnomalyRecord']) -> int:
    """Insert refresh rate anomaly records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO refresh_rate_anomalies 
            (device_id, linked_session_id, package_name, app_name, session_type, detected_at, anomaly_type,
             severity, confidence_score, cause_category, cause_explanation, evidence_metrics_json,
             refresh_rate_hz, cpu_usage, gpu_usage, temperature, brightness, battery_current,
             was_charging, screen_on)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.linked_session_id, r.package_name, r.app_name, r.session_type, r.detected_at,
             r.anomaly_type, r.severity, r.confidence_score, r.cause_category, r.cause_explanation,
             r.evidence_metrics_json, r.refresh_rate_hz, r.cpu_usage, r.gpu_usage, r.temperature,
             r.brightness, r.battery_current, r.was_charging, r.screen_on)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting refresh rate anomalies: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_cpu_gpu_load_samples(conn, records: List['CpuGpuLoadSampleRecord']) -> int:
    """Insert CPU/GPU load sample records."""
    if not records:
        return 0
    
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO cpu_gpu_load_samples 
            (device_id, timestamp, app_name, package_name, pid, uid, is_system_app,
             system_cpu_percent, app_cpu_percent, gpu_percent, is_gpu_available,
             load_1m, load_5m, load_15m, runnable_processes,
             temperature, battery_level, drain_rate_per_hour,
             is_throttled, throttle_level, thermal_status, stress_score, stress_category,
             stress_explanation, inferred_load, confidence_level, detection_method, 
             observed_signals, source_type, validation_status, validation_notes, is_active)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [
            (r.device_id, r.timestamp, r.app_name, r.package_name, r.pid, r.uid, r.is_system_app,
             r.system_cpu_percent, r.app_cpu_percent, r.gpu_percent, r.is_gpu_available,
             r.load_1m, r.load_5m, r.load_15m, r.runnable_processes,
             r.temperature, r.battery_level, r.drain_rate_per_hour,
             r.is_throttled, r.throttle_level, r.thermal_status, r.stress_score, r.stress_category,
             r.stress_explanation, r.inferred_load, r.confidence_level, r.detection_method,
             r.observed_signals, r.source_type, r.validation_status, r.validation_notes, r.is_active)
            for r in records
        ]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting cpu_gpu_load_samples: {e}")
        conn.rollback()
        raise
    finally:
        cursor.close()

def insert_memory_snapshots(conn, records: List[MemorySnapshotRecord]) -> int:
    """Insert memory snapshot records."""
    if not records: return 0
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO memory_snapshots 
            (device_id, timestamp, total_memory_bytes, available_memory_bytes, used_memory_bytes,
             used_memory_percent, is_low_memory, threshold, running_app_count, top_memory_app,
             top_memory_app_bytes, battery_level, temperature, is_charging, is_screen_on)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [(r.device_id, r.timestamp, r.total_memory_bytes, r.available_memory_bytes,
                 r.used_memory_bytes, r.used_memory_percent, r.is_low_memory, r.threshold,
                 r.running_app_count, r.top_memory_app, r.top_memory_app_bytes, r.battery_level,
                 r.temperature, r.is_charging, r.is_screen_on) for r in records]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting memory snapshots: {e}"); conn.rollback(); raise
    finally: cursor.close()

def insert_app_launch_metrics(conn, records: List[AppLaunchMetricRecord]) -> int:
    """Insert app launch metric records."""
    if not records: return 0
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO app_launch_metrics 
            (device_id, timestamp, package_name, app_label, launch_type, launch_time_ms,
             is_slow_launch, app_version, android_version, battery_level, temperature, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [(r.device_id, r.timestamp, r.package_name, r.app_label, r.launch_type, r.launch_duration_ms,
                 r.launch_duration_ms > 2000, r.app_version_name, None, r.after_level, r.after_temp,
                 json.dumps({"ttid_ms": r.ttid_ms, "ttfd_ms": r.ttfd_ms, "context": r.session_context}))
                for r in records]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting app launch metrics: {e}"); conn.rollback(); raise
    finally: cursor.close()

def insert_frame_metrics(conn, records: List[FrameMetricRecord]) -> int:
    """Insert frame metric records."""
    if not records: return 0
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO frame_metrics 
            (device_id, timestamp, package_name, window_name, total_frames, slow_frames,
             frozen_frames, jank_percent, avg_fps, duration_ms, is_low_memory_during_session,
             max_frame_time_ms, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [(r.device_id, r.timestamp, r.package_name, r.activity_name, r.total_frames, r.slow_frames,
                 r.frozen_frames, r.jank_percentage, r.fps, r.session_duration_ms, False,
                 r.max_frame_duration_ms, json.dumps({"p95": 0, "was_thermal": r.was_thermal_throttling}))
                for r in records]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting frame metrics: {e}"); conn.rollback(); raise
    finally: cursor.close()

def insert_crash_events(conn, records: List[CrashEventRecord]) -> int:
    """Insert crash event records."""
    if not records: return 0
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO crash_events 
            (device_id, timestamp, package_name, process_name, reason, short_msg, long_msg,
             stack_trace_hash, is_foreground, app_version, payload)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [(r.device_id, r.timestamp, r.package_name, r.process_name, r.crash_type,
                 r.exception_type, r.exception_message, r.stack_trace_hash, r.is_foreground,
                 r.app_version_name, json.dumps(r.payload) if r.payload else None) for r in records]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting crash events: {e}"); conn.rollback(); raise
    finally: cursor.close()

def insert_anr_events(conn, records: List[AnrEventRecord]) -> int:
    """Insert ANR event records."""
    if not records: return 0
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO anr_events 
            (device_id, timestamp, package_name, process_name, reason, error_msg,
             stack_trace_hash, is_foreground, app_version)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [(r.device_id, r.timestamp, r.package_name, None, r.anr_type, None,
                 r.trace_hash, r.is_foreground, r.app_version_name) for r in records]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting ANR events: {e}"); conn.rollback(); raise
    finally: cursor.close()

def insert_daily_aggregates(conn, records: List[DailyAggregateRecord]) -> int:
    """Insert daily aggregate records."""
    if not records: return 0
    cursor = conn.cursor()
    try:
        sql = """
            INSERT INTO daily_aggregates 
            (device_id, aggregate_date, package_name, app_label, avg_memory_used_percent,
             max_memory_used_percent, low_memory_event_count, launch_count, cold_launch_count,
             avg_cold_launch_ms, slow_launch_count, crash_count, anr_count, unique_crash_types,
             avg_temp, max_temp, total_battery_drain, avg_drain_rate_per_hour, health_score,
             health_grade, app_version)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
                avg_memory_used_percent = VALUES(avg_memory_used_percent),
                health_score = VALUES(health_score),
                health_grade = VALUES(health_grade)
        """
        data = [(r.device_id, r.aggregate_date, r.package_name, r.app_label, r.avg_memory_used_percent,
                 r.max_memory_used_percent, r.low_memory_event_count, r.launch_count, r.cold_launch_count,
                 r.avg_cold_launch_ms, r.slow_launch_count, r.crash_count, r.anr_count, r.unique_crash_types,
                 r.avg_temp, r.max_temp, r.total_battery_drain, r.avg_drain_rate_per_hour, r.health_score,
                 r.health_grade, r.app_version) for r in records]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting daily aggregates: {e}"); conn.rollback(); raise
    finally: cursor.close()

def insert_regression_events(conn, records: List[RegressionEventRecord]) -> int:
    """Insert regression event records."""
    if not records: return 0
    cursor = conn.cursor()
    try:
        sql = """
            INSERT IGNORE INTO regression_events 
            (device_id, timestamp, package_name, regression_type, severity, metric_name,
             current_value, baseline_value, deviation_percent, is_resolved)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        data = [(r.device_id, r.timestamp, r.package_name, r.regression_type, r.severity, r.metric_name,
                 r.current_value, r.baseline_value, r.deviation_percent, r.is_resolved) for r in records]
        cursor.executemany(sql, data)
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        logger.error(f"Error inserting regression events: {e}"); conn.rollback(); raise
    finally: cursor.close()

def log_sync(conn, device_id: str, data_type: str, records_synced: int, 
             status: str, error_message: Optional[str] = None) -> Optional[int]:
    """Log sync operation to database."""
    cursor = conn.cursor()
    try:
        cursor.execute("""
            INSERT INTO sync_log (device_id, data_type, records_synced, status, error_message)
            VALUES (%s, %s, %s, %s, %s)
        """, (device_id, data_type, records_synced, status, error_message))
        conn.commit()
        return cursor.lastrowid
    except Exception as e:
        logger.error(f"Error logging sync: {e}")
        conn.rollback()
        return None
    finally:
        cursor.close()

# ============================================================================
# FASTAPI APPLICATION
# ============================================================================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan handler."""
    # Startup
    logger.info("Starting HexNil Backend Server...")
    init_spool()
    if not init_db_pool():
        logger.error("Failed to connect to database. Server may not function properly.")

    stop_event = asyncio.Event()

    async def drain_spool_forever():
        logger.info("Ingestion spool worker started")
        while not stop_event.is_set():
            try:
                items = claim_batch(limit=Config.SPOOL_BATCH_SIZE)
                if not items:
                    await asyncio.sleep(Config.SPOOL_POLL_INTERVAL_MS / 1000.0)
                    continue

                for item in items:
                    try:
                        payload_dict = json.loads(item.payload_json)
                        batch = BatchSyncRequest.model_validate(payload_dict)

                        conn = get_db_connection()
                        if not ensure_device_exists(conn, batch.device_info):
                            conn.close()
                            raise RuntimeError("Failed to register device")

                        # Process each data type
                        data_handlers = [
                            ("battery_snapshots", batch.battery_snapshots, insert_battery_snapshots),
                            ("charging_sessions", batch.charging_sessions, insert_charging_sessions),
                            ("thermal_events", batch.thermal_events, insert_thermal_events),
                            ("drain_events", batch.drain_events, insert_drain_events),
                            ("idle_drain_sessions", batch.idle_drain_sessions, insert_idle_drain_sessions),
                            ("battery_health", batch.battery_health, insert_battery_health),
                            ("safety_events", batch.safety_events, insert_safety_events),
                            ("calibration_data", batch.calibration_data, insert_calibration_data),
                            ("ai_analysis_results", batch.ai_analysis_results, insert_ai_analysis_results),
                            # App data (from Room DB)
                            ("app_battery_usage", batch.app_battery_usage, insert_app_battery_usage),
                            ("app_drain_ranks", batch.app_drain_ranks, insert_app_drain_ranks),
                            # Additional Room DB tables (complete sync)
                            ("thermal_heat_buckets", batch.thermal_heat_buckets, insert_thermal_heat_buckets),
                            ("stress_test_results", batch.stress_test_results, insert_stress_test_results),
                            ("update_comparisons", batch.update_comparisons, insert_update_comparisons),
                            ("compliance_reports", batch.compliance_reports, insert_compliance_reports),
                            ("compliance_evidence", batch.compliance_evidence, insert_compliance_evidence),
                            ("device_info_sync", batch.device_info_sync, insert_device_info_sync),
                            ("anomaly_records", batch.anomaly_records, insert_anomaly_records),
                            # Refresh rate intelligence
                            ("refresh_rate_sessions", batch.refresh_rate_sessions, insert_refresh_rate_sessions),
                            ("refresh_rate_anomalies", batch.refresh_rate_anomalies, insert_refresh_rate_anomalies),
                            # CPU/GPU load stress monitoring
                            ("cpu_gpu_load_samples", batch.cpu_gpu_load_samples, insert_cpu_gpu_load_samples),
                            # Performance Monitoring (v3.5)
                            ("memory_snapshots", batch.memory_snapshots, insert_memory_snapshots),
                            ("app_launch_metrics", batch.app_launch_metrics, insert_app_launch_metrics),
                            ("frame_metrics", batch.frame_metrics, insert_frame_metrics),
                            ("crash_events", batch.crash_events, insert_crash_events),
                            ("anr_events", batch.anr_events, insert_anr_events),
                            ("daily_aggregates", batch.daily_aggregates, insert_daily_aggregates),
                            ("regression_events", batch.regression_events, insert_regression_events),
                        ]

                        total_received = 0
                        total_stored = 0
                        errors = []

                        for data_type, records, handler in data_handlers:
                            rec_count = len(records) if records else 0
                            if rec_count == 0:
                                continue

                            if rec_count > Config.MAX_BATCH_SIZE:
                                records = records[: Config.MAX_BATCH_SIZE]
                                rec_count = len(records)

                            total_received += rec_count
                            try:
                                stored = handler(conn, records)
                                total_stored += stored
                                log_sync(conn, batch.device_info.device_id, data_type, stored, "success")
                            except Exception as e:
                                errors.append(f"{data_type}: {str(e)}")
                                log_sync(conn, batch.device_info.device_id, data_type, 0, "error", str(e))

                        conn.close()

                        if errors:
                            raise RuntimeError("; ".join(errors))

                        mark_done(item.id)
                    except Exception as e:
                        mark_failed(item.id, str(e), attempts=item.attempts)
            except Exception as e:
                logger.error(f"Spool drain worker error: {e}")
                await asyncio.sleep(1.0)

        logger.info("Ingestion spool worker stopped")

    task = asyncio.create_task(drain_spool_forever())
    yield
    # Shutdown
    logger.info("Shutting down HexNil Backend Server...")
    stop_event.set()
    try:
        await asyncio.wait_for(task, timeout=5)
    except Exception:
        pass
    if db_pool:
        # Pool cleanup handled by garbage collection
        pass

app = FastAPI(
    title="HexNil Backend API",
    description="Battery monitoring data ingestion API for HexNil Android app",
    version="1.0.0",
    lifespan=lifespan
)

# CORS middleware for cross-origin requests
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ============================================================================
# API ENDPOINTS
# ============================================================================

@app.get("/")
async def root():
    """Root endpoint - health check."""
    return {"status": "online", "service": "HexNil Backend", "version": "1.0.0"}

@app.get("/health")
async def health_check():
    """Detailed health check endpoint."""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT 1")
        cursor.fetchone()
        cursor.close()
        conn.close()
        db_status = "connected"
    except Exception as e:
        db_status = f"error: {str(e)}"
    
    return {
        "status": "healthy" if db_status == "connected" else "degraded",
        "database": db_status,
        "spool": pending_counts(),
        "timestamp": datetime.now(timezone.utc).isoformat()
    }

@app.post("/api/v1/sync", response_model=SyncResponse)
async def sync_data(request: BatchSyncRequest):
    """
    Main sync endpoint - receives batch data from Android devices.
    
    This endpoint handles all data types in a single request, making it
    efficient for Android to sync all pending data at once.
    """
    device_id = request.device_info.device_id
    records_received = {}
    records_stored = {}
    errors = []
    
    logger.info(f"Sync request accepted from device: {device_id}")

    try:
        # Non-blocking: persist to local spool immediately, return success/accepted.
        payload = request.model_dump(mode="json")
        spool_id = enqueue(device_id=device_id, payload=payload)

        # counts for response
        for key in [
            "battery_snapshots",
            "charging_sessions",
            "thermal_events",
            "drain_events",
            "idle_drain_sessions",
            "battery_health",
            "safety_events",
            "calibration_data",
            "ai_analysis_results",
            # App data (from Room DB)
            "app_battery_usage",
            "app_drain_ranks",
        ]:
            val = getattr(request, key)
            records_received[key] = len(val) if val else 0
            records_stored[key] = 0

        return SyncResponse(
            success=True,
            message="Accepted for processing",
            device_id=device_id,
            records_received=records_received,
            records_stored=records_stored,
            errors=[],
            sync_id=spool_id
        )
    except Exception as e:
        logger.error(f"Failed to enqueue sync for device {device_id}: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/v1/device/register")
async def register_device(device_info: DeviceInfo):
    """Register or update a device."""
    try:
        conn = get_db_connection()
        success = ensure_device_exists(conn, device_info)
        conn.close()
        
        if success:
            return {"success": True, "message": "Device registered successfully"}
        else:
            raise HTTPException(status_code=500, detail="Failed to register device")
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Device registration error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/v1/device/{device_id}/status")
async def get_device_status(device_id: str):
    """Get device sync status and statistics."""
    try:
        conn = get_db_connection()
        cursor = conn.cursor(dictionary=True)
        
        # Get device info
        cursor.execute("""
            SELECT device_id, device_model, android_version, app_version, 
                   first_seen, last_seen
            FROM devices WHERE device_id = %s
        """, (device_id,))
        device = cursor.fetchone()
        
        if not device:
            raise HTTPException(status_code=404, detail="Device not found")
        
        # Get record counts
        tables = [
            "battery_snapshots", "charging_sessions", "thermal_events",
            "drain_events", "idle_drain_sessions", "battery_health",
            "safety_events", "calibration_data", "ai_analysis_results"
        ]
        
        record_counts = {}
        for table in tables:
            cursor.execute(f"SELECT COUNT(*) as count FROM {table} WHERE device_id = %s", (device_id,))
            result = cursor.fetchone()
            record_counts[table] = result['count']
        
        # Get last sync
        cursor.execute("""
            SELECT sync_timestamp, data_type, records_synced, status
            FROM sync_log WHERE device_id = %s
            ORDER BY sync_timestamp DESC LIMIT 1
        """, (device_id,))
        last_sync = cursor.fetchone()
        
        cursor.close()
        conn.close()
        
        return {
            "device": device,
            "record_counts": record_counts,
            "total_records": sum(record_counts.values()),
            "last_sync": last_sync
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error getting device status: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/v1/stats")
async def get_global_stats():
    """Get global statistics for all devices."""
    try:
        conn = get_db_connection()
        cursor = conn.cursor(dictionary=True)
        
        # Get device count
        cursor.execute("SELECT COUNT(*) as count FROM devices")
        device_count = cursor.fetchone()['count']
        
        # Get total records per table
        tables = [
            "battery_snapshots", "charging_sessions", "thermal_events",
            "drain_events", "idle_drain_sessions", "battery_health",
            "safety_events", "calibration_data", "ai_analysis_results"
        ]
        
        record_counts = {}
        for table in tables:
            cursor.execute(f"SELECT COUNT(*) as count FROM {table}")
            result = cursor.fetchone()
            record_counts[table] = result['count']
        
        # Get recent sync activity
        cursor.execute("""
            SELECT DATE(sync_timestamp) as date, COUNT(*) as syncs, SUM(records_synced) as records
            FROM sync_log
            WHERE sync_timestamp >= DATE_SUB(NOW(), INTERVAL 7 DAY)
            GROUP BY DATE(sync_timestamp)
            ORDER BY date DESC
        """)
        recent_activity = cursor.fetchall()
        
        cursor.close()
        conn.close()
        
        return {
            "device_count": device_count,
            "record_counts": record_counts,
            "total_records": sum(record_counts.values()),
            "recent_activity": recent_activity
        }
        
    except Exception as e:
        logger.error(f"Error getting global stats: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# ============================================================================
# INDIVIDUAL DATA ENDPOINTS (Alternative to batch sync)
# ============================================================================

@app.post("/api/v1/data/battery-snapshots")
async def post_battery_snapshots(records: List[BatterySnapshot]):
    """Post battery snapshot records individually."""
    if not records:
        return {"success": True, "stored": 0}
    
    try:
        conn = get_db_connection()
        stored = insert_battery_snapshots(conn, records)
        device_id = records[0].device_id
        log_sync(conn, device_id, "battery_snapshots", stored, "success")
        conn.close()
        return {"success": True, "stored": stored}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/v1/data/charging-sessions")
async def post_charging_sessions(records: List[ChargingSession]):
    """Post charging session records individually."""
    if not records:
        return {"success": True, "stored": 0}
    
    try:
        conn = get_db_connection()
        stored = insert_charging_sessions(conn, records)
        device_id = records[0].device_id
        log_sync(conn, device_id, "charging_sessions", stored, "success")
        conn.close()
        return {"success": True, "stored": stored}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/v1/data/thermal-events")
async def post_thermal_events(records: List[ThermalEvent]):
    """Post thermal event records individually."""
    if not records:
        return {"success": True, "stored": 0}
    
    try:
        conn = get_db_connection()
        stored = insert_thermal_events(conn, records)
        device_id = records[0].device_id
        log_sync(conn, device_id, "thermal_events", stored, "success")
        conn.close()
        return {"success": True, "stored": stored}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/v1/data/drain-events")
async def post_drain_events(records: List[DrainEvent]):
    """Post drain event records individually."""
    if not records:
        return {"success": True, "stored": 0}
    
    try:
        conn = get_db_connection()
        stored = insert_drain_events(conn, records)
        device_id = records[0].device_id
        log_sync(conn, device_id, "drain_events", stored, "success")
        conn.close()
        return {"success": True, "stored": stored}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/v1/data/safety-events")
async def post_safety_events(records: List[SafetyEvent]):
    """Post safety event records individually."""
    if not records:
        return {"success": True, "stored": 0}
    
    try:
        conn = get_db_connection()
        stored = insert_safety_events(conn, records)
        device_id = records[0].device_id
        log_sync(conn, device_id, "safety_events", stored, "success")
        conn.close()
        return {"success": True, "stored": stored}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# ============================================================================
# ERROR HANDLERS
# ============================================================================

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """Global exception handler."""
    logger.error(f"Unhandled exception: {exc}")
    return JSONResponse(
        status_code=500,
        content={"success": False, "error": "Internal server error", "detail": str(exc)}
    )

# ============================================================================
# MAIN ENTRY POINT
# ============================================================================

if __name__ == "__main__":
    print("=" * 60)
    print("HexNil Backend Server")
    print("=" * 60)
    print(f"Host: {Config.HOST}")
    print(f"Port: {Config.PORT}")
    print(f"Database: {Config.MYSQL_HOST}:{Config.MYSQL_PORT}/{Config.MYSQL_DATABASE}")
    print("=" * 60)
    
    uvicorn.run(
        "backend.hexnil_fastapi_server:app",
        host=Config.HOST,
        port=Config.PORT,
        reload=Config.DEBUG,
        log_level="info"
    )
