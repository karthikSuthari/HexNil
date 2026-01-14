"""HexNil AI Automation Pipeline (laptop-side)

Guarantees
- Reads ONLY from raw tables.
- Writes ONLY to derived AI tables.
- Deterministic: uses ai_watermarks to process new raw data and upsert metrics.

Runs continuously (recommended) or can be triggered by Windows Task Scheduler.
"""

from __future__ import annotations

import os
import time
import json
import math
import logging
import statistics
from dataclasses import dataclass
from datetime import datetime, timezone, date
from typing import Dict, List, Optional, Set, Tuple

import mysql.connector
from mysql.connector import Error as MySQLError


LOG_FILE = os.getenv("HEXNIL_AI_LOG_FILE", "hexnil_ai_pipeline.log")
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    handlers=[logging.FileHandler(LOG_FILE, encoding="utf-8"), logging.StreamHandler()],
)
logger = logging.getLogger("hexnil.ai")


DB_CONFIG = {
    "host": os.getenv("HEXNIL_DB_HOST", "localhost"),
    "port": int(os.getenv("HEXNIL_DB_PORT", "3306")),
    "database": os.getenv("HEXNIL_DB_NAME", "hexnil"),
    "user": os.getenv("HEXNIL_AI_USER", "hexnil_ai"),
    "password": os.getenv("HEXNIL_AI_PASSWORD", "HexNil_AI_2026!"),
}

LOOP_INTERVAL_SEC = int(os.getenv("HEXNIL_AI_INTERVAL_SEC", "900"))
RAW_FETCH_LIMIT = int(os.getenv("HEXNIL_AI_RAW_FETCH_LIMIT", "5000"))

THERMAL_WARNING_TEMP = float(os.getenv("HEXNIL_THERMAL_WARNING", "40"))
THERMAL_CRITICAL_TEMP = float(os.getenv("HEXNIL_THERMAL_CRITICAL", "45"))
DRAIN_WARNING_RATE = float(os.getenv("HEXNIL_DRAIN_WARNING", "10"))  # %/h
DRAIN_CRITICAL_RATE = float(os.getenv("HEXNIL_DRAIN_CRITICAL", "20"))


def utc_ms() -> int:
    return int(time.time() * 1000)


