#!/usr/bin/env python3
"""
HexNil Auto-Sync: Automatically sync data when mobile is connected.
Monitors for device connection and pulls new data from HexNil Room DB.
"""

import os
import sys
import time
import sqlite3
import subprocess
import json
import logging
import requests
from datetime import datetime
from pathlib import Path
from typing import Optional, Dict, Any, List

# Configuration
ADB_PATH = os.path.join(os.environ.get('LOCALAPPDATA', ''), 'Android', 'Sdk', 'platform-tools', 'adb.exe')
PACKAGE_NAME = "com.example.hexnil"
DB_NAME = "hexnil_battery_db"
LOCAL_DB_DIR = Path("sync_data")
LOCAL_DB_PATH = LOCAL_DB_DIR / DB_NAME
SYNC_STATE_FILE = Path("sync_state.json")
BACKEND_URL = "http://127.0.0.1:8000"
CHECK_INTERVAL = 5  # seconds between device checks
DEVICE_ID = None  # Will be populated from device

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.FileHandler('hexnil_auto_sync.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


def run_adb(args: List[str], timeout: int = 30) -> tuple[bool, str]:
    """Run an ADB command and return success status and output."""
    try:
        cmd = [ADB_PATH] + args
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        output = result.stdout + result.stderr
        return result.returncode == 0, output.strip()
    except subprocess.TimeoutExpired:
        return False, "Command timed out"
    except Exception as e:
        return False, str(e)


def is_device_connected() -> bool:
    """Check if an Android device is connected."""
    success, output = run_adb(['devices'])
    if not success:
        return False
    lines = output.strip().split('\n')
    # Skip header line "List of devices attached"
    for line in lines[1:]:
        if '\tdevice' in line:
            return True
    return False


def get_device_id() -> Optional[str]:
    """Get the Android device ID."""
    success, output = run_adb(['shell', 'settings', 'get', 'secure', 'android_id'])
    if success and output:
        return output.strip()
    return None


def get_device_model() -> Optional[str]:
    """Get the device model name."""
    success, output = run_adb(['shell', 'getprop', 'ro.product.model'])
    if success and output:
        return output.strip()
    return None


def pull_database() -> bool:
    """Pull the HexNil database from the device using exec-out for binary transfer."""
    logger.info("Pulling database from device...")
    
    # Ensure sync directory exists
    LOCAL_DB_DIR.mkdir(exist_ok=True)
    
    # Use exec-out with run-as to get binary data properly
    try:
        result = subprocess.run(
            [ADB_PATH, 'exec-out', f'run-as {PACKAGE_NAME} cat databases/{DB_NAME}'],
            capture_output=True
        )
        
        if result.returncode == 0 and len(result.stdout) > 100:  # SQLite header is 100 bytes
            with open(LOCAL_DB_PATH, 'wb') as f:
                f.write(result.stdout)
            logger.info(f"Database pulled successfully: {len(result.stdout)} bytes -> {LOCAL_DB_PATH}")
            return True
        else:
            logger.error(f"Failed to pull database: {result.stderr.decode() if result.stderr else 'Empty response'}")
            return False
    except Exception as e:
        logger.error(f"Error pulling database: {e}")
        return False


def load_sync_state() -> Dict[str, Any]:
    """Load the last sync state."""
    if SYNC_STATE_FILE.exists():
        try:
            with open(SYNC_STATE_FILE, 'r') as f:
                return json.load(f)
        except:
            pass
    return {
        'last_sync': None,
        'last_snapshot_id': 0,
        'last_charging_id': 0,
        'last_thermal_id': 0,
        'last_drain_id': 0,
        'last_health_id': 0,
        'last_safety_id': 0,
        'last_app_battery_usage_id': 0,
        'last_app_drain_ranks_id': 0,
        'last_refresh_rate_sessions_id': 0,
        'last_refresh_rate_anomalies_id': 0,
        'last_anomaly_records_id': 0,
        'last_thermal_heat_buckets_id': 0,
        'last_device_info_id': 0,
        'last_ai_analysis_results_id': 0,
        'last_cpu_gpu_load_samples_id': 0,
        'last_memory_snapshots_id': 0,
        'last_app_launch_metrics_id': 0,
        'last_frame_metrics_id': 0,
        'last_crash_events_id': 0,
        'last_anr_events_id': 0,
        'last_daily_aggregates_id': 0,
        'last_regression_events_id': 0,
        'last_network_cellular_snapshots_id': 0,
        'last_network_wifi_snapshots_id': 0,
        'last_network_bluetooth_snapshots_id': 0,
        'last_modem_firmware_events_id': 0
    }


def save_sync_state(state: Dict[str, Any]):
    """Save the sync state."""
    state['last_sync'] = datetime.now().isoformat()
    with open(SYNC_STATE_FILE, 'w') as f:
        json.dump(state, f, indent=2)
    logger.info("Sync state saved")


def read_new_data(conn: sqlite3.Connection, table: str, last_id: int) -> List[Dict]:
    """Read new records from a table since last sync."""
    cursor = conn.cursor()
    try:
        cursor.execute(f"SELECT * FROM {table} WHERE id > ? ORDER BY id", (last_id,))
        columns = [desc[0] for desc in cursor.description]
        rows = cursor.fetchall()
        return [dict(zip(columns, row)) for row in rows]
    except sqlite3.Error as e:
        logger.warning(f"Error reading {table}: {e}")
        return []


def transform_snapshot(row: Dict, device_id: str) -> Dict:
    """Transform battery snapshot for API. Preserves NULL for three-state (0/1/NULL)."""
    status = row.get('status', 0)
    is_charging = status == 2 or bool(row.get('isCharging', 0))
    plugged = row.get('plugged', 0)
    charge_type = row.get('chargingType', row.get('chargeType'))
    if not charge_type:
        charge_type = 'ac' if plugged == 1 else ('usb' if plugged == 2 else ('wireless' if plugged == 4 else None))

    raw_current = row.get('currentNow', row.get('current_now', row.get('current')))
    current_now = None if raw_current is None else (float(raw_current) if isinstance(raw_current, (int, float)) else None)

    raw_screen = row.get('screenOn', row.get('screen_on'))
    if raw_screen is None:
        screen_on = None  # UNAVAILABLE
    else:
        screen_on = bool(int(raw_screen)) if raw_screen != '' else None

    raw_trigger = row.get('snapshotTrigger', row.get('snapshot_trigger'))
    raw_source = row.get('dataSource', row.get('data_source'))
    raw_conf = row.get('confidence')
    try:
        confidence = float(raw_conf) if raw_conf is not None else None
    except (TypeError, ValueError):
        confidence = None
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp'),
        'battery_level': row.get('level', 0),
        'temperature': (row.get('temperature', 0) or 0) / 10.0 if row.get('temperature') is not None else None,
        'voltage': (row.get('voltage', 0) or 0) / 1000.0 if row.get('voltage') is not None else None,
        'current_now': current_now,
        'is_charging': is_charging,
        'charge_type': charge_type,
        'screen_on': screen_on,
        'power_save_mode': bool(row.get('powerSaveMode', row.get('power_save_mode', False)) or False),
        'snapshot_trigger': raw_trigger if raw_trigger else None,
        'data_source': raw_source if raw_source else None,
        'confidence': confidence,
    }


def transform_charging(row: Dict, device_id: str) -> Dict:
    """Transform charging session for API - matches ChargingSession Pydantic model."""
    start_time = row.get('startTime', row.get('start_time', 0))
    end_time = row.get('endTime', row.get('end_time', 0))
    is_complete = end_time and end_time > 0
    
    return {
        'device_id': device_id,
        'timestamp': start_time,
        'session_id': row.get('sessionId', row.get('session_id', f"session_{row.get('id')}")),
        'start_level': row.get('startLevel', row.get('start_level', 0)),
        'end_level': row.get('endLevel', row.get('end_level')) if is_complete else None,
        'start_time': start_time,
        'end_time': end_time if is_complete else None,
        'charge_type': row.get('chargeType', row.get('charge_type', 'ac')),
        'max_temperature': row.get('maxTemperature', row.get('max_temperature')),
        'avg_temperature': row.get('avgTemperature', row.get('avg_temperature')),
        'energy_added_mah': None,
        'charging_speed_ma': row.get('avgCurrent', row.get('avg_current')),
        'is_complete': is_complete
    }


