import sqlite3
import os

db_path = "sync_data/db_copy.sqlite"

print(f"Checking database: {db_path}")
print(f"File size: {os.path.getsize(db_path):,} bytes\n")

try:
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # Check cpu_gpu_load_samples table
    cursor.execute("SELECT COUNT(*) FROM cpu_gpu_load_samples")
    count = cursor.fetchone()[0]
    print(f"cpu_gpu_load_samples: {count} rows")
    
    if count > 0:
        print("\nLatest 5 samples:")
        cursor.execute("""
            SELECT id, timestamp, systemCpuPercent, gpuPercent, 
                   stressScore, stressCategory, temperature, batteryLevel
            FROM cpu_gpu_load_samples 
            ORDER BY id DESC LIMIT 5
        """)
        for row in cursor.fetchall():
            print(f"  id={row[0]}, cpu={row[2]}%, gpu={row[3]}%, "
                  f"stress={row[4]} ({row[5]}), temp={row[6]}C, batt={row[7]}%")
    
    # Check battery_snapshots for cpuLoadTotal values
    print("\n\nLatest 5 battery_snapshots (cpuLoadTotal, gpuLoad):")
    cursor.execute("""
        SELECT id, timestamp, cpuLoadTotal, gpuLoad, temperature, level
        FROM battery_snapshots 
        ORDER BY id DESC LIMIT 5
    """)
    for row in cursor.fetchall():
        print(f"  id={row[0]}, cpuLoadTotal={row[2]}, gpuLoad={row[3]}, "
              f"temp={row[4]}, level={row[5]}%")
    
    conn.close()
except Exception as e:
    print(f"Error: {e}")
