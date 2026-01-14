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
    calibration_type: str = Field(..., min_length=1, max_length=64)
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