def transform_thermal(row: Dict, device_id: str) -> Dict:
    """Transform thermal event for API - matches ThermalEvent Pydantic model."""
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp'),
        'temperature': row.get('temperature', 0) / 10.0 if row.get('temperature') else 0,  # Convert from tenths
        'threshold_exceeded': None,
        'duration_seconds': row.get('durationSeconds', row.get('duration_seconds', 0)),
        'trigger_source': row.get('eventType', row.get('event_type', 'threshold')),
        'cpu_usage': row.get('cpuUsage', row.get('cpu_usage')),
        'foreground_app': row.get('foregroundApp', row.get('foreground_app')),
        'severity': row.get('severity', 'warning')
    }


def transform_drain(row: Dict, device_id: str) -> Dict:
    """Transform drain event for API - matches DrainEvent Pydantic model."""
    start_time = row.get('startTime', row.get('start_time', 0))
    end_time = row.get('endTime', row.get('end_time', 0))
    
    # Skip incomplete drain events (end_time is 0)
    if not end_time or end_time == 0:
        return None  # Will be filtered out
    
    # Calculate duration in minutes
    duration = int((end_time - start_time) / 60000) if end_time > start_time else 1
    
    return {
        'device_id': device_id,
        'timestamp': start_time,
        'start_level': row.get('startLevel', row.get('start_level', 0)),
        'end_level': row.get('endLevel', row.get('end_level', 0)),
        'duration_minutes': max(duration, 1),  # At least 1 minute
        'drain_rate_per_hour': row.get('drainRatePerHour', row.get('drain_rate_per_hour', 0.0)),
        'screen_on_time_minutes': row.get('screenOnTime', row.get('screen_on_time', 0)),
        'suspected_cause': row.get('drainType', row.get('drain_type', 'normal')),
        'top_apps': None
    }


def transform_health(row: Dict, device_id: str) -> Dict:
    """Transform battery health for API - matches BatteryHealth Pydantic model."""
    # Helper to safely convert to int
    def safe_int(val):
        if val is None:
            return None
        return int(val) if isinstance(val, (int, float)) else None
    
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp'),
        'health_percentage': row.get('healthPercentage', row.get('health_percentage')),
        'cycle_count': safe_int(row.get('cycleCount', row.get('cycle_count'))),
        'design_capacity_mah': safe_int(row.get('designCapacityMah', row.get('design_capacity_mah'))),
        'current_capacity_mah': safe_int(row.get('capacityMah', row.get('capacity_mah'))),
        'charge_full_design': None,
        'charge_full': None,
        'technology': None,
        'manufacture_date': None,
        'first_use_date': None
    }


def transform_safety(row: Dict, device_id: str) -> Dict:
    """Transform safety event for API - matches SafetyEvent Pydantic model."""
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp'),
        'event_type': row.get('eventType', row.get('event_type', 'warning')),
        'severity': row.get('severity', 'low'),
        'message': row.get('description', row.get('message', '')),
        'battery_level': row.get('level', row.get('batteryLevel', row.get('battery_level'))),
        'temperature': row.get('temperature', 0) / 10.0 if row.get('temperature') else None,
        'resolved': row.get('resolved', False),
        'resolved_at': row.get('resolvedAt', row.get('resolved_at'))
    }


def transform_app_battery_usage(row: Dict, device_id: str) -> Dict:
    """Transform app battery usage for API."""
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp', 0),
        'period_start': row.get('periodStart', row.get('period_start', 0)),
        'period_end': row.get('periodEnd', row.get('period_end', 0)),
        'package_name': row.get('packageName', row.get('package_name')),
        'app_name': row.get('appName', row.get('app_name')),
        'is_system_app': bool(row.get('isSystemApp', row.get('is_system_app', 0))),
        'is_preinstalled': bool(row.get('isPreInstalled', row.get('is_preinstalled', 0))),
        'app_category': row.get('appCategory', row.get('app_category')),
        'foreground_time': int(row.get('foregroundTime', row.get('foreground_time', 0))),
        'background_time': int(row.get('backgroundTime', row.get('background_time', 0))),
        'battery_percent_used': float(row.get('batteryPercentUsed', row.get('battery_percent_used', 0))),
        'drain_per_hour': float(row.get('drainPerHour', row.get('drain_per_hour', 0))),
        'cpu_time_ms': float(row.get('cpuTimeMs', row.get('cpu_time_ms', 0))),
        'network_bytes': int(row.get('networkBytes', row.get('network_bytes', 0))),
        'wakelock_count': int(row.get('wakelockCount', row.get('wakelock_count', 0))),
        'wakelock_time': int(row.get('wakelockTime', row.get('wakelock_time', 0))),
        'partial_wakelock_time': int(row.get('partialWakelockTime', row.get('partial_wakelock_time', 0))),
        'alarm_wake_count': int(row.get('alarmWakeCount', row.get('alarm_wake_count', 0))),
        'network_wake_count': int(row.get('networkWakeCount', row.get('network_wake_count', 0))),
        'wakelock_drain_score': float(row.get('wakelockDrainScore', row.get('wakelock_drain_score', 0))),
        'alarm_count': int(row.get('alarmCount', row.get('alarm_count', 0))),
        'gps_requests': int(row.get('gpsRequests', row.get('gps_requests', 0))),
        'background_wakes': int(row.get('backgroundWakes', row.get('background_wakes', 0))),
        'push_notifications': int(row.get('pushNotifications', row.get('push_notifications', 0))),
        'background_services': int(row.get('backgroundServices', row.get('background_services', 0))),
        'has_excessive_background': bool(row.get('hasExcessiveBackground', row.get('has_excessive_background', 0))),
        'category_average': float(row.get('categoryAverage', row.get('category_average', 0))),
        'deviation_from_average': float(row.get('deviationFromAverage', row.get('deviation_from_average', 0))),
        'efficiency_rating': row.get('efficiencyRating', row.get('efficiency_rating')),
        'drain_reasons': row.get('drainReasons', row.get('drain_reasons')),
        'optimization_tips': row.get('optimizationTips', row.get('optimization_tips')),
        'needs_restriction': bool(row.get('needsRestriction', row.get('needs_restriction', 0)))
    }


def transform_app_drain_ranks(row: Dict, device_id: str) -> Dict:
    """Transform app drain ranks for API."""
    return {
        'device_id': device_id,
        'window_label': row.get('windowLabel', row.get('window_label')),
        'window_start': row.get('windowStart', row.get('window_start', 0)),
        'window_end': row.get('windowEnd', row.get('window_end', 0)),
        'rank_position': int(row.get('rank', row.get('rank_position', 0))),
        'package_name': row.get('packageName', row.get('package_name')),
        'drain_score': float(row.get('drainScore', row.get('drain_score', 0))),
        'contribution_pct': float(row.get('contributionPct', row.get('contribution_pct', 0))),
        'screen_time_ms': int(row.get('screenTimeMs', row.get('screen_time_ms', 0))),
        'foreground_time_ms': int(row.get('foregroundTimeMs', row.get('foreground_time_ms', 0))),
        'background_time_ms': int(row.get('backgroundTimeMs', row.get('background_time_ms', 0)))
    }


def transform_refresh_rate_session(row: Dict, device_id: str) -> Dict:
    """Transform refresh rate session for API."""
    return {
        'device_id': device_id,
        'session_id': row.get('sessionId', row.get('session_id')),
        'session_type': row.get('sessionType', row.get('session_type')),
        'package_name': row.get('packageName', row.get('package_name')),
        'app_name': row.get('appName', row.get('app_name')),
        'start_time': row.get('startTime', row.get('start_time', 0)),
        'end_time': row.get('endTime', row.get('end_time', 0)),
        'duration_ms': row.get('durationMs', row.get('duration_ms', 0)),
        'start_refresh_rate_hz': float(row.get('startRefreshRateHz', row.get('start_refresh_rate_hz', 0))),
        'end_refresh_rate_hz': float(row.get('endRefreshRateHz', row.get('end_refresh_rate_hz', 0))),
        'avg_refresh_rate_hz': float(row.get('avgRefreshRateHz', row.get('avg_refresh_rate_hz', 0))),
        'min_refresh_rate_hz': float(row.get('minRefreshRateHz', row.get('min_refresh_rate_hz', 0))),
        'max_refresh_rate_hz': float(row.get('maxRefreshRateHz', row.get('max_refresh_rate_hz', 0))),
        'time_at_high_hz_ms': int(row.get('timeAtHighHzMs', row.get('time_at_high_hz_ms', 0))),
        'time_at_low_hz_ms': int(row.get('timeAtLowHzMs', row.get('time_at_low_hz_ms', 0))),
        'is_adaptive_display': bool(row.get('isAdaptiveDisplay', row.get('is_adaptive_display', 0))),
        'avg_brightness': float(row.get('avgBrightness', row.get('avg_brightness', 0))),
        'avg_cpu_usage': float(row.get('avgCpuUsage', row.get('avg_cpu_usage', 0))),
        'avg_gpu_usage': float(row.get('avgGpuUsage', row.get('avg_gpu_usage', 0))),
        'avg_battery_current': float(row.get('avgBatteryCurrent', row.get('avg_battery_current', 0))),
        'avg_temperature': float(row.get('avgTemperature', row.get('avg_temperature', 0))),
        'estimated_battery_cost': float(row.get('estimatedBatteryCost', row.get('estimated_battery_cost', 0))),
        'anomaly_count': int(row.get('anomalyCount', row.get('anomaly_count', 0))),
        'has_strong_anomaly': bool(row.get('hasStrongAnomaly', row.get('has_strong_anomaly', 0)))
    }


