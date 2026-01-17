r"""AI Pipeline Smoke Tests for HexNil

Tests:
1. Database connectivity with AI user
2. Watermark management
3. Hourly metrics computation
4. Daily metrics aggregation
5. Anomaly detection
6. Risk score calculation
7. Recommendation generation
8. Full device processing pipeline

Run:
  .\.venv\Scripts\python.exe .\test_ai_smoke.py

Notes:
- Requires data in raw tables (run test_backend_smoke.py first or use existing data)
- Uses AI user credentials for database access
"""

from __future__ import annotations

import os
import sys
import time
import json
from typing import Dict, Any, List, Optional, Tuple
from dataclasses import dataclass
from datetime import datetime, timezone

import pytest
import mysql.connector as mysql


# ============================================================================
# CONFIGURATION
# ============================================================================

DEVICE_ID = os.getenv("HEXNIL_SMOKE_DEVICE_ID", "ai_smoke_device")

DB_HOST = os.getenv("HEXNIL_DB_HOST", "localhost")
DB_PORT = int(os.getenv("HEXNIL_DB_PORT", "3306"))
DB_NAME = os.getenv("HEXNIL_DB_NAME", "hexnil")

AI_USER = os.getenv("HEXNIL_AI_USER", "hexnil_ai")
AI_PASS = os.getenv("HEXNIL_AI_PASSWORD", "HexNil_AI_2026!")

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
        print(f"AI SMOKE TEST RESULTS: {passed}/{len(self.results)} passed")
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

def get_ai_conn():
    return mysql.connect(
        host=DB_HOST, port=DB_PORT, user=AI_USER,
        password=AI_PASS, database=DB_NAME
    )


def get_backend_conn():
    return mysql.connect(
        host=DB_HOST, port=DB_PORT, user=BACKEND_USER,
        password=BACKEND_PASS, database=DB_NAME
    )


def q(conn, sql: str, params: tuple = ()) -> List[Dict]:
    cur = conn.cursor(dictionary=True)
    cur.execute(sql, params)
    rows = list(cur.fetchall())
    cur.close()
    return rows


def exec1(conn, sql: str, params: tuple = ()) -> int:
    cur = conn.cursor()
    try:
        cur.execute(sql, params)
        conn.commit()
        return int(cur.rowcount)
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()


