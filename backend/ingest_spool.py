"""SQLite-backed durable spool for non-blocking ingestion.

Why this exists
- MySQL can be temporarily unavailable (restarts, connectivity, schema apply)
- FastAPI must never block ingestion requests on MySQL writes

Behavior
- API request validates JSON (Pydantic) then enqueues into this spool (fast local write)
- Background worker drains spool into MySQL and marks items DONE

This makes ingestion deterministic and durable across restarts.
"""

from __future__ import annotations

import json
import os
import sqlite3
import time
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple


DEFAULT_SPOOL_PATH = os.getenv(
    "HEXNIL_SPOOL_PATH",
    os.path.join(os.path.dirname(__file__), "hexnil_ingest_spool.sqlite"),
)


@dataclass
class SpoolItem:
    id: int
    device_id: str
    created_at_ms: int
    payload_json: str
    attempts: int


def _utc_ms() -> int:
    return int(time.time() * 1000)


def init_spool(spool_path: str = DEFAULT_SPOOL_PATH) -> None:
    os.makedirs(os.path.dirname(spool_path), exist_ok=True)
    conn = sqlite3.connect(spool_path)
    try:
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA synchronous=NORMAL;")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS spool_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL,
                status TEXT NOT NULL, -- PENDING, IN_PROGRESS, DONE, DEAD
                attempts INTEGER NOT NULL DEFAULT 0,
                next_attempt_at_ms INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                payload_json TEXT NOT NULL
            );
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_spool_status_next ON spool_items(status, next_attempt_at_ms, id);"
        )
        conn.commit()
    finally:
        conn.close()


def enqueue(device_id: str, payload: Dict[str, Any], spool_path: str = DEFAULT_SPOOL_PATH) -> int:
    created_at_ms = _utc_ms()
    payload_json = json.dumps(payload, separators=(",", ":"), ensure_ascii=False)
    conn = sqlite3.connect(spool_path)
    try:
        cur = conn.cursor()
        cur.execute(
            """
            INSERT INTO spool_items(device_id, created_at_ms, status, attempts, next_attempt_at_ms, payload_json)
            VALUES (?, ?, 'PENDING', 0, 0, ?)
            """,
            (device_id, created_at_ms, payload_json),
        )
        conn.commit()
        return int(cur.lastrowid)
    finally:
        conn.close()


def claim_batch(
    limit: int = 50,
    spool_path: str = DEFAULT_SPOOL_PATH,
    now_ms: Optional[int] = None,
) -> List[SpoolItem]:
    now_ms = _utc_ms() if now_ms is None else now_ms
    conn = sqlite3.connect(spool_path)
    conn.isolation_level = None  # manual transactions
    try:
        conn.execute("BEGIN IMMEDIATE;")
        rows = conn.execute(
            """
            SELECT id, device_id, created_at_ms, payload_json, attempts
            FROM spool_items
            WHERE status = 'PENDING' AND next_attempt_at_ms <= ?
            ORDER BY id
            LIMIT ?
            """,
            (now_ms, limit),
        ).fetchall()
        ids = [r[0] for r in rows]
        if ids:
            qmarks = ",".join(["?"] * len(ids))
            conn.execute(
                f"UPDATE spool_items SET status='IN_PROGRESS' WHERE id IN ({qmarks});",
                ids,
            )
        conn.execute("COMMIT;")

        return [
            SpoolItem(
                id=int(r[0]),
                device_id=str(r[1]),
                created_at_ms=int(r[2]),
                payload_json=str(r[3]),
                attempts=int(r[4]),
            )
            for r in rows
        ]
    except Exception:
        try:
            conn.execute("ROLLBACK;")
        except Exception:  # pragma: no cover
            pass  # pragma: no cover
        raise
    finally:
        conn.close()


def mark_done(item_id: int, spool_path: str = DEFAULT_SPOOL_PATH) -> None:
    conn = sqlite3.connect(spool_path)
    try:
        conn.execute(
            "UPDATE spool_items SET status='DONE', last_error=NULL WHERE id=?;",
            (item_id,),
        )
        conn.commit()
    finally:
        conn.close()


def mark_failed(
    item_id: int,
    error_message: str,
    attempts: int,
    spool_path: str = DEFAULT_SPOOL_PATH,
    max_attempts: int = 10,
) -> None:
    # Exponential-ish backoff, capped.
    next_delay_ms = min(60_000, (2 ** min(attempts, 10)) * 250)
    next_attempt_at_ms = _utc_ms() + next_delay_ms
    new_status = "DEAD" if attempts + 1 >= max_attempts else "PENDING"

    conn = sqlite3.connect(spool_path)
    try:
        conn.execute(
            """
            UPDATE spool_items
            SET status=?, attempts=attempts+1, next_attempt_at_ms=?, last_error=?
            WHERE id=?;
            """,
            (new_status, next_attempt_at_ms, error_message[:2000], item_id),
        )
        conn.commit()
    finally:
        conn.close()


def pending_counts(spool_path: str = DEFAULT_SPOOL_PATH) -> Dict[str, int]:
    conn = sqlite3.connect(spool_path)
    try:
        rows = conn.execute(
            "SELECT status, COUNT(*) FROM spool_items GROUP BY status;"
        ).fetchall()
        return {str(s): int(c) for (s, c) in rows}
    finally:
        conn.close()