def transform_refresh_rate_anomaly(row: Dict, device_id: str) -> Dict:
    """Transform refresh rate anomaly for API."""
    return {
        'device_id': device_id,
        'linked_session_id': int(row.get('linkedSessionId', row.get('linked_session_id', 0))),
        'package_name': row.get('packageName', row.get('package_name')),
        'app_name': row.get('appName', row.get('app_name')),
        'session_type': row.get('sessionType', row.get('session_type')),
        'detected_at': row.get('detectedAt', row.get('detected_at', 0)),
        'anomaly_type': row.get('anomalyType', row.get('anomaly_type')),
        'severity': row.get('severity'),
        'confidence_score': float(row.get('confidenceScore', row.get('confidence_score', 0))),
        'cause_category': row.get('causeCategory', row.get('cause_category')),
        'cause_explanation': row.get('causeExplanation', row.get('cause_explanation')),
        'evidence_metrics_json': row.get('evidenceMetricsJson', row.get('evidence_metrics_json')),
        'refresh_rate_hz': float(row.get('refreshRateHz', row.get('refresh_rate_hz', 0))),
        'cpu_usage': float(row.get('cpuUsage', row.get('cpu_usage', 0))),
        'gpu_usage': float(row.get('gpuUsage', row.get('gpu_usage', 0))),
        'temperature': float(row.get('temperature', 0)),
        'brightness': float(row.get('brightness', 0)),
        'battery_current': float(row.get('batteryCurrent', row.get('battery_current', 0))),
        'was_charging': bool(row.get('wasCharging', row.get('was_charging', 0))),
        'screen_on': bool(row.get('screenOn', row.get('screen_on', 1)))
    }


def transform_anomaly_record(row: Dict, device_id: str) -> Dict:
    """Transform anomaly record for API."""
    return {
        'device_id': device_id,
        'detected_at': row.get('detectedAt', row.get('detected_at', 0)),
        'anomaly_type': row.get('anomalyType', row.get('anomaly_type')),
        'severity': row.get('severity'),
        'confidence_score': float(row.get('confidenceScore', row.get('confidence_score', 0)) or 0),
        'source_app': row.get('sourceApp', row.get('source_app')),
        'source_app_name': row.get('sourceAppName', row.get('source_app_name')),
        'source_component': row.get('sourceComponent', row.get('source_component')),
        'title': row.get('title'),
        'description': row.get('description'),
        'technical_details': row.get('technicalDetails', row.get('technical_details')),
        'expected_value': float(row.get('expectedValue', row.get('expected_value', 0)) or 0),
        'actual_value': float(row.get('actualValue', row.get('actual_value', 0)) or 0),
        'deviation_percent': float(row.get('deviationPercent', row.get('deviation_percent', 0)) or 0),
        'metric_name': row.get('metricName', row.get('metric_name')),
        'metric_unit': row.get('metricUnit', row.get('metric_unit')),
        'battery_level': row.get('batteryLevel', row.get('battery_level')),
        'was_charging': bool(row.get('wasCharging', row.get('was_charging', 0))),
        'screen_on': bool(row.get('screenOn', row.get('screen_on', 0))),
        'device_state': row.get('deviceState', row.get('device_state')),
        'period_start': row.get('periodStart', row.get('period_start')),
        'period_end': row.get('periodEnd', row.get('period_end')),
        'data_points_analyzed': int(row.get('dataPointsAnalyzed', row.get('data_points_analyzed', 0)) or 0),
        'recommendation': row.get('recommendation'),
        'root_cause': row.get('rootCause', row.get('root_cause')),
        'impact': row.get('impact'),
        'acknowledged': bool(row.get('acknowledged', 0)),
        'resolved': bool(row.get('resolved', 0)),
        'resolved_at': row.get('resolvedAt', row.get('resolved_at')),
        'resolution': row.get('resolution'),
        'related_anomaly_id': row.get('relatedAnomalyId', row.get('related_anomaly_id')),
        'occurrence_count': int(row.get('occurrenceCount', row.get('occurrence_count', 1)) or 1),
        'is_recurring': bool(row.get('isRecurring', row.get('is_recurring', 0)))
    }


def transform_thermal_heat_bucket(row: Dict, device_id: str) -> Dict:
    """Transform thermal heat bucket for API."""
    return {
        'device_id': device_id,
        'start_time': row.get('startTime', row.get('start_time', 0)),
        'end_time': row.get('endTime', row.get('end_time', 0)),
        'temp_bucket': row.get('tempBucket', row.get('temp_bucket')),
        'hour_block': int(row.get('hourBlock', row.get('hour_block', 0)) or 0),
        'sample_count': int(row.get('sampleCount', row.get('sample_count', 0)) or 0),
        'duration_ms': int(row.get('durationMs', row.get('duration_ms', 0)) or 0),
        'max_temp': float(row.get('maxTemp', row.get('max_temp', 0)) or 0),
        'avg_temp': float(row.get('avgTemp', row.get('avg_temp', 0)) or 0),
        'min_temp': float(row.get('minTemp', row.get('min_temp', 0)) or 0),
        'thermal_score': float(row.get('thermalScore', row.get('thermal_score', 0)) or 0)
    }


def transform_calibration_data(row: Dict, device_id: str) -> Dict:
    """Transform calibration data for API."""
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp', 0),
        'calibration_type': row.get('calibrationType', row.get('calibration_type')),
        'old_value': float(row.get('oldValue', row.get('old_value', 0)) or 0),
        'new_value': float(row.get('newValue', row.get('new_value', 0)) or 0),
        'accuracy_score': float(row.get('accuracyScore', row.get('accuracy_score', 0)) or 0),
        'reference_source': row.get('referenceSource', row.get('reference_source')),
        'is_automatic': bool(row.get('isAutomatic', row.get('is_automatic', 1)))
    }


def transform_device_info(row: Dict, device_id: str) -> Dict:
    """Transform device info for API."""
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp', row.get('lastUpdated', 0)),
        'first_recorded': row.get('firstRecorded', row.get('first_recorded')),
        'manufacturer': row.get('manufacturer'),
        'model': row.get('model'),
        'product': row.get('product'),
        'brand': row.get('brand'),
        'device': row.get('device'),
        'android_version': row.get('androidVersion', row.get('android_version')),
        'sdk_version': row.get('sdkVersion', row.get('sdk_version')),
        'build_number': row.get('buildNumber', row.get('build_number')),
        'battery_technology': row.get('batteryTechnology', row.get('battery_technology')),
        'design_capacity_mah': row.get('designCapacityMah', row.get('design_capacity_mah')),
        'hexnil_version': row.get('hexnilVersion', row.get('hexnil_version')),
        'total_snapshots': int(row.get('totalSnapshots', row.get('total_snapshots', 0)) or 0),
        'total_analyses': int(row.get('totalAnalyses', row.get('total_analyses', 0)) or 0)
    }


def transform_ai_analysis_result(row: Dict, device_id: str) -> Dict:
    """Transform AI analysis result for API."""
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp', 0),
        'analysis_type': row.get('analysisType', row.get('analysis_type', 'unknown')),
        'confidence_score': float(row.get('confidenceScore', row.get('confidence_score', 0)) or 0),
        'prediction': row.get('prediction'),
        'features_used': row.get('featuresUsed', row.get('features_used')),
        'model_version': row.get('modelVersion', row.get('model_version')),
        'execution_time_ms': row.get('executionTimeMs', row.get('execution_time_ms'))
    }


