"""End-to-end smoke test for the HexNil pipeline.

What it does:
- Starts the FastAPI backend (uvicorn) on 127.0.0.1:8000
- Calls /health
- POSTs a minimal /api/v1/sync payload for a test device
- Waits briefly for the spool worker to drain into MySQL
- Verifies raw table counts in MySQL
- Runs one AI processing pass for the test device
- Verifies derived table counts (ai_hourly_metrics/ai_daily_metrics)
- Shuts down the backend

Run (PowerShell):
  .\.venv\Scripts\python.exe .\smoke_test_pipeline.py

Notes:
- Uses the DB users created by backend/hexnil_mysql_schema.sql:
  - hexnil_backend / HexNil_Backend_2026!
  - hexnil_ai / HexNil_AI_2026!
"""

from __future__ import annotations

import os
import signal
import subprocess
import sys
import time
from typing import Dict

import requests
import mysql.connector as mysql


BASE_URL = os.getenv("HEXNIL_SMOKE_BASE_URL", "http://127.0.0.1:8000")
DEVICE_ID = os.getenv("HEXNIL_SMOKE_DEVICE_ID", "test_device_001")

DB_HOST = os.getenv("HEXNIL_DB_HOST", "localhost")
DB_PORT = int(os.getenv("HEXNIL_DB_PORT", "3306"))
DB_NAME = os.getenv("HEXNIL_DB_NAME", "hexnil")

BACKEND_USER = os.getenv("HEXNIL_DB_USER", "hexnil_backend")
BACKEND_PASS = os.getenv("HEXNIL_DB_PASSWORD", "HexNil_Backend_2026!")

AI_USER = os.getenv("HEXNIL_AI_USER", "hexnil_ai")
AI_PASS = os.getenv("HEXNIL_AI_PASSWORD", "HexNil_AI_2026!")


def wait_http(path: str, timeout_s: float = 10.0) -> requests.Response:
    end = time.time() + timeout_s
    last_exc = None
    while time.time() < end:
        try:
            r = requests.get(f"{BASE_URL}{path}", timeout=1)
            return r
        except Exception as e:  # noqa: BLE001
            last_exc = e
            time.sleep(0.2)
    raise RuntimeError(f"Server not reachable at {BASE_URL}{path}: {last_exc}")


def mysql_counts(user: str, password: str) -> Dict[str, int]:
    conn = mysql.connect(host=DB_HOST, port=DB_PORT, user=user, password=password, database=DB_NAME)
    cur = conn.cursor()
    out: Dict[str, int] = {}
    for name, sql in [
        ("devices", "SELECT COUNT(*) FROM devices WHERE device_id=%s"),
        ("battery_snapshots", "SELECT COUNT(*) FROM battery_snapshots WHERE device_id=%s"),
        ("battery_health", "SELECT COUNT(*) FROM battery_health WHERE device_id=%s"),
        ("sync_log", "SELECT COUNT(*) FROM sync_log WHERE device_id=%s"),
        ("ai_hourly_metrics", "SELECT COUNT(*) FROM ai_hourly_metrics WHERE device_id=%s"),
        ("ai_daily_metrics", "SELECT COUNT(*) FROM ai_daily_metrics WHERE device_id=%s"),
    ]:
        cur.execute(sql, (DEVICE_ID,))
        out[name] = int(cur.fetchone()[0])
    cur.close()
    conn.close()
    return out


