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
        'last_calibration_data_id': 0,
        'last_device_info_id': 0,
        'last_ai_analysis_results_id': 0
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
    """Transform battery snapshot for API - matches BatterySnapshot Pydantic model."""
    status = row.get('status', 0)
    is_charging = status == 2 or bool(row.get('isCharging', 0))  # STATUS_CHARGING
    plugged = row.get('plugged', 0)
    
    # Get charge type from chargingType field or derive from plugged
    charge_type = row.get('chargingType', row.get('chargeType'))
    if not charge_type:
        charge_type = 'ac' if plugged == 1 else ('usb' if plugged == 2 else ('wireless' if plugged == 4 else None))
    
    return {
        'device_id': device_id,
        'timestamp': row.get('timestamp'),
        'battery_level': row.get('level', 0),
        'temperature': row.get('temperature', 0) / 10.0 if row.get('temperature') else None,  # Convert from tenths
        'voltage': row.get('voltage', 0) / 1000.0 if row.get('voltage') else None,  # Convert to volts
        'current_now': row.get('currentNow', row.get('current_now', row.get('current'))),
        'is_charging': is_charging,
        'charge_type': charge_type,
        'screen_on': bool(row.get('screenOn', row.get('screen_on', 0))),
        'power_save_mode': bool(row.get('powerSaveMode', row.get('power_save_mode', False)) or False),
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
        'ai_analysis_results': data.get('ai_analysis_results', [])
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
            key_map = {
                'battery_snapshots': 'snapshots',
                'charging_sessions': 'charging',
                'thermal_events': 'thermal',
                'drain_events': 'drain',
                'battery_health': 'health',
                'safety_events': 'safety',
                'app_battery_usage': 'app_battery_usage',
                'app_drain_ranks': 'app_drain_ranks'
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