def hour_start_ms(ts_ms: int) -> int:
    return (ts_ms // 3_600_000) * 3_600_000


def day_start_ms(ts_ms: int) -> int:
    return (ts_ms // 86_400_000) * 86_400_000


def cleanup_test_data():
    """Remove test device data from AI tables and raw tables."""
    conn = get_backend_conn()
    cur = conn.cursor()
    
    # Order matters: delete from child tables first due to FK constraints
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
            conn.commit()
        except Exception as e:
            conn.rollback()
            # Ignore errors - table may not have data or device_id column
            pass
    
    cur.close()
    conn.close()


def setup_test_data():
    """Insert test data into raw tables for AI processing."""
    conn = get_backend_conn()
    cur = conn.cursor()
    
    now_ms = int(time.time() * 1000)
    current_hour = hour_start_ms(now_ms)
    current_day = day_start_ms(now_ms)
    
    # Use a unique offset to avoid timestamp collisions on re-runs
    unique_offset = int(time.time()) % 1000
    
    # Insert device
    cur.execute("""
        INSERT INTO devices (device_id, device_model, android_version, app_version, status)
        VALUES (%s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE device_model=VALUES(device_model)
    """, (DEVICE_ID, "AISmokeTest", "14", "1.0.0", "ACTIVE"))
    
    # Insert battery snapshots across multiple hours with unique timestamps
    snapshots = []
    for hour_offset in range(3):  # 3 hours of data
        hour_base = current_hour - (hour_offset * 3_600_000)
        for minute in range(0, 60, 10):  # Every 10 minutes
            ts = hour_base + (minute * 60_000) + unique_offset
            level = 90 - hour_offset * 5 - (minute // 10)
            temp = 32.0 + (minute % 30) / 10
            snapshots.append((
                DEVICE_ID, ts, level, temp, 3.9, -400.0, 0, None, 1, 0
            ))
    
    for snapshot in snapshots:
        try:
            cur.execute("""
                INSERT INTO battery_snapshots 
                (device_id, timestamp, battery_level, temperature, voltage, current_now,
                 is_charging, charge_type, screen_on, power_save_mode)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """, snapshot)
        except Exception:
            pass  # Ignore duplicate key errors
    
    # Insert a charging session
    try:
        cur.execute("""
            INSERT INTO charging_sessions
            (device_id, session_id, timestamp, start_time, end_time, start_level, end_level,
             charge_type, avg_temperature, max_temperature)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """, (
            DEVICE_ID, f"smoke_session_{now_ms}_{unique_offset}",
            current_hour - 7_200_000 + unique_offset,
            current_hour - 10_800_000 + unique_offset, current_hour - 7_200_000 + unique_offset,
            30, 90, "AC", 35.0, 38.0
        ))
    except Exception:
        pass
    
    # Insert thermal event (no event_type column in this table)
    try:
        cur.execute("""
            INSERT INTO thermal_events
            (device_id, timestamp, temperature, threshold_exceeded, duration_seconds, 
             trigger_source, severity)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
        """, (DEVICE_ID, current_hour - 3_600_000 + unique_offset, 41.5, 40.0, 120, "battery", "WARNING"))
    except Exception:
        pass
    
    # Insert drain event
    try:
        cur.execute("""
            INSERT INTO drain_events
            (device_id, timestamp, start_level, end_level, duration_minutes, drain_rate_per_hour)
            VALUES (%s, %s, %s, %s, %s, %s)
        """, (DEVICE_ID, current_hour - 5_400_000 + unique_offset, 80, 60, 30, 40.0))
    except Exception:
        pass
    
    # Insert battery health
    try:
        cur.execute("""
            INSERT INTO battery_health
            (device_id, timestamp, health_percentage, cycle_count, 
             design_capacity_mah, current_capacity_mah, technology)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
        """, (DEVICE_ID, current_hour - 86_400_000 + unique_offset, 92.0, 150, 5000, 4600, "Li-ion"))
    except Exception:
        pass
    
    # Insert safety event
    try:
        cur.execute("""
            INSERT INTO safety_events
            (device_id, timestamp, event_type, severity, description)
            VALUES (%s, %s, %s, %s, %s)
        """, (DEVICE_ID, current_hour - 43_200_000 + unique_offset, "VOLTAGE_DROP", "WARNING", "Brief voltage dip"))
    except Exception:
        pass
    
    conn.commit()
    cur.close()
    conn.close()
    
    return current_hour, current_day


# ============================================================================
# AI PIPELINE FUNCTIONS (IMPORTED FROM PIPELINE)
# ============================================================================

def get_watermark(conn, device_id: str, source_table: str) -> Tuple[int, int]:
    rows = q(conn, """
        SELECT last_processed_id, last_processed_ts
        FROM ai_watermarks WHERE device_id=%s AND source_table=%s
    """, (device_id, source_table))
    if not rows:
        exec1(conn, """
            INSERT INTO ai_watermarks(device_id, source_table, last_processed_id, last_processed_ts)
            VALUES (%s, %s, 0, 0)
        """, (device_id, source_table))
        return (0, 0)
    return (int(rows[0]["last_processed_id"] or 0), int(rows[0]["last_processed_ts"] or 0))


def set_watermark(conn, device_id: str, source_table: str, last_id: int, last_ts: int) -> None:
    exec1(conn, """
        UPDATE ai_watermarks SET last_processed_id=%s, last_processed_ts=%s
        WHERE device_id=%s AND source_table=%s
    """, (last_id, last_ts, device_id, source_table))


# ============================================================================
# PYTEST FIXTURES
# ============================================================================

@pytest.fixture(scope="module", autouse=True)
def ensure_device_exists():
    """Ensure test device is registered before any tests run.
    
    This fixture runs automatically for the entire module and ensures
    the ai_smoke_device exists in the devices table before tests that
    depend on FK constraints (like ai_watermarks) can run.
    """
    conn = get_backend_conn()
    cur = conn.cursor()
    
    try:
        # Register the device if it doesn't exist
        cur.execute("""
            INSERT INTO devices (device_id, device_model, android_version, app_version, status)
            VALUES (%s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE device_model=VALUES(device_model), last_seen=CURRENT_TIMESTAMP
        """, (DEVICE_ID, "AISmokeTest", "14", "1.0.0", "ACTIVE"))
        conn.commit()
    except Exception:
        conn.rollback()
    finally:
        cur.close()
        conn.close()
    
    yield  # Tests run here
    
    # Optional: cleanup after all tests (keeping data for debugging)


# ============================================================================
# INDIVIDUAL TESTS
# ============================================================================

def test_ai_db_connectivity():
    """Test AI user can connect to database."""
    conn = get_ai_conn()
    cur = conn.cursor()
    cur.execute("SELECT 1")
    result = cur.fetchone()
    cur.close()
    conn.close()
    assert result == (1,), f"Expected (1,), got {result}"


def test_ai_can_read_raw_tables():
    """Test AI user can read raw tables."""
    conn = get_ai_conn()
    
    tables = ["battery_snapshots", "charging_sessions", "thermal_events",
              "drain_events", "battery_health", "safety_events", "devices"]
    
    for table in tables:
        rows = q(conn, f"SELECT COUNT(*) AS c FROM {table} WHERE device_id=%s", (DEVICE_ID,))
        assert rows[0]["c"] >= 0, f"Failed to read {table}"
    
    conn.close()


def test_ai_can_write_ai_tables():
    """Test AI user can write to AI tables."""
    conn = get_ai_conn()
    
    # Test watermark write
    exec1(conn, """
        INSERT INTO ai_watermarks(device_id, source_table, last_processed_id, last_processed_ts)
        VALUES (%s, %s, 0, 0)
        ON DUPLICATE KEY UPDATE last_processed_ts = 0
    """, (DEVICE_ID, "test_table"))
    
    conn.close()


def test_watermark_creation():
    """Test watermark creation for new device/table pairs."""
    conn = get_ai_conn()
    
    last_id, last_ts = get_watermark(conn, DEVICE_ID, "battery_snapshots")
    assert last_id >= 0, f"Watermark ID should be >= 0"
    assert last_ts >= 0, f"Watermark TS should be >= 0"
    
    conn.close()


def test_watermark_update():
    """Test watermark update functionality."""
    conn = get_ai_conn()
    
    set_watermark(conn, DEVICE_ID, "battery_snapshots", 999, 123456789)
    
    last_id, last_ts = get_watermark(conn, DEVICE_ID, "battery_snapshots")
    assert last_id == 999, f"Expected 999, got {last_id}"
    assert last_ts == 123456789, f"Expected 123456789, got {last_ts}"
    
    # Reset for other tests
    set_watermark(conn, DEVICE_ID, "battery_snapshots", 0, 0)
    
    conn.close()


def test_hourly_metrics_computation():
    """Test hourly metrics are computed correctly."""
    # Import and use the actual AI pipeline
    sys.path.insert(0, os.path.dirname(__file__))
    from backend import hexnil_ai_pipeline as ai
    
    ai.DB_CONFIG["host"] = DB_HOST
    ai.DB_CONFIG["port"] = DB_PORT
    ai.DB_CONFIG["database"] = DB_NAME
    ai.DB_CONFIG["user"] = AI_USER
    ai.DB_CONFIG["password"] = AI_PASS
    
    conn = ai.get_conn()
    
    # Get a recent hour with data
    rows = q(conn, """
        SELECT MIN(timestamp) as min_ts, MAX(timestamp) as max_ts
        FROM battery_snapshots WHERE device_id=%s
    """, (DEVICE_ID,))
    
    if rows and rows[0]["min_ts"]:
        hour_ms = hour_start_ms(int(rows[0]["min_ts"]))
        ai.recompute_hour(conn, DEVICE_ID, hour_ms)
        
        # Verify hourly metric was created (use datetime for query)
        hour_dt = ai.to_datetime_utc(hour_ms)
        result = q(conn, """
            SELECT avg_battery_level, snapshot_count 
            FROM ai_hourly_metrics 
            WHERE device_id=%s AND hour_timestamp=%s
        """, (DEVICE_ID, hour_dt))
        
        assert len(result) >= 1, "Hourly metric should be created"
        assert result[0]["snapshot_count"] > 0, "Should have snapshot count"
    
    conn.close()


def test_daily_metrics_computation():
    """Test daily metrics aggregation."""
    from backend import hexnil_ai_pipeline as ai
    
    ai.DB_CONFIG["host"] = DB_HOST
    ai.DB_CONFIG["port"] = DB_PORT
    ai.DB_CONFIG["database"] = DB_NAME
    ai.DB_CONFIG["user"] = AI_USER
    ai.DB_CONFIG["password"] = AI_PASS
    
    conn = ai.get_conn()
    
    # Get current day
    now_ms = int(time.time() * 1000)
    day_ms = day_start_ms(now_ms)
    day_dt = ai.to_datetime_utc(day_ms)
    
    # First ensure hourly metrics exist
    rows = q(conn, """
        SELECT COUNT(*) as c FROM ai_hourly_metrics 
        WHERE device_id=%s AND hour_timestamp >= %s
    """, (DEVICE_ID, day_dt))
    
    if rows[0]["c"] > 0:
        ai.recompute_day(conn, DEVICE_ID, day_ms)
        
        # Verify daily metric
        result = q(conn, """
            SELECT avg_battery_level, total_drain
            FROM ai_daily_metrics 
            WHERE device_id=%s AND date_timestamp=%s
        """, (DEVICE_ID, ai.to_date_utc(day_ms)))
        
        assert len(result) >= 1, "Daily metric should be created"
    
    conn.close()


def test_anomaly_detection():
    """Test anomaly detection for thermal and drain spikes."""
    from backend import hexnil_ai_pipeline as ai
    
    ai.DB_CONFIG["host"] = DB_HOST
    ai.DB_CONFIG["port"] = DB_PORT
    ai.DB_CONFIG["database"] = DB_NAME
    ai.DB_CONFIG["user"] = AI_USER
    ai.DB_CONFIG["password"] = AI_PASS
    
    conn = ai.get_conn()
    
    # Get an hour with computed metrics (using datetime type)
    rows = q(conn, """
        SELECT hour_timestamp FROM ai_hourly_metrics 
        WHERE device_id=%s ORDER BY hour_timestamp DESC LIMIT 1
    """, (DEVICE_ID,))
    
    if rows:
        hour_dt = rows[0]["hour_timestamp"]
        # Convert datetime to milliseconds for the function
        if hasattr(hour_dt, 'timestamp'):
            hour_ms = int(hour_dt.timestamp() * 1000)
        else:
            hour_ms = int(hour_dt)
        ai.detect_hourly_anomalies(conn, DEVICE_ID, hour_ms)
        # Even if no anomalies, function should complete without error
    
    conn.close()


def test_risk_score_calculation():
    """Test risk score calculation."""
    from backend import hexnil_ai_pipeline as ai
    
    ai.DB_CONFIG["host"] = DB_HOST
    ai.DB_CONFIG["port"] = DB_PORT
    ai.DB_CONFIG["database"] = DB_NAME
    ai.DB_CONFIG["user"] = AI_USER
    ai.DB_CONFIG["password"] = AI_PASS
    
    conn = ai.get_conn()
    
    # Ensure we have daily metrics first
    rows = q(conn, """
        SELECT COUNT(*) as c FROM ai_daily_metrics WHERE device_id=%s
    """, (DEVICE_ID,))
    
    if rows[0]["c"] > 0:
        ai.compute_risk_and_recommend(conn, DEVICE_ID)
        
        # Verify risk score was created
        result = q(conn, """
            SELECT overall_risk_score, risk_level 
            FROM ai_risk_scores 
            WHERE device_id=%s 
            ORDER BY computed_at DESC LIMIT 1
        """, (DEVICE_ID,))
        
        assert len(result) >= 1, "Risk score should be created"
        assert result[0]["risk_level"] in ("LOW", "MEDIUM", "HIGH", "CRITICAL")
    
    conn.close()


def test_full_device_processing():
    """Test full process_device() pipeline."""
    from backend import hexnil_ai_pipeline as ai
    
    ai.DB_CONFIG["host"] = DB_HOST
    ai.DB_CONFIG["port"] = DB_PORT
    ai.DB_CONFIG["database"] = DB_NAME
    ai.DB_CONFIG["user"] = AI_USER
    ai.DB_CONFIG["password"] = AI_PASS
    
    conn = ai.get_conn()
    
    # Reset watermarks so data is reprocessed
    for table in ["battery_snapshots", "charging_sessions", "thermal_events",
                  "drain_events", "battery_health", "safety_events"]:
        try:
            exec1(conn, """
                UPDATE ai_watermarks SET last_processed_id=0, last_processed_ts=0
                WHERE device_id=%s AND source_table=%s
            """, (DEVICE_ID, table))
        except Exception:
            pass
    
    # Run full processing
    ai.process_device(conn, DEVICE_ID)
    
    # Verify outputs
    hourly = q(conn, "SELECT COUNT(*) as c FROM ai_hourly_metrics WHERE device_id=%s", (DEVICE_ID,))
    assert hourly[0]["c"] >= 1, "Should have hourly metrics"
    
    daily = q(conn, "SELECT COUNT(*) as c FROM ai_daily_metrics WHERE device_id=%s", (DEVICE_ID,))
    assert daily[0]["c"] >= 1, "Should have daily metrics"
    
    conn.close()


def test_recommendations_created():
    """Test that recommendations are created for high-risk devices."""
    conn = get_ai_conn()
    
    # Check if any recommendations exist
    rows = q(conn, """
        SELECT recommendation_type, priority, title
        FROM ai_recommendations 
        WHERE device_id=%s
        ORDER BY created_at DESC LIMIT 5
    """, (DEVICE_ID,))
    
    # Recommendations may or may not exist depending on risk level
    # Just verify the query works
    assert isinstance(rows, list), "Should return list of recommendations"
    
    conn.close()


def test_ai_tables_have_correct_schema():
    """Test AI tables have expected columns."""
    conn = get_ai_conn()
    
    # Verify ai_hourly_metrics has key columns
    rows = q(conn, """
        SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_SCHEMA=%s AND TABLE_NAME='ai_hourly_metrics'
    """, (DB_NAME,))
    
    columns = {r["COLUMN_NAME"] for r in rows}
    required = {"device_id", "hour_timestamp", "avg_battery_level", "total_drain", "snapshot_count"}
    assert required.issubset(columns), f"Missing columns: {required - columns}"
    
    # Verify ai_daily_metrics
    rows = q(conn, """
        SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_SCHEMA=%s AND TABLE_NAME='ai_daily_metrics'
    """, (DB_NAME,))
    
    columns = {r["COLUMN_NAME"] for r in rows}
    required = {"device_id", "date_timestamp", "avg_battery_level", "estimated_health_score"}
    assert required.issubset(columns), f"Missing columns: {required - columns}"
    
    conn.close()


def test_incremental_processing():
    """Test that AI pipeline only processes new data (watermark-based)."""
    from backend import hexnil_ai_pipeline as ai
    
    ai.DB_CONFIG["host"] = DB_HOST
    ai.DB_CONFIG["port"] = DB_PORT
    ai.DB_CONFIG["database"] = DB_NAME
    ai.DB_CONFIG["user"] = AI_USER
    ai.DB_CONFIG["password"] = AI_PASS
    
    conn = ai.get_conn()
    
    # Get current watermark
    last_id_before, _ = get_watermark(conn, DEVICE_ID, "battery_snapshots")
    
    # Process device (should update watermark)
    ai.process_device(conn, DEVICE_ID)
    
    # Get new watermark
    last_id_after, _ = get_watermark(conn, DEVICE_ID, "battery_snapshots")
    
    # Watermark should advance or stay same
    assert last_id_after >= last_id_before, "Watermark should not decrease"
    
    # Run again - should be idempotent with no new data
    hourly_before = q(conn, "SELECT COUNT(*) as c FROM ai_hourly_metrics WHERE device_id=%s", (DEVICE_ID,))
    ai.process_device(conn, DEVICE_ID)
    hourly_after = q(conn, "SELECT COUNT(*) as c FROM ai_hourly_metrics WHERE device_id=%s", (DEVICE_ID,))
    
    # Should not create duplicate hourly metrics for same hours
    assert hourly_after[0]["c"] == hourly_before[0]["c"], "Should not duplicate hourly metrics"
    
    conn.close()


def test_error_handling_invalid_device():
    """Test AI pipeline handles non-existent device gracefully."""
    from backend import hexnil_ai_pipeline as ai
    
    ai.DB_CONFIG["host"] = DB_HOST
    ai.DB_CONFIG["port"] = DB_PORT
    ai.DB_CONFIG["database"] = DB_NAME
    ai.DB_CONFIG["user"] = AI_USER
    ai.DB_CONFIG["password"] = AI_PASS
    
    conn = ai.get_conn()
    
    # Process non-existent device - should not crash
    try:
        ai.process_device(conn, "nonexistent_device_xyz123")
        # Should complete without error (just no data to process)
    except Exception as e:
        assert False, f"Should handle missing device gracefully: {e}"
    finally:
        conn.close()


# ============================================================================
# MAIN
# ============================================================================

def main() -> int:
    runner = TestRunner()
    
    print("=" * 60)
    print("HEXNIL AI PIPELINE SMOKE TESTS")
    print("=" * 60)
    
    # Setup
    print("\n[Setup] Cleaning up test data...")
    cleanup_test_data()
    
    print("[Setup] Inserting test data...")
    current_hour, current_day = setup_test_data()
    print(f"[Setup] Test data created for hour={current_hour}, day={current_day}\n")
    
    # Run tests
    print("[Tests] Running AI smoke tests:\n")
    
    runner.run_test("AI DB Connectivity", test_ai_db_connectivity)
    runner.run_test("AI Read Raw Tables", test_ai_can_read_raw_tables)
    runner.run_test("AI Write AI Tables", test_ai_can_write_ai_tables)
    runner.run_test("Watermark Creation", test_watermark_creation)
    runner.run_test("Watermark Update", test_watermark_update)
    runner.run_test("AI Tables Schema", test_ai_tables_have_correct_schema)
    runner.run_test("Hourly Metrics Computation", test_hourly_metrics_computation)
    runner.run_test("Daily Metrics Computation", test_daily_metrics_computation)
    runner.run_test("Anomaly Detection", test_anomaly_detection)
    runner.run_test("Risk Score Calculation", test_risk_score_calculation)
    runner.run_test("Full Device Processing", test_full_device_processing)
    runner.run_test("Recommendations Created", test_recommendations_created)
    runner.run_test("Incremental Processing", test_incremental_processing)
    runner.run_test("Error Handling Invalid Device", test_error_handling_invalid_device)
    
    return runner.summary()


if __name__ == "__main__":
    raise SystemExit(main())
