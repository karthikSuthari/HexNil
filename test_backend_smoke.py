r"""Backend Smoke Tests for HexNil FastAPI Server

Tests:
1. Health endpoint
2. Device registration
3. Sync endpoint (battery snapshots, charging sessions, thermal events, etc.)
4. Spool worker ingestion
5. Database connectivity
6. Error handling

Run:
  .\.venv\Scripts\python.exe .\test_backend_smoke.py
"""

from __future__ import annotations

import os
import signal
import subprocess
import sys
import time
from typing import Dict, Any, Optional
from dataclasses import dataclass

import requests
import mysql.connector as mysql


# ============================================================================
# CONFIGURATION
# ============================================================================

BASE_URL = os.getenv("HEXNIL_SMOKE_BASE_URL", "http://127.0.0.1:8000")
DEVICE_ID = os.getenv("HEXNIL_SMOKE_DEVICE_ID", "backend_smoke_device")

DB_HOST = os.getenv("HEXNIL_DB_HOST", "localhost")
DB_PORT = int(os.getenv("HEXNIL_DB_PORT", "3306"))
DB_NAME = os.getenv("HEXNIL_DB_NAME", "hexnil")
BACKEND_USER = os.getenv("HEXNIL_DB_USER", "hexnil_backend")
BACKEND_PASS = os.getenv("HEXNIL_DB_PASSWORD", "HexNil_Backend_2026!")


# ============================================================================
# TEST RESULT TRACKING
# ============================================================================

@dataclass
class TestResult:
    name: str
    passed: bool
    message: str
    duration_ms: float


class TestRunner:
    def __init__(self):
        self.results: list[TestResult] = []
        self.server_proc: Optional[subprocess.Popen] = None

    def run_test(self, name: str, test_fn):
        start = time.time()
        try:
            test_fn()
            duration = (time.time() - start) * 1000
            result = TestResult(name, True, "PASS", duration)
            print(f"  [PASS] {name} ({duration:.0f}ms)")
        except Exception as e:
            duration = (time.time() - start) * 1000
            result = TestResult(name, False, str(e), duration)
            print(f"  [FAIL] {name}: {e}")
        self.results.append(result)
        return result.passed

    def summary(self) -> int:
        passed = sum(1 for r in self.results if r.passed)
        failed = len(self.results) - passed
        print("\n" + "=" * 60)
        print(f"BACKEND SMOKE TEST RESULTS: {passed}/{len(self.results)} passed")
        if failed > 0:
            print("Failed tests:")
            for r in self.results:
                if not r.passed:
                    print(f"  - {r.name}: {r.message}")
        print("=" * 60)
        return 0 if failed == 0 else 1


# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

def wait_http(path: str, timeout_s: float = 15.0) -> requests.Response:
    end = time.time() + timeout_s
    last_exc = None
    while time.time() < end:
        try:
            r = requests.get(f"{BASE_URL}{path}", timeout=2)
            return r
        except Exception as e:
            last_exc = e
            time.sleep(0.3)
    raise RuntimeError(f"Server not reachable at {BASE_URL}{path}: {last_exc}")


def mysql_query(sql: str, params: tuple = ()) -> list:
    conn = mysql.connect(
        host=DB_HOST, port=DB_PORT, user=BACKEND_USER,
        password=BACKEND_PASS, database=DB_NAME
    )
    cur = conn.cursor(dictionary=True)
    cur.execute(sql, params)
    rows = list(cur.fetchall())
    cur.close()
    conn.close()
    return rows


def mysql_count(table: str, device_id: str) -> int:
    rows = mysql_query(f"SELECT COUNT(*) AS c FROM {table} WHERE device_id=%s", (device_id,))
    return int(rows[0]["c"])


def cleanup_test_device():
    """Remove test device data from previous runs."""
    conn = mysql.connect(
        host=DB_HOST, port=DB_PORT, user=BACKEND_USER,
        password=BACKEND_PASS, database=DB_NAME
    )
    cur = conn.cursor()
    tables = [
        "ai_recommendations", "ai_risk_scores", "ai_anomalies",
        "ai_daily_metrics", "ai_hourly_metrics", "ai_watermarks",
        "ai_analysis_results", "calibration_data", "safety_events",
        "battery_health", "idle_drain_sessions", "drain_events",
        "thermal_events", "charging_sessions", "battery_snapshots",
        "sync_log", "devices"
    ]
    for table in tables:
        try:
            cur.execute(f"DELETE FROM {table} WHERE device_id=%s", (DEVICE_ID,))
        except Exception:
            pass
    conn.commit()
    cur.close()
    conn.close()