def transform_cpu_gpu_load_sample(row: Dict, device_id: str) -> Dict:
    """Transform CPU/GPU load sample for API."""
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp', 0),
        'app_name': row.get('appName', row.get('app_name')),
        'package_name': row.get('packageName', row.get('package_name')),
        'pid': int(row.get('pid', 0) or 0),
        'uid': int(row.get('uid', 0) or 0),
        'is_system_app': bool(row.get('isSystemApp', row.get('is_system_app', 0))),
        'system_cpu_percent': int(row.get('systemCpuPercent', row.get('system_cpu_percent', -1)) or -1),
        'app_cpu_percent': int(row.get('appCpuPercent', row.get('app_cpu_percent', -1)) or -1),
        'gpu_percent': int(row.get('gpuPercent', row.get('gpu_percent', -1)) or -1),
        'is_gpu_available': bool(row.get('isGpuAvailable', row.get('is_gpu_available', 1))),
        'load_1m': float(row.get('load1m', row.get('load_1m', 0)) or 0),
        'load_5m': float(row.get('load5m', row.get('load_5m', 0)) or 0),
        'load_15m': float(row.get('load15m', row.get('load_15m', 0)) or 0),
        'runnable_processes': int(row.get('runnableProcesses', row.get('runnable_processes', 0)) or 0),
        'temperature': float(row.get('temperature', 0) or 0),
        'battery_level': int(row.get('batteryLevel', row.get('battery_level', 0)) or 0),
        'drain_rate_per_hour': float(row.get('drainRatePerHour', row.get('drain_rate_per_hour', 0)) or 0),
        'is_throttled': bool(row.get('isThrottled', row.get('is_throttled', 0))),
        'throttle_level': int(row.get('throttleLevel', row.get('throttle_level', 0)) or 0),
        'thermal_status': int(row.get('thermalStatus', row.get('thermal_status', 0)) or 0),
        'stress_score': float(row.get('stressScore', row.get('stress_score', 0)) or 0),
        'stress_category': row.get('stressCategory', row.get('stress_category', 'LOW')),
        'stress_explanation': row.get('stressExplanation', row.get('stress_explanation')),
        'inferred_load': row.get('inferredLoad', row.get('inferred_load', 'LOW')),
        'confidence_level': row.get('confidenceLevel', row.get('confidence_level', 'LOW')),
        'detection_method': row.get('detectionMethod', row.get('detection_method', 'Direct hardware counters')),
        'observed_signals': row.get('observedSignals', row.get('observed_signals')),
        'source_type': row.get('sourceType', row.get('source_type', 'system')),
        'validation_status': row.get('validationStatus', row.get('validation_status', 'unchecked')),
        'validation_notes': row.get('validationNotes', row.get('validation_notes')),
        'is_active': bool(row.get('isActive', row.get('is_active', 1)))
    }


def transform_memory_snapshot(row: Dict, device_id: str) -> Dict:
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp'),
        'total_memory_bytes': row.get('totalMemoryBytes', 0),
        'available_memory_bytes': row.get('availableMemoryBytes', 0),
        'used_memory_bytes': row.get('usedMemoryBytes', 0),
        'used_memory_percent': row.get('usedMemoryPercent', 0),
        'is_low_memory': bool(row.get('isLowMemory', 0)),
        'threshold': row.get('threshold', 0),
        'running_app_count': row.get('runningAppCount', 0),
        'top_memory_app': row.get('topMemoryApp'),
        'top_memory_app_bytes': row.get('topMemoryAppBytes', 0),
        'battery_level': row.get('batteryLevel', 0),
        'temperature': row.get('temperature', 0),
        'is_charging': bool(row.get('isCharging', 0)),
        'is_screen_on': bool(row.get('isScreenOn', 0))
    }


def transform_app_launch(row: Dict, device_id: str) -> Dict:
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp'),
        'package_name': row.get('packageName'),
        'app_label': row.get('appLabel'),
        'launch_type': row.get('launchType'),
        'launch_duration_ms': row.get('launchDurationMs', 0),
        'ttid_ms': row.get('ttidMs', 0),
        'ttfd_ms': row.get('ttfdMs', 0),
        'before_level': row.get('beforeLevel', 0),
        'after_level': row.get('afterLevel', 0),
        'before_temp': row.get('beforeTemp', 0),
        'after_temp': row.get('afterTemp', 0),
        'is_first_launch_today': bool(row.get('isFirstLaunchToday', 0)),
        'session_context': row.get('sessionContext'),
        'app_version_code': row.get('appVersionCode', 0),
        'app_version_name': row.get('appVersionName'),
        'memory_usage_percent': row.get('memoryUsagePercent', 0),
        'is_charging': bool(row.get('isCharging', 0)),
        'thermal_headroom': row.get('thermalHeadroom', 0)
    }


def transform_frame_metric(row: Dict, device_id: str) -> Dict:
    return {
        'device_id': device_id,
        'timestamp': row.get('sessionStart'),
        'session_start': row.get('sessionStart'),
        'session_end': row.get('sessionEnd'),
        'package_name': row.get('packageName'),
        'total_frames': row.get('totalFrames', 0),
        'slow_frames': row.get('slowFrames', 0),
        'frozen_frames': row.get('frozenFrames', 0),
        'jank_percentage': row.get('jankPercentage', 0),
        'avg_frame_duration_ms': row.get('avgFrameDurationMs', 0),
        'max_frame_duration_ms': row.get('maxFrameDurationMs', 0),
        'dropped_frame_count': row.get('droppedFrameCount', 0),
        'fps': row.get('fps', 0),
        'session_duration_ms': row.get('sessionDurationMs', 0),
        'activity_name': row.get('activityName'),
        'temperature': row.get('temperature', 0),
        'memory_usage_percent': row.get('memoryUsagePercent', 0),
        'was_thermal_throttling': bool(row.get('wasThermalThrottling', 0))
    }


def transform_crash_event(row: Dict, device_id: str) -> Dict:
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp'),
        'package_name': row.get('packageName'),
        'app_label': row.get('appLabel'),
        'crash_type': row.get('crashType'),
        'exception_type': row.get('exceptionType'),
        'exception_message': row.get('exceptionMessage'),
        'stack_trace_hash': row.get('stackTraceHash'),
        'process_name': row.get('processName'),
        'is_foreground': bool(row.get('importance', 0) <= 100),
        'app_version_name': row.get('appVersionName'),
        'payload': None
    }


def transform_anr_event(row: Dict, device_id: str) -> Dict:
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp'),
        'package_name': row.get('packageName'),
        'app_label': row.get('appLabel'),
        'anr_type': row.get('anrType'),
        'duration_ms': row.get('durationMs', 0),
        'trace_hash': row.get('traceHash'),
        'is_foreground': True,  # Usually foreground for ANR
        'app_version_name': row.get('appVersionName')
    }


def transform_daily_aggregate(row: Dict, device_id: str) -> Dict:
    return {
        'device_id': device_id,
        'aggregate_date': row.get('date'),
        'package_name': row.get('packageName'),
        'app_label': row.get('appLabel'),
        'avg_memory_used_percent': row.get('avgMemoryUsedPercent', 0),
        'max_memory_used_percent': row.get('maxMemoryUsedPercent', 0),
        'low_memory_event_count': row.get('lowMemoryEventCount', 0),
        'launch_count': row.get('launchCount', 0),
        'cold_launch_count': row.get('coldLaunchCount', 0),
        'avg_cold_launch_ms': row.get('avgColdLaunchMs', 0),
        'slow_launch_count': row.get('slowLaunchCount', 0),
        'crash_count': row.get('crashCount', 0),
        'anr_count': row.get('anrCount', 0),
        'unique_crash_types': row.get('uniqueCrashTypes', 0),
        'avg_temp': row.get('avgTemp', 0),
        'max_temp': row.get('maxTemp', 0),
        'total_battery_drain': row.get('totalBatteryDrain', 0),
        'avg_drain_rate_per_hour': row.get('avgDrainRatePerHour', 0),
        'health_score': row.get('healthScore', 0),
        'health_grade': row.get('healthGrade'),
        'app_version': row.get('appVersion')
    }