def hour_start_ms(ts_ms: int) -> int:
    return (ts_ms // 3_600_000) * 3_600_000


def day_start_ms(ts_ms: int) -> int:
    return (ts_ms // 86_400_000) * 86_400_000


def to_date_utc(ts_ms: int) -> date:
    return datetime.fromtimestamp(ts_ms / 1000, tz=timezone.utc).date()


def to_datetime_utc(ts_ms: int) -> datetime:
    """Convert milliseconds timestamp to datetime for MySQL TIMESTAMP column."""
    return datetime.fromtimestamp(ts_ms / 1000, tz=timezone.utc)


def get_conn():
    return mysql.connector.connect(**DB_CONFIG)


def q(conn, sql: str, params: Tuple = ()) -> List[Dict]:
    cur = conn.cursor(dictionary=True)
    try:
        cur.execute(sql, params)
        return list(cur.fetchall())
    finally:
        cur.close()


def exec1(conn, sql: str, params: Tuple = ()) -> int:
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


def get_devices(conn) -> List[str]:
    rows = q(conn, "SELECT device_id FROM devices WHERE status = 'ACTIVE' OR status IS NULL")
    return [r["device_id"] for r in rows]


def get_watermark(conn, device_id: str, source_table: str) -> Tuple[int, int]:
    rows = q(
        conn,
        """
        SELECT last_processed_id, last_processed_ts
        FROM ai_watermarks
        WHERE device_id=%s AND source_table=%s
        """,
        (device_id, source_table),
    )
    if not rows:
        exec1(
            conn,
            """
            INSERT INTO ai_watermarks(device_id, source_table, last_processed_id, last_processed_ts)
            VALUES (%s, %s, 0, 0)
            """,
            (device_id, source_table),
        )
        return (0, 0)
    return (int(rows[0]["last_processed_id"] or 0), int(rows[0]["last_processed_ts"] or 0))


def set_watermark(conn, device_id: str, source_table: str, last_id: int, last_ts: int) -> None:
    exec1(
        conn,
        """
        UPDATE ai_watermarks
        SET last_processed_id=%s, last_processed_ts=%s
        WHERE device_id=%s AND source_table=%s
        """,
        (last_id, last_ts, device_id, source_table),
    )


def fetch_new_ids_and_hours(
    conn,
    device_id: str,
    table: str,
    ts_col: str = "timestamp",
    limit: int = RAW_FETCH_LIMIT,
) -> Tuple[int, int, Set[int]]:
    last_id, _last_ts = get_watermark(conn, device_id, table)
    rows = q(
        conn,
        f"SELECT id, {ts_col} AS ts FROM {table} WHERE device_id=%s AND id>%s ORDER BY id ASC LIMIT %s",
        (device_id, last_id, limit),
    )
    if not rows:
        return (last_id, _last_ts, set())

    max_id = last_id
    max_ts = _last_ts
    hours: Set[int] = set()
    for r in rows:
        rid = int(r["id"])
        ts = int(r["ts"])
        if rid > max_id:
            max_id = rid
        if ts > max_ts:
            max_ts = ts
        hours.add(hour_start_ms(ts))

    set_watermark(conn, device_id, table, max_id, max_ts)
    return (max_id, max_ts, hours)


def recompute_hour(conn, device_id: str, hour_ms: int) -> None:
    hour_end = hour_ms + 3_600_000

    snapshots = q(
        conn,
        """
        SELECT timestamp, battery_level, temperature, voltage, current_now,
               screen_on, power_save_mode, is_charging
        FROM battery_snapshots
        WHERE device_id=%s AND timestamp >= %s AND timestamp < %s
        ORDER BY timestamp ASC
        """,
        (device_id, hour_ms, hour_end),
    )

    if not snapshots:
        return

    battery_levels = [s["battery_level"] for s in snapshots if s["battery_level"] is not None]
    temps = [s["temperature"] for s in snapshots if s["temperature"] is not None]
    volts = [s["voltage"] for s in snapshots if s["voltage"] is not None]
    currents = [s["current_now"] for s in snapshots if s["current_now"] is not None]

    avg_batt = statistics.mean(battery_levels) if battery_levels else None
    min_batt = min(battery_levels) if battery_levels else None
    max_batt = max(battery_levels) if battery_levels else None
    batt_std = statistics.pstdev(battery_levels) if len(battery_levels) >= 2 else None

    avg_temp = statistics.mean(temps) if temps else None
    min_temp = min(temps) if temps else None
    max_temp = max(temps) if temps else None
    temp_std = statistics.pstdev(temps) if len(temps) >= 2 else None

    avg_v = statistics.mean(volts) if volts else None
    min_v = min(volts) if volts else None
    max_v = max(volts) if volts else None

    avg_i = statistics.mean(currents) if currents else None

    # Drain estimates
    first_level = battery_levels[0] if battery_levels else None
    last_level = battery_levels[-1] if battery_levels else None
    total_drain = max(0, (first_level - last_level)) if (first_level is not None and last_level is not None) else None

    ts0 = int(snapshots[0]["timestamp"])
    ts1 = int(snapshots[-1]["timestamp"])
    span_h = max(1e-6, (ts1 - ts0) / 3_600_000.0)
    drain_rate = (total_drain / span_h) if (total_drain is not None) else None

    charging_minutes = int(round(60 * (sum(1 for s in snapshots if s.get("is_charging")) / max(len(snapshots), 1))))
    screen_on_minutes = int(round(60 * (sum(1 for s in snapshots if s.get("screen_on")) / max(len(snapshots), 1))))
    power_save_minutes = int(round(60 * (sum(1 for s in snapshots if s.get("power_save_mode")) / max(len(snapshots), 1))))

    thermal_count = q(
        conn,
        "SELECT COUNT(*) AS c FROM thermal_events WHERE device_id=%s AND timestamp >= %s AND timestamp < %s",
        (device_id, hour_ms, hour_end),
    )[0]["c"]
    drain_count = q(
        conn,
        "SELECT COUNT(*) AS c FROM drain_events WHERE device_id=%s AND timestamp >= %s AND timestamp < %s",
        (device_id, hour_ms, hour_end),
    )[0]["c"]
    safety_count = q(
        conn,
        "SELECT COUNT(*) AS c FROM safety_events WHERE device_id=%s AND timestamp >= %s AND timestamp < %s",
        (device_id, hour_ms, hour_end),
    )[0]["c"]

    exec1(
        conn,
        """
        INSERT INTO ai_hourly_metrics(
            device_id, hour_timestamp,
            avg_battery_level, min_battery_level, max_battery_level, snapshot_count,
            total_drain, drain_rate_per_hour,
            avg_temperature, max_temperature,
            screen_on_minutes, charging_minutes,
            thermal_events_count, drain_events_count
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            computed_at = CURRENT_TIMESTAMP,
            avg_battery_level=VALUES(avg_battery_level),
            min_battery_level=VALUES(min_battery_level),
            max_battery_level=VALUES(max_battery_level),
            total_drain=VALUES(total_drain),
            drain_rate_per_hour=VALUES(drain_rate_per_hour),
            avg_temperature=VALUES(avg_temperature),
            max_temperature=VALUES(max_temperature),
            screen_on_minutes=VALUES(screen_on_minutes),
            charging_minutes=VALUES(charging_minutes),
            snapshot_count=VALUES(snapshot_count),
            thermal_events_count=VALUES(thermal_events_count),
            drain_events_count=VALUES(drain_events_count)
        """,
        (
            device_id,
            to_datetime_utc(hour_ms),
            avg_batt,
            min_batt,
            max_batt,
            len(snapshots),
            total_drain,
            drain_rate,
            avg_temp,
            max_temp,
            screen_on_minutes,
            charging_minutes,
            int(thermal_count),
            int(drain_count),
        ),
    )


def recompute_day(conn, device_id: str, day_ms: int) -> None:
    day_end = day_ms + 86_400_000
    d = to_date_utc(day_ms)

    rows = q(
        conn,
        """
        SELECT avg_battery_level, min_battery_level, max_battery_level,
               total_drain, drain_rate_per_hour,
               avg_temperature, max_temperature,
               screen_on_minutes, charging_minutes,
               thermal_events_count, drain_events_count
        FROM ai_hourly_metrics
        WHERE device_id=%s AND hour_timestamp >= %s AND hour_timestamp < %s
        """,
        (device_id, to_datetime_utc(day_ms), to_datetime_utc(day_end)),
    )
    if not rows:
        return

    avg_batts = [r["avg_battery_level"] for r in rows if r["avg_battery_level"] is not None]
    mins = [r["min_battery_level"] for r in rows if r["min_battery_level"] is not None]
    maxs = [r["max_battery_level"] for r in rows if r["max_battery_level"] is not None]
    drain_rates = [r["drain_rate_per_hour"] for r in rows if r["drain_rate_per_hour"] is not None]
    temps = [r["avg_temperature"] for r in rows if r["avg_temperature"] is not None]
    max_temps = [r["max_temperature"] for r in rows if r["max_temperature"] is not None]

    avg_batt = statistics.mean(avg_batts) if avg_batts else None
    min_batt = min(mins) if mins else None
    max_batt = max(maxs) if maxs else None
    total_drain = sum((r["total_drain"] or 0) for r in rows)
    avg_drain_rate = statistics.mean(drain_rates) if drain_rates else None

    avg_temp = statistics.mean(temps) if temps else None
    max_temp = max(max_temps) if max_temps else None

    screen_on_hours = sum((r["screen_on_minutes"] or 0) for r in rows) / 60.0
    charging_hours = sum((r["charging_minutes"] or 0) for r in rows) / 60.0

    thermal_events = int(sum((r["thermal_events_count"] or 0) for r in rows))
    drain_events = int(sum((r["drain_events_count"] or 0) for r in rows))

    safety_events = int(
        q(
            conn,
            "SELECT COUNT(*) AS c FROM safety_events WHERE device_id=%s AND timestamp >= %s AND timestamp < %s",
            (device_id, day_ms, day_end),
        )[0]["c"]
    )

    cs = q(
        conn,
        """
        SELECT start_level, end_level
        FROM charging_sessions
        WHERE device_id=%s AND start_time >= %s AND start_time < %s
        """,
        (device_id, day_ms, day_end),
    )
    full_charges = 0
    partial_charges = 0
    for r in cs:
        end_level = r.get("end_level")
        if end_level is None:
            continue
        if float(end_level) >= 95:
            full_charges += 1
        else:
            partial_charges += 1

    # Health estimate from last battery_health before day end
    health_rows = q(
        conn,
        """
        SELECT health_percentage
        FROM battery_health
        WHERE device_id=%s AND timestamp < %s
        ORDER BY timestamp DESC
        LIMIT 1
        """,
        (device_id, day_end),
    )
    est_health = float(health_rows[0]["health_percentage"]) if health_rows and health_rows[0]["health_percentage"] is not None else None

    exec1(
        conn,
        """
        INSERT INTO ai_daily_metrics(
            device_id, date_timestamp,
            avg_battery_level, min_battery_level, max_battery_level,
            total_drain, avg_drain_rate_per_hour,
            avg_temperature, max_temperature,
            screen_on_time_hours, charging_time_hours,
            full_charges, partial_charges,
            thermal_events_count, drain_events_count,
            safety_events_count,
            estimated_health_score
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            computed_at=CURRENT_TIMESTAMP,
            avg_battery_level=VALUES(avg_battery_level),
            min_battery_level=VALUES(min_battery_level),
            max_battery_level=VALUES(max_battery_level),
            total_drain=VALUES(total_drain),
            avg_drain_rate_per_hour=VALUES(avg_drain_rate_per_hour),
            avg_temperature=VALUES(avg_temperature),
            max_temperature=VALUES(max_temperature),
            screen_on_time_hours=VALUES(screen_on_time_hours),
            charging_time_hours=VALUES(charging_time_hours),
            full_charges=VALUES(full_charges),
            partial_charges=VALUES(partial_charges),
            thermal_events_count=VALUES(thermal_events_count),
            drain_events_count=VALUES(drain_events_count),
            safety_events_count=VALUES(safety_events_count),
            estimated_health_score=VALUES(estimated_health_score)
        """,
        (
            device_id,
            d,
            avg_batt,
            min_batt,
            max_batt,
            total_drain,
            avg_drain_rate,
            avg_temp,
            max_temp,
            screen_on_hours,
            charging_hours,
            full_charges,
            partial_charges,
            thermal_events,
            drain_events,
            safety_events,
            est_health,
        ),
    )


def detect_hourly_anomalies(conn, device_id: str, hour_ms: int) -> None:
    # Load current hour metric row
    rows = q(
        conn,
        "SELECT id, max_temperature, drain_rate_per_hour FROM ai_hourly_metrics WHERE device_id=%s AND hour_timestamp=%s",
        (device_id, to_datetime_utc(hour_ms)),
    )
    if not rows:
        return
    row = rows[0]
    hour_id = int(row["id"])
    max_temp = row.get("max_temperature")
    drain_rate = row.get("drain_rate_per_hour")

    # Baselines from last 7 days
    base = q(
        conn,
        """
        SELECT AVG(max_temperature) AS btemp, AVG(drain_rate_per_hour) AS bdrain
        FROM ai_hourly_metrics
        WHERE device_id=%s AND hour_timestamp >= %s AND hour_timestamp < %s
        """,
        (device_id, to_datetime_utc(hour_ms - 7 * 86_400_000), to_datetime_utc(hour_ms)),
    )[0]
    btemp = float(base["btemp"]) if base.get("btemp") is not None else 30.0
    bdrain = float(base["bdrain"]) if base.get("bdrain") is not None else 2.0

    anomalies: List[Tuple[str, str, float, float, str]] = []

    if max_temp is not None:
        mt = float(max_temp)
        if mt >= THERMAL_CRITICAL_TEMP:
            anomalies.append(("THERMAL_SPIKE", "CRITICAL", btemp, mt, f"Max temp {mt:.1f}°C (critical)"))
        elif mt >= THERMAL_WARNING_TEMP and mt >= btemp + 8:
            anomalies.append(("THERMAL_SPIKE", "WARNING", btemp, mt, f"Max temp {mt:.1f}°C above baseline"))

    if drain_rate is not None:
        dr = float(drain_rate)
        if dr >= DRAIN_CRITICAL_RATE:
            anomalies.append(("EXCESSIVE_DRAIN", "CRITICAL", bdrain, dr, f"Drain {dr:.1f}%/h (critical)"))
        elif dr >= DRAIN_WARNING_RATE and dr >= bdrain * 2.5:
            anomalies.append(("EXCESSIVE_DRAIN", "WARNING", bdrain, dr, f"Drain {dr:.1f}%/h above baseline"))

    for anomaly_type, severity, expected, actual, desc in anomalies:
        # Dedupe using unique key (device_id, anomaly_type, source_table, source_id)
        exec1(
            conn,
            """
            INSERT INTO ai_anomalies(
                device_id, anomaly_type, severity, confidence_score,
                source_table, source_id,
                metric_name, expected_value, actual_value, deviation_percent,
                description
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s,
                    CASE WHEN %s>0 THEN ((%s-%s)/%s)*100 ELSE NULL END,
                    %s)
            ON DUPLICATE KEY UPDATE
                detected_at=CURRENT_TIMESTAMP,
                severity=VALUES(severity),
                confidence_score=VALUES(confidence_score),
                expected_value=VALUES(expected_value),
                actual_value=VALUES(actual_value),
                description=VALUES(description)
            """,
            (
                device_id,
                anomaly_type,
                severity,
                0.85 if severity == "WARNING" else 0.95,
                "ai_hourly_metrics",
                hour_id,
                "max_temperature" if anomaly_type == "THERMAL_SPIKE" else "drain_rate_per_hour",
                expected,
                actual,
                expected,
                actual,
                expected,
                expected,
                desc,
            ),
        )


def compute_risk_and_recommend(conn, device_id: str) -> None:
    # Latest daily metrics
    d = q(
        conn,
        """
        SELECT id, avg_drain_rate_per_hour, max_temperature, estimated_health_score,
               thermal_events_count, drain_events_count, safety_events_count
        FROM ai_daily_metrics
        WHERE device_id=%s
        ORDER BY date_timestamp DESC
        LIMIT 1
        """,
        (device_id,),
    )
    if not d:
        return
    dm = d[0]

    max_temp = float(dm.get("max_temperature") or 0)
    drain = float(dm.get("avg_drain_rate_per_hour") or 0)
    health = dm.get("estimated_health_score")
    health_val = float(health) if health is not None else 100.0

    thermal_risk = 0.1
    if max_temp >= THERMAL_CRITICAL_TEMP:
        thermal_risk = 0.9
    elif max_temp >= THERMAL_WARNING_TEMP:
        thermal_risk = 0.6

    drain_risk = 0.1
    if drain >= DRAIN_CRITICAL_RATE:
        drain_risk = 0.85
    elif drain >= DRAIN_WARNING_RATE:
        drain_risk = 0.6

    health_risk = 0.1
    if health_val <= 50:
        health_risk = 0.85
    elif health_val <= 70:
        health_risk = 0.6
    elif health_val <= 85:
        health_risk = 0.35

    # Safety risk from recent anomalies and safety events
    recent_anoms = q(
        conn,
        """
        SELECT COUNT(*) AS c,
               SUM(CASE WHEN severity IN ('CRITICAL','EMERGENCY') THEN 1 ELSE 0 END) AS crit
        FROM ai_anomalies
        WHERE device_id=%s AND detected_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        """,
        (device_id,),
    )[0]
    anom_count = int(recent_anoms.get("c") or 0)
    crit_count = int(recent_anoms.get("crit") or 0)

    safety_risk = 0.1 + min(0.7, crit_count * 0.2) + min(0.2, anom_count * 0.02)
    safety_risk = min(0.95, safety_risk)

    overall = (
        thermal_risk * 0.30
        + drain_risk * 0.30
        + health_risk * 0.25
        + safety_risk * 0.15
    )

    if overall >= 0.8:
        level = "CRITICAL"
    elif overall >= 0.6:
        level = "HIGH"
    elif overall >= 0.35:
        level = "MEDIUM"
    else:
        level = "LOW"

    factors = {
        "thermal_risk": thermal_risk,
        "drain_risk": drain_risk,
        "health_risk": health_risk,
        "safety_risk": safety_risk,
        "max_temperature": max_temp,
        "avg_drain_rate": drain,
        "estimated_health_score": health_val,
        "recent_anomalies": anom_count,
        "recent_critical_anomalies": crit_count,
    }

    exec1(
        conn,
        """
        INSERT INTO ai_risk_scores(device_id, overall_risk_score, thermal_risk_score,
                                  drain_risk_score, health_risk_score, safety_risk_score,
                                  risk_level, contributing_factors)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        """,
        (
            device_id,
            overall,
            thermal_risk,
            drain_risk,
            health_risk,
            safety_risk,
            level,
            json.dumps(factors),
        ),
    )

    # Recommendations (idempotency: allow repeats but we keep them lightweight)
    if level in ("HIGH", "CRITICAL"):
        title = "Battery risk elevated"
        desc = f"Overall risk is {level} (score={overall:.2f}). Focus on heat and drain control."
        actions: List[str] = []
        if thermal_risk >= 0.6:
            actions.append("Avoid gaming/charging simultaneously")
            actions.append("Remove thick case during charging")
        if drain_risk >= 0.6:
            actions.append("Identify top draining apps and restrict background")
            actions.append("Enable Battery Saver during idle")
        if health_risk >= 0.6:
            actions.append("Avoid deep discharges; keep 20–80% if possible")

        exec1(
            conn,
            """
            INSERT INTO ai_recommendations(device_id, recommendation_type, priority, title, description, action_items)
            VALUES (%s, %s, %s, %s, %s, %s)
            """,
            (
                device_id,
                "HEALTH_PRESERVATION" if health_risk >= 0.6 else "THERMAL_MANAGEMENT",
                "URGENT" if level == "CRITICAL" else "HIGH",
                title,
                desc,
                json.dumps(actions),
            ),
        )


def process_device(conn, device_id: str) -> None:
    impacted_hours: Set[int] = set()

    # Any new raw data should trigger recomputation of affected hours.
    for table in [
        "battery_snapshots",
        "charging_sessions",
        "thermal_events",
        "drain_events",
        "idle_drain_sessions",
        "battery_health",
        "safety_events",
        "calibration_data",
        "ai_analysis_results",
    ]:
        try:
            _max_id, _max_ts, hours = fetch_new_ids_and_hours(conn, device_id, table, ts_col="timestamp")
            impacted_hours |= hours
        except Exception as e:
            logger.error(f"{device_id}: watermark/update failed for {table}: {e}")

    if not impacted_hours:
        return

    impacted_days: Set[int] = set(day_start_ms(h) for h in impacted_hours)

    for h in sorted(impacted_hours):
        recompute_hour(conn, device_id, h)
        detect_hourly_anomalies(conn, device_id, h)

    for dms in sorted(impacted_days):
        recompute_day(conn, device_id, dms)

    compute_risk_and_recommend(conn, device_id)


def run_forever() -> None:
    logger.info("HexNil AI pipeline starting")
    while True:
        try:
            conn = get_conn()
            devices = get_devices(conn)
            logger.info(f"AI cycle: {len(devices)} active devices")
            for device_id in devices:
                try:
                    process_device(conn, device_id)
                except Exception as e:
                    logger.error(f"AI error for {device_id}: {e}")
            conn.close()
        except MySQLError as e:
            logger.error(f"DB error: {e}")
        except Exception as e:
            logger.error(f"Fatal AI loop error: {e}")

        time.sleep(LOOP_INTERVAL_SEC)


if __name__ == "__main__":
    run_forever()