def generate_sync_payload(ts_offset_ms: int = 0) -> Dict[str, Any]:
    """Generate a sync payload matching the FastAPI Pydantic models."""
    now_ms = int(time.time() * 1000) + ts_offset_ms
    return {
        "device_info": {
            "device_id": DEVICE_ID,
            "device_model": "BackendSmokeTest",
            "android_version": "14",
            "app_version": "1.0.0",
        },
        "battery_snapshots": [
            {
                "device_id": DEVICE_ID,
                "timestamp": now_ms - 300_000,
                "battery_level": 85,
                "temperature": 32.5,
                "voltage": 3.95,
                "current_now": -350.0,
                "is_charging": False,
                "charge_type": None,
                "screen_on": True,
                "power_save_mode": False,
                "payload": {"test": "snapshot1"},
            },
            {
                "device_id": DEVICE_ID,
                "timestamp": now_ms - 180_000,
                "battery_level": 82,
                "temperature": 33.2,
                "voltage": 3.92,
                "current_now": -420.0,
                "is_charging": False,
                "charge_type": None,
                "screen_on": True,
                "power_save_mode": False,
                "payload": {"test": "snapshot2"},
            },
            {
                "device_id": DEVICE_ID,
                "timestamp": now_ms - 60_000,
                "battery_level": 80,
                "temperature": 33.8,
                "voltage": 3.90,
                "current_now": -480.0,
                "is_charging": False,
                "charge_type": None,
                "screen_on": False,
                "power_save_mode": False,
                "payload": {"test": "snapshot3"},
            },
        ],
        "charging_sessions": [
            {
                "device_id": DEVICE_ID,
                "timestamp": now_ms - 3_600_000,
                "session_id": f"smoke_charging_{now_ms}",
                "start_time": now_ms - 5_400_000,
                "end_time": now_ms - 3_600_000,
                "start_level": 20,
                "end_level": 85,
                "charge_type": "AC",
                "avg_temperature": 36.0,
                "max_temperature": 40.0,
                "energy_added_mah": 3000.0,
                "charging_speed_ma": 2500.0,
                "is_complete": True,
                "payload": {"test": "charging1"},
            }
        ],
        "thermal_events": [
            {
                "device_id": DEVICE_ID,
                "timestamp": now_ms - 600_000,
                "temperature": 42.5,
                "threshold_exceeded": 40.0,
                "duration_seconds": 180,
                "trigger_source": "battery",
                "cpu_usage": 75.0,
                "foreground_app": "com.example.game",
                "severity": "WARNING",
                "payload": {"test": "thermal1"},
            }
        ],
        "drain_events": [
            {
                "device_id": DEVICE_ID,
                "timestamp": now_ms - 7_200_000,
                "start_level": 90,
                "end_level": 70,
                "duration_minutes": 30,
                "drain_rate_per_hour": 40.0,
                "screen_on_time_minutes": 25,
                "suspected_cause": "gaming",
                "top_apps": [{"package": "com.example.game", "drain": 15.0}],
                "payload": {"test": "drain1"},
            }
        ],
        "idle_drain_sessions": [
            {
                "device_id": DEVICE_ID,
                "timestamp": now_ms - 28_800_000,
                "session_id": f"smoke_idle_{now_ms}",
                "start_time": now_ms - 36_000_000,
                "end_time": now_ms - 28_800_000,
                "start_level": 60,
                "end_level": 55,
                "total_drain": 5,
                "drain_rate_per_hour": 2.5,
                "screen_off_duration_minutes": 120,
                "doze_time_minutes": 100,
                "wake_locks_count": 15,
                "network_activity_mb": 5.2,
                "payload": {"test": "idle1"},
            }
        ],
        "battery_health": [
            {
                "device_id": DEVICE_ID,
                "timestamp": now_ms - 86_400_000,
                "health_percentage": 94.5,
                "cycle_count": 85,
                "design_capacity_mah": 5000,
                "current_capacity_mah": 4725,
                "charge_full_design": 5000,
                "charge_full": 4725,
                "technology": "Li-ion",
                "manufacture_date": "2024-03",
                "first_use_date": now_ms - 180 * 86_400_000,
                "payload": {"test": "health1"},
            }
        ],
        "safety_events": [
            {
                "device_id": DEVICE_ID,
                "timestamp": now_ms - 172_800_000,
                "event_type": "VOLTAGE_ANOMALY",
                "severity": "WARNING",
                "temperature": 35.0,
                "voltage": 3.2,
                "current_ma": -500.0,
                "description": "Brief voltage dip detected",
                "action_taken": "none",
                "user_notified": False,
                "payload": {"test": "safety1"},
            }
        ],
        "calibration_data": [],
        "ai_analysis_results": [],
    }