def transform_regression_event(row: Dict, device_id: str) -> Dict:
    return {
        'device_id': device_id,
        'timestamp': row.get('detectedAt'),
        'package_name': row.get('packageName'),
        'regression_type': row.get('regressionType'),
        'severity': row.get('severity'),
        'metric_name': row.get('metricName'),
        'current_value': row.get('currentValue', 0),
        'baseline_value': row.get('baselineValue', 0),
        'deviation_percent': row.get('deltaPercent', 0),
        'is_resolved': bool(row.get('isResolved', 0))
    }


def transform_network_cellular_snapshot(row: Dict, device_id: str) -> Dict:
    """Transform network cellular snapshot for API."""
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp', 0),
        'sequence_number': row.get('sequenceNumber', 0),
        'elapsed_realtime_ms': row.get('elapsedRealtimeMs', 0),
        'session_id': row.get('sessionId', 0),
        'radio_technology': row.get('radioTechnology'),
        'network_type': row.get('networkType'),
        'signal_strength_dbm': row.get('signalStrengthDbm'),
        'rsrp': row.get('rsrp'),
        'rsrq': row.get('rsrq'),
        'sinr': row.get('sinr'),
        'rssi': row.get('rssi'),
        'signal_level': row.get('signalLevel'),
        'asu': row.get('asu'),
        'band': row.get('band'),
        'arfcn': row.get('arfcn'),
        'pci': row.get('pci'),
        'cell_id': row.get('cellId'),
        'tac': row.get('tac'),
        'carrier_id': row.get('carrierId'),
        'mcc': row.get('mcc'),
        'mnc': row.get('mnc'),
        'network_operator_name': row.get('networkOperatorName'),
        'plmn': row.get('plmn'),
        'data_registration_state': row.get('dataRegistrationState'),
        'voice_registration_state': row.get('voiceRegistrationState'),
        'is_roaming': bool(row.get('isRoaming', 0)) if row.get('isRoaming') is not None else None,
        'roaming_type': row.get('roamingType'),
        'emergency_only': bool(row.get('emergencyOnly', 0)) if row.get('emergencyOnly') is not None else None,
        'data_state': row.get('dataState'),
        'data_activity': row.get('dataActivity'),
        'modem_firmware_version': row.get('modemFirmwareVersion'),
        'baseband_version': row.get('basebandVersion'),
        'snapshot_context': row.get('snapshotContext'),
        'snapshot_trigger': row.get('snapshotTrigger'),
        'data_source': row.get('dataSource'),
        'confidence': row.get('confidence'),
        'validation_status': row.get('validationStatus', 'unchecked'),
        'quality_score': row.get('qualityScore', -1),
        'validation_flags': row.get('validationFlags', 0),
        'validation_notes': row.get('validationNotes'),
        'signal_data_changed': bool(row.get('signalDataChanged', 1))
    }


def transform_network_wifi_snapshot(row: Dict, device_id: str) -> Dict:
    """Transform network Wi-Fi snapshot for API."""
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp', 0),
        'sequence_number': row.get('sequenceNumber', 0),
        'elapsed_realtime_ms': row.get('elapsedRealtimeMs', 0),
        'session_id': row.get('sessionId', 0),
        'ssid_hash': row.get('ssidHash'),
        'bssid_hash': row.get('bssidHash'),
        'frequency': row.get('frequency'),
        'frequency_band': row.get('frequencyBand'),
        'channel': row.get('channel'),
        'link_speed_mbps': row.get('linkSpeedMbps'),
        'tx_link_speed_mbps': row.get('txLinkSpeedMbps'),
        'rx_link_speed_mbps': row.get('rxLinkSpeedMbps'),
        'max_tx_link_speed_mbps': row.get('maxTxLinkSpeedMbps'),
        'max_rx_link_speed_mbps': row.get('maxRxLinkSpeedMbps'),
        'rssi': row.get('rssi'),
        'signal_level': row.get('signalLevel'),
        'wifi_standard': row.get('wifiStandard'),
        'wifi_standard_name': row.get('wifiStandardName'),
        'security_type': row.get('securityType'),
        'connection_state': row.get('connectionState'),
        'network_id': row.get('networkId'),
        'is_metered': bool(row.get('isMetered', 0)) if row.get('isMetered') is not None else None,
        'is_passpoint': bool(row.get('isPasspoint', 0)) if row.get('isPasspoint') is not None else None,
        'disconnect_reason': row.get('disconnectReason'),
        'reconnect_count': row.get('reconnectCount'),
        'dhcp_latency_ms': row.get('dhcpLatencyMs'),
        'time_since_auth_ms': row.get('timeSinceAuthMs'),
        'association_attempts': row.get('associationAttempts'),
        'association_failures': row.get('associationFailures'),
        'has_ipv4': bool(row.get('hasIpv4', 0)) if row.get('hasIpv4') is not None else None,
        'has_ipv6': bool(row.get('hasIpv6', 0)) if row.get('hasIpv6') is not None else None,
        'gateway_latency_ms': row.get('gatewayLatencyMs'),
        'snapshot_context': row.get('snapshotContext'),
        'snapshot_trigger': row.get('snapshotTrigger'),
        'data_source': row.get('dataSource'),
        'confidence': row.get('confidence'),
        'validation_status': row.get('validationStatus', 'unchecked'),
        'quality_score': row.get('qualityScore', -1),
        'validation_flags': row.get('validationFlags', 0),
        'validation_notes': row.get('validationNotes'),
        'signal_data_changed': bool(row.get('signalDataChanged', 1))
    }


def transform_network_bluetooth_snapshot(row: Dict, device_id: str) -> Dict:
    """Transform network Bluetooth snapshot for API."""
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp', 0),
        'sequence_number': row.get('sequenceNumber', 0),
        'elapsed_realtime_ms': row.get('elapsedRealtimeMs', 0),
        'session_id': row.get('sessionId', 0),
        'adapter_state': row.get('adapterState'),
        'adapter_state_name': row.get('adapterStateName'),
        'scan_mode': row.get('scanMode'),
        'is_enabled': bool(row.get('isEnabled', 0)) if row.get('isEnabled') is not None else None,
        'is_discovering': bool(row.get('isDiscovering', 0)) if row.get('isDiscovering') is not None else None,
        'paired_device_count': row.get('pairedDeviceCount'),
        'connected_device_count': row.get('connectedDeviceCount'),
        'profile_type': row.get('profileType'),
        'profile_type_constant': row.get('profileTypeConstant'),
        'connection_state': row.get('connectionState'),
        'connection_state_name': row.get('connectionStateName'),
        'codec_type': row.get('codecType'),
        'codec_type_constant': row.get('codecTypeConstant'),
        'sample_rate': row.get('sampleRate'),
        'bits_per_sample': row.get('bitsPerSample'),
        'channel_mode': row.get('channelMode'),
        'codec_quality': row.get('codecQuality'),
        'is_playing': bool(row.get('isPlaying', 0)) if row.get('isPlaying') is not None else None,
        'le_connection_interval': row.get('leConnectionInterval'),
        'le_latency': row.get('leLatency'),
        'rssi': row.get('rssi'),
        'tx_power_level': row.get('txPowerLevel'),
        'packet_error_count': row.get('packetErrorCount'),
        'reconnect_count': row.get('reconnectCount'),
        'connection_duration_ms': row.get('connectionDurationMs'),
        'remote_address_hash': row.get('remoteAddressHash'),
        'remote_device_type': row.get('remoteDeviceType'),
        'remote_device_class': row.get('remoteDeviceClass'),
        'remote_device_name': row.get('remoteDeviceName'),
        'snapshot_context': row.get('snapshotContext'),
        'snapshot_trigger': row.get('snapshotTrigger'),
        'data_source': row.get('dataSource'),
        'confidence': row.get('confidence'),
        'validation_status': row.get('validationStatus', 'unchecked'),
        'quality_score': row.get('qualityScore', -1),
        'validation_flags': row.get('validationFlags', 0),
        'validation_notes': row.get('validationNotes')
    }