def main() -> int:
    py = sys.executable

    env = os.environ.copy()
    env.setdefault("HEXNIL_HOST", "127.0.0.1")
    env.setdefault("HEXNIL_PORT", "8000")

    # Ensure backend uses the restricted backend user
    env.setdefault("HEXNIL_DB_USER", BACKEND_USER)
    env.setdefault("HEXNIL_DB_PASSWORD", BACKEND_PASS)

    print("[1/6] Starting backend...")
    proc = subprocess.Popen(
        [
            py,
            "-m",
            "uvicorn",
            "backend.hexnil_fastapi_server:app",
            "--host",
            "127.0.0.1",
            "--port",
            "8000",
        ],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )

    try:
        r = wait_http("/health", timeout_s=15)
        print("[2/6] /health:", r.status_code, r.text)

        now_ms = int(time.time() * 1000)
        payload = {
            "device_info": {
                "device_id": DEVICE_ID,
                "device_model": "SmokeTestDevice",
                "android_version": "14",
                "app_version": "1.0.0",
            },
            "battery_snapshots": [
                {
                    "device_id": DEVICE_ID,
                    "timestamp": now_ms - 120_000,
                    "battery_level": 80,
                    "temperature": 34.2,
                    "voltage": 3.91,
                    "current_now": -420.0,
                    "is_charging": False,
                    "charge_type": None,
                    "screen_on": True,
                    "power_save_mode": False,
                    "payload": {"src": "smoke"},
                },
                {
                    "device_id": DEVICE_ID,
                    "timestamp": now_ms - 60_000,
                    "battery_level": 78,
                    "temperature": 34.8,
                    "voltage": 3.89,
                    "current_now": -520.0,
                    "is_charging": False,
                    "charge_type": None,
                    "screen_on": True,
                    "power_save_mode": False,
                    "payload": {"src": "smoke"},
                },
            ],
            "charging_sessions": [],
            "thermal_events": [],
            "drain_events": [],
            "idle_drain_sessions": [],
            "battery_health": [
                {
                    "device_id": DEVICE_ID,
                    "timestamp": now_ms - 300_000,
                    "health_percentage": 92.0,
                    "cycle_count": 120,
                    "design_capacity_mah": 4500,
                    "current_capacity_mah": 4150,
                    "charge_full_design": 4500,
                    "charge_full": 4150,
                    "technology": "Li-ion",
                    "manufacture_date": "2024-01",
                    "first_use_date": now_ms - 30_000_000_000,
                    "payload": {"src": "smoke"},
                }
            ],
            "safety_events": [],
            "calibration_data": [],
            "ai_analysis_results": [],
        }

        print("[3/6] POST /api/v1/sync ...")
        rr = requests.post(f"{BASE_URL}/api/v1/sync", json=payload, timeout=10)
        print("sync:", rr.status_code, rr.text)
        rr.raise_for_status()

        print("[4/6] Waiting for spool drain...")
        time.sleep(2.0)

        raw_counts = mysql_counts(BACKEND_USER, BACKEND_PASS)
        print("raw+derived counts (post-ingest):", raw_counts)
        if raw_counts["battery_snapshots"] < 2:
            raise RuntimeError("Expected battery_snapshots to be inserted")
        if raw_counts["battery_health"] < 1:
            raise RuntimeError("Expected battery_health to be inserted")

        print("[5/6] Running one AI pass...")
        # Import and run a single device processing pass.
        from backend import hexnil_ai_pipeline as ai  # local import

        ai.DB_CONFIG["host"] = DB_HOST
        ai.DB_CONFIG["port"] = DB_PORT
        ai.DB_CONFIG["database"] = DB_NAME
        ai.DB_CONFIG["user"] = AI_USER
        ai.DB_CONFIG["password"] = AI_PASS

        conn = ai.get_conn()
        try:
            ai.process_device(conn, DEVICE_ID)
        finally:
            conn.close()

        derived_counts = mysql_counts(BACKEND_USER, BACKEND_PASS)
        print("raw+derived counts (post-AI):", derived_counts)
        if derived_counts["ai_hourly_metrics"] < 1:
            raise RuntimeError("Expected ai_hourly_metrics to be populated")
        if derived_counts["ai_daily_metrics"] < 1:
            raise RuntimeError("Expected ai_daily_metrics to be populated")

        print("[6/6] OK: pipeline smoke test passed")
        return 0
    finally:
        if proc.poll() is None:
            try:
                proc.send_signal(signal.CTRL_BREAK_EVENT if os.name == "nt" else signal.SIGINT)
            except Exception:
                proc.terminate()
            try:
                proc.wait(timeout=5)
            except Exception:
                proc.kill()

        # Drain any remaining output for debugging
        try:
            if proc.stdout is not None:
                out = proc.stdout.read()
                if out.strip():
                    print("\n[backend output]\n" + out[-4000:])
        except Exception:
            pass


if __name__ == "__main__":
    raise SystemExit(main())