# ============================================================================
# INDIVIDUAL TESTS
# ============================================================================

def test_health_endpoint():
    """Test /health endpoint returns OK."""
    r = requests.get(f"{BASE_URL}/health", timeout=5)
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"
    data = r.json()
    assert data.get("status") == "healthy", f"Expected healthy status: {data}"


def test_db_connectivity():
    """Test direct MySQL connectivity."""
    conn = mysql.connect(
        host=DB_HOST, port=DB_PORT, user=BACKEND_USER,
        password=BACKEND_PASS, database=DB_NAME
    )
    cur = conn.cursor()
    cur.execute("SELECT 1")
    result = cur.fetchone()
    cur.close()
    conn.close()
    assert result == (1,), f"Expected (1,), got {result}"


def test_sync_battery_snapshots():
    """Test POST /api/v1/sync inserts battery snapshots."""
    payload = generate_sync_payload()
    r = requests.post(f"{BASE_URL}/api/v1/sync", json=payload, timeout=10)
    assert r.status_code == 200, f"Sync failed: {r.status_code} {r.text}"
    
    # Wait for spool to drain
    time.sleep(1.5)
    
    count = mysql_count("battery_snapshots", DEVICE_ID)
    assert count >= 3, f"Expected >= 3 battery_snapshots, got {count}"


def test_sync_charging_sessions():
    """Test sync inserts charging sessions."""
    count = mysql_count("charging_sessions", DEVICE_ID)
    assert count >= 1, f"Expected >= 1 charging_sessions, got {count}"


def test_sync_thermal_events():
    """Test sync inserts thermal events."""
    count = mysql_count("thermal_events", DEVICE_ID)
    assert count >= 1, f"Expected >= 1 thermal_events, got {count}"


def test_sync_drain_events():
    """Test sync inserts drain events."""
    count = mysql_count("drain_events", DEVICE_ID)
    assert count >= 1, f"Expected >= 1 drain_events, got {count}"


def test_sync_idle_drain():
    """Test sync inserts idle drain sessions."""
    count = mysql_count("idle_drain_sessions", DEVICE_ID)
    assert count >= 1, f"Expected >= 1 idle_drain_sessions, got {count}"


def test_sync_battery_health():
    """Test sync inserts battery health records."""
    count = mysql_count("battery_health", DEVICE_ID)
    assert count >= 1, f"Expected >= 1 battery_health, got {count}"


def test_sync_safety_events():
    """Test sync inserts safety events."""
    count = mysql_count("safety_events", DEVICE_ID)
    assert count >= 1, f"Expected >= 1 safety_events, got {count}"


def test_device_registered():
    """Test device was registered in devices table."""
    count = mysql_count("devices", DEVICE_ID)
    assert count >= 1, f"Expected device to be registered"
    
    rows = mysql_query("SELECT device_model, android_version FROM devices WHERE device_id=%s", (DEVICE_ID,))
    assert rows[0]["device_model"] == "BackendSmokeTest", f"Device model mismatch"


def test_sync_log_created():
    """Test sync created a sync_log entry."""
    count = mysql_count("sync_log", DEVICE_ID)
    assert count >= 1, f"Expected >= 1 sync_log entry, got {count}"


def test_duplicate_sync_idempotent():
    """Test that re-posting same data doesn't cause errors."""
    payload = generate_sync_payload(ts_offset_ms=-600_000)  # Earlier data
    r = requests.post(f"{BASE_URL}/api/v1/sync", json=payload, timeout=10)
    assert r.status_code == 200, f"Duplicate sync failed: {r.status_code}"


def test_invalid_payload_rejected():
    """Test that invalid payloads are rejected."""
    invalid = {"device_info": {"device_id": ""}}  # Empty device_id
    r = requests.post(f"{BASE_URL}/api/v1/sync", json=invalid, timeout=5)
    assert r.status_code in (400, 422), f"Expected 400/422, got {r.status_code}"