def transform_modem_firmware_event(row: Dict, device_id: str) -> Dict:
    """Transform modem firmware event for API."""
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp', 0),
        'elapsed_realtime_ms': row.get('elapsedRealtimeMs', 0),
        'previous_version': row.get('previousVersion'),
        'current_version': row.get('currentVersion'),
        'previous_baseband_version': row.get('previousBasebandVersion'),
        'current_baseband_version': row.get('currentBasebandVersion'),
        'event_type': row.get('eventType'),
        'detection_source': row.get('detectionSource'),
        'android_version': row.get('androidVersion'),
        'android_sdk_level': row.get('androidSdkLevel'),
        'security_patch': row.get('securityPatch'),
        'build_fingerprint': row.get('buildFingerprint'),
        'build_number': row.get('buildNumber'),
        'manufacturer': row.get('manufacturer'),
        'model': row.get('model'),
        'product': row.get('product'),
        'bootloader_version': row.get('bootloaderVersion'),
        'hardware': row.get('hardware'),
        'board': row.get('board'),
        'is_version_change': bool(row.get('isVersionChange', 0)) if row.get('isVersionChange') is not None else None,
        'is_ota_update': bool(row.get('isOtaUpdate', 0)) if row.get('isOtaUpdate') is not None else None,
        'time_since_last_event_ms': row.get('timeSinceLastEventMs'),
        'session_id': row.get('sessionId', 0),
        'notes': row.get('notes')
    }


def transform_update_session(row: Dict, device_id: str) -> Dict:
    """Transform update session for API."""
    return {
        'device_id': device_id,
        'session_uuid': row.get('sessionUuid', row.get('session_uuid')),
        'detected_at': row.get('detectedAt', row.get('detected_at', 0)),
        'pre_snapshot_locked_at': row.get('preSnapshotLockedAt', row.get('pre_snapshot_locked_at')),
        'update_installed_at': row.get('updateInstalledAt', row.get('update_installed_at')),
        'verification_completed_at': row.get('verificationCompletedAt', row.get('verification_completed_at')),
        'previous_build_fingerprint': row.get('previousBuildFingerprint', row.get('previous_build_fingerprint')),
        'current_build_fingerprint': row.get('currentBuildFingerprint', row.get('current_build_fingerprint')),
        'previous_android_version': row.get('previousAndroidVersion', row.get('previous_android_version')),
        'current_android_version': row.get('currentAndroidVersion', row.get('current_android_version')),
        'previous_security_patch': row.get('previousSecurityPatch', row.get('previous_security_patch')),
        'current_security_patch': row.get('currentSecurityPatch', row.get('current_security_patch')),
        'previous_modem_version': row.get('previousModemVersion', row.get('previous_modem_version')),
        'current_modem_version': row.get('currentModemVersion', row.get('current_modem_version')),
        'status': row.get('status'),
        'update_type': row.get('updateType', row.get('update_type')),
        'detection_method': row.get('detectionMethod', row.get('detection_method')),
        'known_issues_count': int(row.get('knownIssuesCount', row.get('known_issues_count', 0)) or 0),
        'pre_update_data_quality': float(row.get('preUpdateDataQuality', row.get('pre_update_data_quality', 0)) or 0),
        'pre_update_observation_hours': float(row.get('preUpdateObservationHours', row.get('pre_update_observation_hours', 0)) or 0),
        'pre_update_snapshot_count': row.get('preUpdateSnapshotCount', row.get('pre_update_snapshot_count')),
        'issues_fixed': int(row.get('issuesFixed', row.get('issues_fixed', 0)) or 0),
        'issues_unchanged': int(row.get('issuesUnchanged', row.get('issues_unchanged', 0)) or 0),
        'new_regressions_detected': int(row.get('newRegressionsDetected', row.get('new_regressions_detected', 0)) or 0),
        'update_impact_score': float(row.get('updateImpactScore', row.get('update_impact_score', 0)) or 0) if row.get('updateImpactScore') or row.get('update_impact_score') else None,
        'overall_verdict': row.get('overallVerdict', row.get('overall_verdict')),
        'user_was_notified': bool(row.get('userWasNotified', row.get('user_was_notified', 0))),
        'user_dismissed': bool(row.get('userDismissed', row.get('user_dismissed', 0))),
        'notes': row.get('notes')
    }


def transform_pre_update_snapshot(row: Dict, device_id: str) -> Dict:
    """Transform pre-update snapshot for API."""
    return {
        'device_id': device_id,
        'session_id': row.get('sessionId', row.get('session_id', 0)),
        'captured_at': row.get('capturedAt', row.get('captured_at', 0)),
        'build_fingerprint': row.get('buildFingerprint', row.get('build_fingerprint')),
        'android_version': row.get('androidVersion', row.get('android_version')),
        'security_patch': row.get('securityPatch', row.get('security_patch')),
        'modem_version': row.get('modemVersion', row.get('modem_version')),
        'avg_drain_rate_per_hour': float(row.get('avgDrainRatePerHour', row.get('avg_drain_rate_per_hour', 0)) or 0) if row.get('avgDrainRatePerHour') or row.get('avg_drain_rate_per_hour') else None,
        'idle_drain_rate_per_hour': float(row.get('idleDrainRatePerHour', row.get('idle_drain_rate_per_hour', 0)) or 0) if row.get('idleDrainRatePerHour') or row.get('idle_drain_rate_per_hour') else None,
        'battery_health_percent': float(row.get('batteryHealthPercent', row.get('battery_health_percent', 0)) or 0) if row.get('batteryHealthPercent') or row.get('battery_health_percent') else None,
        'cycle_count': row.get('cycleCount', row.get('cycle_count')),
        'avg_charging_temp': float(row.get('avgChargingTemp', row.get('avg_charging_temp', 0)) or 0) if row.get('avgChargingTemp') or row.get('avg_charging_temp') else None,
        'avg_cold_launch_ms': float(row.get('avgColdLaunchMs', row.get('avg_cold_launch_ms', 0)) or 0) if row.get('avgColdLaunchMs') or row.get('avg_cold_launch_ms') else None,
        'avg_jank_percent': float(row.get('avgJankPercent', row.get('avg_jank_percent', 0)) or 0) if row.get('avgJankPercent') or row.get('avg_jank_percent') else None,
        'avg_fps': float(row.get('avgFps', row.get('avg_fps', 0)) or 0) if row.get('avgFps') or row.get('avg_fps') else None,
        'avg_memory_used_percent': float(row.get('avgMemoryUsedPercent', row.get('avg_memory_used_percent', 0)) or 0) if row.get('avgMemoryUsedPercent') or row.get('avg_memory_used_percent') else None,
        'crashes_per_day': float(row.get('crashesPerDay', row.get('crashes_per_day', 0)) or 0) if row.get('crashesPerDay') or row.get('crashes_per_day') else None,
        'anrs_per_day': float(row.get('anrsPerDay', row.get('anrs_per_day', 0)) or 0) if row.get('anrsPerDay') or row.get('anrs_per_day') else None,
        'thermal_events_per_day': float(row.get('thermalEventsPerDay', row.get('thermal_events_per_day', 0)) or 0) if row.get('thermalEventsPerDay') or row.get('thermal_events_per_day') else None,
        'avg_operating_temp': float(row.get('avgOperatingTemp', row.get('avg_operating_temp', 0)) or 0) if row.get('avgOperatingTemp') or row.get('avg_operating_temp') else None,
        'max_temp_recorded': float(row.get('maxTempRecorded', row.get('max_temp_recorded', 0)) or 0) if row.get('maxTempRecorded') or row.get('max_temp_recorded') else None,
        'time_above_40c': row.get('timeAbove40C', row.get('time_above_40c')),
        'time_above_45c': row.get('timeAbove45C', row.get('time_above_45c')),
        'known_issues_json': row.get('knownIssuesJson', row.get('known_issues_json')),
        'anomaly_count_total': row.get('anomalyCountTotal', row.get('anomaly_count_total')),
        'high_severity_anomalies': row.get('highSeverityAnomalies', row.get('high_severity_anomalies')),
        'data_quality_score': float(row.get('dataQualityScore', row.get('data_quality_score', 0)) or 0) if row.get('dataQualityScore') or row.get('data_quality_score') else None,
        'observation_days': row.get('observationDays', row.get('observation_days')),
        'snapshot_count': row.get('snapshotCount', row.get('snapshot_count'))
    }


