import mysql.connector
import json

# Connection settings
DB_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': '248r1a05aq',
    'database': 'hexnil'
}

def apply_performance_schema():
    print("HexNil Performance Schema Updater (v3.5)")
    print("=" * 40)
    
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        print("✓ Connected to MySQL")

        # 1. CPU/GPU Load Samples (from previous task)
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS cpu_gpu_load_samples (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            device_id VARCHAR(64) NOT NULL,
            timestamp BIGINT NOT NULL,
            app_name VARCHAR(128),
            package_name VARCHAR(256),
            pid INT,
            uid INT,
            is_system_app BOOLEAN,
            system_cpu_percent INT,
            app_cpu_percent INT,
            gpu_percent INT,
            is_gpu_available BOOLEAN,
            load_1m FLOAT,
            load_5m FLOAT,
            load_15m FLOAT,
            runnable_processes INT,
            temperature FLOAT,
            battery_level INT,
            drain_rate_per_hour FLOAT,
            is_throttled BOOLEAN,
            throttle_level INT,
            thermal_status INT,
            stress_score FLOAT,
            stress_category VARCHAR(16),
            stress_explanation TEXT,
            inferred_load VARCHAR(16),
            confidence_level VARCHAR(16),
            detection_method VARCHAR(64),
            observed_signals TEXT,
            source_type VARCHAR(16),
            validation_status VARCHAR(16),
            validation_notes TEXT,
            is_active BOOLEAN DEFAULT TRUE,
            received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_device_timestamp (device_id, timestamp),
            FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
        ) ENGINE=InnoDB;
        """)
        print("✓ Table: cpu_gpu_load_samples")

        # 2. Memory Snapshots
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS memory_snapshots (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            device_id VARCHAR(64) NOT NULL,
            timestamp BIGINT NOT NULL,
            received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            total_memory_bytes BIGINT,
            available_memory_bytes BIGINT,
            used_memory_bytes BIGINT,
            used_memory_percent FLOAT,
            is_low_memory BOOLEAN DEFAULT FALSE,
            threshold BIGINT,
            running_app_count INT,
            top_memory_app VARCHAR(256),
            top_memory_app_bytes BIGINT,
            battery_level INT,
            temperature FLOAT,
            is_charging BOOLEAN DEFAULT FALSE,
            is_screen_on BOOLEAN DEFAULT FALSE,
            payload JSON,
            INDEX idx_device_timestamp (device_id, timestamp),
            FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
        ) ENGINE=InnoDB;
        """)
        print("✓ Table: memory_snapshots")

        # 3. App Launch Metrics
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS app_launch_metrics (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            device_id VARCHAR(64) NOT NULL,
            timestamp BIGINT NOT NULL,
            received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            package_name VARCHAR(256),
            app_label VARCHAR(128),
            launch_time_ms FLOAT,
            launch_type VARCHAR(16),
            is_slow_launch BOOLEAN DEFAULT FALSE,
            app_version VARCHAR(32),
            android_version VARCHAR(32),
            battery_level INT,
            temperature FLOAT,
            payload JSON,
            INDEX idx_device_timestamp (device_id, timestamp),
            FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
        ) ENGINE=InnoDB;
        """)
        print("✓ Table: app_launch_metrics")

        # 4. Frame Metrics
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS frame_metrics (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            device_id VARCHAR(64) NOT NULL,
            timestamp BIGINT NOT NULL,
            received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            package_name VARCHAR(256),
            window_name VARCHAR(256),
            total_frames INT,
            slow_frames INT,
            frozen_frames INT,
            jank_percent FLOAT,
            avg_fps FLOAT,
            duration_ms BIGINT,
            is_low_memory_during_session BOOLEAN DEFAULT FALSE,
            max_frame_time_ms FLOAT,
            payload JSON,
            INDEX idx_device_timestamp (device_id, timestamp),
            FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
        ) ENGINE=InnoDB;
        """)
        print("✓ Table: frame_metrics")

        # 5. Crash Events
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS crash_events (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            device_id VARCHAR(64) NOT NULL,
            timestamp BIGINT NOT NULL,
            received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            package_name VARCHAR(256),
            process_name VARCHAR(256),
            reason VARCHAR(256),
            short_msg TEXT,
            long_msg TEXT,
            stack_trace_hash VARCHAR(64),
            is_foreground BOOLEAN DEFAULT FALSE,
            app_version VARCHAR(32),
            payload JSON,
            INDEX idx_device_timestamp (device_id, timestamp),
            FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
        ) ENGINE=InnoDB;
        """)
        print("✓ Table: crash_events")

        # 6. ANR Events
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS anr_events (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            device_id VARCHAR(64) NOT NULL,
            timestamp BIGINT NOT NULL,
            received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            package_name VARCHAR(256),
            process_name VARCHAR(256),
            reason VARCHAR(256),
            error_msg TEXT,
            stack_trace_hash VARCHAR(64),
            is_foreground BOOLEAN DEFAULT FALSE,
            app_version VARCHAR(32),
            payload JSON,
            INDEX idx_device_timestamp (device_id, timestamp),
            FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
        ) ENGINE=InnoDB;
        """)
        print("✓ Table: anr_events")

        # 7. Daily Aggregates
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS daily_aggregates (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            device_id VARCHAR(64) NOT NULL,
            aggregate_date DATE NOT NULL,
            package_name VARCHAR(256),
            app_label VARCHAR(128),
            received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            avg_memory_used_percent FLOAT,
            max_memory_used_percent FLOAT,
            low_memory_event_count INT,
            launch_count INT,
            cold_launch_count INT,
            avg_cold_launch_ms FLOAT,
            slow_launch_count INT,
            crash_count INT,
            anr_count INT,
            unique_crash_types INT,
            avg_temp FLOAT,
            max_temp FLOAT,
            total_battery_drain FLOAT,
            avg_drain_rate_per_hour FLOAT,
            health_score FLOAT,
            health_grade VARCHAR(4),
            app_version VARCHAR(32),
            payload JSON,
            INDEX idx_device_date (device_id, aggregate_date),
            UNIQUE KEY uk_device_date_pkg (device_id, aggregate_date, package_name),
            FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
        ) ENGINE=InnoDB;
        """)
        print("✓ Table: daily_aggregates")

        # 8. Regression Events
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS regression_events (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            device_id VARCHAR(64) NOT NULL,
            timestamp BIGINT NOT NULL,
            received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            package_name VARCHAR(256),
            regression_type VARCHAR(32),
            severity VARCHAR(16),
            metric_name VARCHAR(64),
            current_value FLOAT,
            baseline_value FLOAT,
            deviation_percent FLOAT,
            is_resolved BOOLEAN DEFAULT FALSE,
            payload JSON,
            INDEX idx_device_timestamp (device_id, timestamp),
            FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
        ) ENGINE=InnoDB;
        """)
        print("✓ Table: regression_events")

        # Grant permissions to backend
        print("\nGranting permissions...")
        tables = [
            "cpu_gpu_load_samples", "memory_snapshots", "app_launch_metrics", 
            "frame_metrics", "crash_events", "anr_events", 
            "daily_aggregates", "regression_events"
        ]
        for table in tables:
            try:
                cursor.execute(f"GRANT SELECT, INSERT ON hexnil.{table} TO 'hexnil_backend'@'localhost'")
                cursor.execute(f"GRANT SELECT, INSERT ON hexnil.{table} TO 'hexnil_backend'@'%'")
            except:
                pass
        
        cursor.execute("FLUSH PRIVILEGES")
        conn.commit()
        
        print("\n✅ Performance schema applied successfully!")
        
        cursor.execute("SHOW TABLES")
        print("\nUpdated Table List:")
        for (table,) in cursor:
            print(f"  - {table}")
            
        cursor.close()
        conn.close()

    except Exception as e:
        print(f"\n✗ Error: {e}")

if __name__ == "__main__":
    apply_performance_schema()