def test_oversized_batch_rejected():
    """Test that excessively large batches are handled properly."""
    now_ms = int(time.time() * 1000)
    # Create batch with many snapshots (should work but test limits)
    payload = {
        "device_info": {
            "device_id": DEVICE_ID,
            "device_model": "TestDevice",
        },
        "battery_snapshots": [
            {
                "device_id": DEVICE_ID,
                "timestamp": now_ms - i * 1000,
                "battery_level": 50 + (i % 50),
                "is_charging": False,
                "screen_on": False,
                "power_save_mode": False,
            }
            for i in range(500)  # 500 snapshots - should still work
        ],
        "charging_sessions": [],
        "thermal_events": [],
        "drain_events": [],
        "idle_drain_sessions": [],
        "battery_health": [],
        "safety_events": [],
        "calibration_data": [],
        "ai_analysis_results": [],
    }
    r = requests.post(f"{BASE_URL}/api/v1/sync", json=payload, timeout=30)
    # Should succeed or return 413 if too large
    assert r.status_code in (200, 413), f"Unexpected status: {r.status_code}"


def test_spool_pending_endpoint():
    """Test /api/v1/spool/pending endpoint."""
    r = requests.get(f"{BASE_URL}/api/v1/spool/pending", timeout=5)
    # May or may not exist, just check it doesn't crash
    assert r.status_code in (200, 404), f"Unexpected: {r.status_code}"


def test_metrics_endpoint():
    """Test /metrics or similar monitoring endpoint."""
    r = requests.get(f"{BASE_URL}/metrics", timeout=5)
    # May or may not exist
    assert r.status_code in (200, 404), f"Unexpected: {r.status_code}"


# ============================================================================
# MAIN
# ============================================================================

def start_server() -> subprocess.Popen:
    """Start the FastAPI backend server."""
    py = sys.executable
    env = os.environ.copy()
    env.setdefault("HEXNIL_HOST", "127.0.0.1")
    env.setdefault("HEXNIL_PORT", "8000")
    env.setdefault("HEXNIL_DB_USER", BACKEND_USER)
    env.setdefault("HEXNIL_DB_PASSWORD", BACKEND_PASS)

    proc = subprocess.Popen(
        [py, "-m", "uvicorn", "backend.hexnil_fastapi_server:app",
         "--host", "127.0.0.1", "--port", "8000"],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return proc


def stop_server(proc: subprocess.Popen):
    """Stop the server gracefully."""
    if proc.poll() is None:
        try:
            # Use terminate() instead of CTRL_BREAK_EVENT to avoid debug mode
            proc.terminate()
        except Exception:
            pass
        try:
            proc.wait(timeout=5)
        except Exception:
            proc.kill()


def main() -> int:
    runner = TestRunner()
    
    print("=" * 60)
    print("HEXNIL BACKEND SMOKE TESTS")
    print("=" * 60)
    
    # Start server
    print("\n[Setup] Starting backend server...")
    proc = start_server()
    
    try:
        # Wait for server to be ready
        wait_http("/health", timeout_s=20)
        print("[Setup] Server ready\n")
        
        # Cleanup from previous runs
        print("[Setup] Cleaning up test data...")
        cleanup_test_device()
        print()
        
        # Run tests
        print("[Tests] Running backend smoke tests:\n")
        
        runner.run_test("Health Endpoint", test_health_endpoint)
        runner.run_test("DB Connectivity", test_db_connectivity)
        runner.run_test("Sync Battery Snapshots", test_sync_battery_snapshots)
        runner.run_test("Sync Charging Sessions", test_sync_charging_sessions)
        runner.run_test("Sync Thermal Events", test_sync_thermal_events)
        runner.run_test("Sync Drain Events", test_sync_drain_events)
        runner.run_test("Sync Idle Drain Sessions", test_sync_idle_drain)
        runner.run_test("Sync Battery Health", test_sync_battery_health)
        runner.run_test("Sync Safety Events", test_sync_safety_events)
        runner.run_test("Device Registration", test_device_registered)
        runner.run_test("Sync Log Created", test_sync_log_created)
        runner.run_test("Duplicate Sync Idempotent", test_duplicate_sync_idempotent)
        runner.run_test("Invalid Payload Rejected", test_invalid_payload_rejected)
        runner.run_test("Large Batch Handling", test_oversized_batch_rejected)
        runner.run_test("Spool Pending Endpoint", test_spool_pending_endpoint)
        runner.run_test("Metrics Endpoint", test_metrics_endpoint)
        
    finally:
        print("\n[Teardown] Stopping server...")
        stop_server(proc)
        
        # Drain output for debugging
        try:
            if proc.stdout:
                out = proc.stdout.read()
                if out.strip() and any(not r.passed for r in runner.results):
                    print("\n[Server Output (last 3000 chars)]")
                    print(out[-3000:])
        except Exception:
            pass
    
    return runner.summary()


if __name__ == "__main__":
    raise SystemExit(main())