def transform_pre_update_issue(row: Dict, device_id: str) -> Dict:
    """Transform pre-update issue for API."""
    return {
        'device_id': device_id,
        'session_id': row.get('sessionId', row.get('session_id', 0)),
        'issue_uuid': row.get('issueUuid', row.get('issue_uuid')),
        'detected_at': row.get('detectedAt', row.get('detected_at', 0)),
        'issue_type': row.get('issueType', row.get('issue_type')),
        'component': row.get('component'),
        'severity': row.get('severity'),
        'issue_title': row.get('issueTitle', row.get('issue_title')),
        'issue_description': row.get('issueDescription', row.get('issue_description')),
        'technical_details': row.get('technicalDetails', row.get('technical_details')),
        'metric_name': row.get('metricName', row.get('metric_name')),
        'baseline_value': float(row.get('baselineValue', row.get('baseline_value', 0)) or 0) if row.get('baselineValue') or row.get('baseline_value') else None,
        'actual_value': float(row.get('actualValue', row.get('actual_value', 0)) or 0) if row.get('actualValue') or row.get('actual_value') else None,
        'deviation_percent': float(row.get('deviationPercent', row.get('deviation_percent', 0)) or 0) if row.get('deviationPercent') or row.get('deviation_percent') else None,
        'unit': row.get('unit'),
        'occurrence_count': row.get('occurrenceCount', row.get('occurrence_count')),
        'first_observed': row.get('firstObserved', row.get('first_observed')),
        'last_observed': row.get('lastObserved', row.get('last_observed')),
        'impact_score': float(row.get('impactScore', row.get('impact_score', 0)) or 0) if row.get('impactScore') or row.get('impact_score') else None,
        'affects_user_experience': bool(row.get('affectsUserExperience', row.get('affects_user_experience', 1))),
        'evidence_table_name': row.get('evidenceTableName', row.get('evidence_table_name')),
        'evidence_query': row.get('evidenceQuery', row.get('evidence_query')),
        'evidence_count': row.get('evidenceCount', row.get('evidence_count')),
        'source_package_name': row.get('sourcePackageName', row.get('source_package_name')),
        'source_app_label': row.get('sourceAppLabel', row.get('source_app_label'))
    }


def transform_post_update_verification(row: Dict, device_id: str) -> Dict:
    """Transform post-update verification for API."""
    return {
        'device_id': device_id,
        'session_id': row.get('sessionId', row.get('session_id', 0)),
        'pre_update_issue_id': row.get('preUpdateIssueId', row.get('pre_update_issue_id', 0)),
        'verified_at': row.get('verifiedAt', row.get('verified_at', 0)),
        'issue_uuid': row.get('issueUuid', row.get('issue_uuid')),
        'issue_type': row.get('issueType', row.get('issue_type')),
        'issue_title': row.get('issueTitle', row.get('issue_title')),
        'pre_update_value': float(row.get('preUpdateValue', row.get('pre_update_value', 0)) or 0) if row.get('preUpdateValue') or row.get('pre_update_value') else None,
        'pre_update_occurrences': row.get('preUpdateOccurrences', row.get('pre_update_occurrences')),
        'post_update_value': float(row.get('postUpdateValue', row.get('post_update_value', 0)) or 0) if row.get('postUpdateValue') or row.get('post_update_value') else None,
        'post_update_occurrences': row.get('postUpdateOccurrences', row.get('post_update_occurrences')),
        'absolute_change': float(row.get('absoluteChange', row.get('absolute_change', 0)) or 0) if row.get('absoluteChange') or row.get('absolute_change') else None,
        'percent_change': float(row.get('percentChange', row.get('percent_change', 0)) or 0) if row.get('percentChange') or row.get('percent_change') else None,
        'verification_status': row.get('verificationStatus', row.get('verification_status')),
        'confidence_score': float(row.get('confidenceScore', row.get('confidence_score', 0)) or 0) if row.get('confidenceScore') or row.get('confidence_score') else None,
        'post_update_evidence_json': row.get('postUpdateEvidenceJson', row.get('post_update_evidence_json')),
        'comparison_details': row.get('comparisonDetails', row.get('comparison_details')),
        'user_noticeable': bool(row.get('userNoticeable', row.get('user_noticeable', 0)))
    }


def transform_update_regression(row: Dict, device_id: str) -> Dict:
    """Transform update regression for API."""
    return {
        'device_id': device_id,
        'session_id': row.get('sessionId', row.get('session_id', 0)),
        'regression_uuid': row.get('regressionUuid', row.get('regression_uuid')),
        'detected_at': row.get('detectedAt', row.get('detected_at', 0)),
        'regression_type': row.get('regressionType', row.get('regression_type')),
        'component': row.get('component'),
        'severity': row.get('severity'),
        'title': row.get('title'),
        'description': row.get('description'),
        'technical_details': row.get('technicalDetails', row.get('technical_details')),
        'metric_name': row.get('metricName', row.get('metric_name')),
        'pre_update_baseline': float(row.get('preUpdateBaseline', row.get('pre_update_baseline', 0)) or 0) if row.get('preUpdateBaseline') or row.get('pre_update_baseline') else None,
        'post_update_value': float(row.get('postUpdateValue', row.get('post_update_value', 0)) or 0) if row.get('postUpdateValue') or row.get('post_update_value') else None,
        'delta_percent': float(row.get('deltaPercent', row.get('delta_percent', 0)) or 0) if row.get('deltaPercent') or row.get('delta_percent') else None,
        'unit': row.get('unit'),
        'first_occurrence': row.get('firstOccurrence', row.get('first_occurrence')),
        'occurrence_count': row.get('occurrenceCount', row.get('occurrence_count')),
        'evidence_json': row.get('evidenceJson', row.get('evidence_json')),
        'impact_score': float(row.get('impactScore', row.get('impact_score', 0)) or 0) if row.get('impactScore') or row.get('impact_score') else None,
        'user_noticeable': bool(row.get('userNoticeable', row.get('user_noticeable', 0))),
        'acknowledged': bool(row.get('acknowledged', 0)),
        'user_reported': bool(row.get('userReported', row.get('user_reported', 0)))
    }

def sync_to_backend(device_id: str, data: Dict[str, List], device_model: str = None) -> bool:
    """Sync collected data to the backend server."""
    payload = {
        'device_info': {
            'device_id': device_id,
            'device_model': device_model or 'Unknown',
            'android_version': None,
            'app_version': None
        },
        'battery_snapshots': data.get('snapshots', []),
        'charging_sessions': data.get('charging', []),
        'thermal_events': data.get('thermal', []),
        'drain_events': data.get('drain', []),
        'idle_drain_sessions': data.get('idle_drain_sessions', []),
        'battery_health': data.get('health', []),
        'safety_events': data.get('safety', []),
        'app_battery_usage': data.get('app_battery_usage', []),
        'app_drain_ranks': data.get('app_drain_ranks', []),
        'refresh_rate_sessions': data.get('refresh_rate_sessions', []),
        'refresh_rate_anomalies': data.get('refresh_rate_anomalies', []),
        'anomaly_records': data.get('anomaly_records', []),
        'thermal_heat_buckets': data.get('thermal_heat_buckets', []),
        'calibration_data': data.get('calibration_data', []),
        'device_info_sync': data.get('device_info', []),
        'ai_analysis_results': data.get('ai_analysis_results', []),
        'cpu_gpu_load_samples': data.get('cpu_gpu_load_samples', []),
        'memory_snapshots': data.get('memory_snapshots', []),
        'app_launch_metrics': data.get('app_launch_metrics', []),
        'frame_metrics': data.get('frame_metrics', []),
        'crash_events': data.get('crash_events', []),
        'anr_events': data.get('anr_events', []),
        'daily_aggregates': data.get('daily_aggregates', []),
        'regression_events': data.get('regression_events', []),
        'network_cellular_snapshots': data.get('network_cellular_snapshots', []),
        'network_wifi_snapshots': data.get('network_wifi_snapshots', []),
        'network_bluetooth_snapshots': data.get('network_bluetooth_snapshots', []),
        'modem_firmware_events': data.get('modem_firmware_events', []),
        'update_sessions': data.get('update_sessions', []),
        'pre_update_snapshots': data.get('pre_update_snapshots', []),
        'pre_update_issues': data.get('pre_update_issues', []),
        'post_update_verifications': data.get('post_update_verifications', []),
        'update_regressions': data.get('update_regressions', [])
    }
    
    total_records = sum(len(v) for v in data.values())
    if total_records == 0:
        logger.info("No new data to sync")
        return True
    
    logger.info(f"Syncing {total_records} records to backend...")
    
    try:
        response = requests.post(f"{BACKEND_URL}/api/v1/sync", json=payload, timeout=60)
        if response.status_code == 200:
            result = response.json()
            logger.info(f"Sync successful: {result}")
            return True
        else:
            logger.error(f"Sync failed: {response.status_code} - {response.text}")
            return False
    except requests.exceptions.ConnectionError:
        logger.error("Backend server not running. Start it with: python run_hexnil_backend.py")
        return False
    except Exception as e:
        logger.error(f"Sync error: {e}")
        return False


def process_sync(device_id: str, device_model: str = None) -> bool:
    """Process a full sync operation."""
    # Pull database from device
    if not pull_database():
        return False
    
    # Load last sync state
    state = load_sync_state()
    
    # Connect to pulled database
    try:
        conn = sqlite3.connect(str(LOCAL_DB_PATH))
        conn.row_factory = sqlite3.Row
    except sqlite3.Error as e:
        logger.error(f"Failed to open database: {e}")
        return False
    
    # Table mappings: (room_table_name, state_key, transform_func)
    # All transforms now need device_id
    tables = [
        ('battery_snapshots', 'last_snapshot_id', transform_snapshot),
        ('charging_sessions', 'last_charging_id', transform_charging),
        ('thermal_events', 'last_thermal_id', transform_thermal),
        ('drain_events', 'last_drain_id', transform_drain),
        ('battery_health', 'last_health_id', transform_health),
        ('safety_events', 'last_safety_id', transform_safety),
        ('app_battery_usage', 'last_app_battery_usage_id', transform_app_battery_usage),
        ('app_drain_ranks', 'last_app_drain_ranks_id', transform_app_drain_ranks),
        ('refresh_rate_sessions', 'last_refresh_rate_sessions_id', transform_refresh_rate_session),
        ('refresh_rate_anomalies', 'last_refresh_rate_anomalies_id', transform_refresh_rate_anomaly),
        ('anomaly_records', 'last_anomaly_records_id', transform_anomaly_record),
        ('thermal_heat_buckets', 'last_thermal_heat_buckets_id', transform_thermal_heat_bucket),
        ('calibration_data', 'last_calibration_data_id', transform_calibration_data),
        ('device_info', 'last_device_info_id', transform_device_info),
        ('ai_analysis_results', 'last_ai_analysis_results_id', transform_ai_analysis_result),
        ('cpu_gpu_load_samples', 'last_cpu_gpu_load_samples_id', transform_cpu_gpu_load_sample),
        ('memory_snapshots', 'last_memory_snapshots_id', transform_memory_snapshot),
        ('app_launch_metrics', 'last_app_launch_metrics_id', transform_app_launch),
        ('frame_metrics', 'last_frame_metrics_id', transform_frame_metric),
        ('crash_events', 'last_crash_events_id', transform_crash_event),
        ('anr_events', 'last_anr_events_id', transform_anr_event),
        ('daily_aggregates', 'last_daily_aggregates_id', transform_daily_aggregate),
        ('regression_events', 'last_regression_events_id', transform_regression_event),
        ('network_cellular_snapshots', 'last_network_cellular_snapshots_id', transform_network_cellular_snapshot),
        ('network_wifi_snapshots', 'last_network_wifi_snapshots_id', transform_network_wifi_snapshot),
        ('network_bluetooth_snapshots', 'last_network_bluetooth_snapshots_id', transform_network_bluetooth_snapshot),
        ('modem_firmware_events', 'last_modem_firmware_events_id', transform_modem_firmware_event),
        ('update_sessions', 'last_update_sessions_id', transform_update_session),
        ('pre_update_snapshots', 'last_pre_update_snapshots_id', transform_pre_update_snapshot),
        ('pre_update_issues', 'last_pre_update_issues_id', transform_pre_update_issue),
        ('post_update_verifications', 'last_post_update_verifications_id', transform_post_update_verification),
        ('update_regressions', 'last_update_regressions_id', transform_update_regression),
    ]
    
    # Read new data from each table
    new_data = {}
    max_ids = {}
    
    for table_name, state_key, transform_func in tables:
        last_id = state.get(state_key, 0)
        rows = read_new_data(conn, table_name, last_id)
        
        if rows:
            # Transform data for API (all transforms need device_id now)
            transformed = [transform_func(row, device_id) for row in rows]
            # Filter out None values (incomplete records)
            transformed = [t for t in transformed if t is not None]
            
            if not transformed:
                continue
                
            # Map table names to payload keys
            # Mapping from Room DB table name -> payload key in BatchSyncRequest
            key_map = {
                'battery_snapshots': 'battery_snapshots',
                'charging_sessions': 'charging_sessions',
                'thermal_events': 'thermal_events',
                'drain_events': 'drain_events',
                'idle_drain_sessions': 'idle_drain_sessions',
                'battery_health': 'battery_health',
                'safety_events': 'safety_events',
                'app_battery_usage': 'app_battery_usage',
                'app_drain_ranks': 'app_drain_ranks',
                'refresh_rate_sessions': 'refresh_rate_sessions',
                'refresh_rate_anomalies': 'refresh_rate_anomalies',
                'anomaly_records': 'anomaly_records',
                'thermal_heat_buckets': 'thermal_heat_buckets',
                'calibration_data': 'calibration_data',
                'device_info': 'device_info_sync',
                'ai_analysis_results': 'ai_analysis_results',
                'cpu_gpu_load_samples': 'cpu_gpu_load_samples',
                'memory_snapshots': 'memory_snapshots',
                'app_launch_metrics': 'app_launch_metrics',
                'frame_metrics': 'frame_metrics',
                'crash_events': 'crash_events',
                'anr_events': 'anr_events',
                'daily_aggregates': 'daily_aggregates',
                'regression_events': 'regression_events',
                'network_cellular_snapshots': 'network_cellular_snapshots',
                'network_wifi_snapshots': 'network_wifi_snapshots',
                'network_bluetooth_snapshots': 'network_bluetooth_snapshots',
                'modem_firmware_events': 'modem_firmware_events',
                'update_sessions': 'update_sessions',
                'pre_update_snapshots': 'pre_update_snapshots',
                'pre_update_issues': 'pre_update_issues',
                'post_update_verifications': 'post_update_verifications',
                'update_regressions': 'update_regressions'
            }
            key = key_map.get(table_name, table_name)
            new_data[key] = transformed
            
            # Track max ID
            max_ids[state_key] = max(row.get('id', 0) for row in rows)
            logger.info(f"Found {len(rows)} new records in {table_name}")
    
    conn.close()
    
    # Sync to backend
    if sync_to_backend(device_id, new_data, device_model):
        # Update state with new max IDs
        for key, max_id in max_ids.items():
            state[key] = max_id
        save_sync_state(state)
        return True
    
    return False


def wait_for_device():
    """Wait for a device to be connected."""
    logger.info("Waiting for device connection...")
    while not is_device_connected():
        time.sleep(CHECK_INTERVAL)
    logger.info("Device connected!")


def main():
    """Main entry point."""
    print("=" * 60)
    print("     HexNil Auto-Sync - Device Connection Monitor")
    print("=" * 60)
    print()
    
    # Check ADB exists
    if not os.path.exists(ADB_PATH):
        logger.error(f"ADB not found at: {ADB_PATH}")
        logger.error("Please install Android SDK Platform Tools")
        sys.exit(1)
    
    logger.info(f"ADB path: {ADB_PATH}")
    logger.info(f"Backend URL: {BACKEND_URL}")
    logger.info(f"Check interval: {CHECK_INTERVAL}s")
    print()
    
    was_connected = False
    
    try:
        while True:
            is_connected = is_device_connected()
            
            if is_connected and not was_connected:
                # Device just connected!
                logger.info("=" * 40)
                logger.info("DEVICE CONNECTED - Starting auto-sync...")
                logger.info("=" * 40)
                
                # Get device info
                device_id = get_device_id()
                device_model = get_device_model()
                
                if device_id:
                    logger.info(f"Device ID: {device_id}")
                    logger.info(f"Device Model: {device_model}")
                    
                    # Small delay to ensure device is fully ready
                    time.sleep(2)
                    
                    # Process sync
                    if process_sync(device_id, device_model):
                        logger.info("Auto-sync completed successfully!")
                    else:
                        logger.error("Auto-sync failed!")
                else:
                    logger.error("Could not get device ID")
                
                print()
            
            elif not is_connected and was_connected:
                logger.info("Device disconnected")
                print()
            
            was_connected = is_connected
            time.sleep(CHECK_INTERVAL)
            
    except KeyboardInterrupt:
        logger.info("\nAuto-sync stopped by user")
        sys.exit(0)


if __name__ == "__main__":
    main()
