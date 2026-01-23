import sqlite3
import os
import json
from datetime import datetime

def get_table_counts(db_path):
    if not os.path.exists(db_path):
        return {"error": f"File {db_path} not found"}
    
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
        tables = [row[0] for row in cursor.fetchall() if row[0] != 'sqlite_sequence' and not row[0].startswith('room_')]
        
        counts = {}
        for table in tables:
            try:
                cursor.execute(f"SELECT COUNT(*) FROM {table}")
                counts[table] = cursor.fetchone()[0]
            except Exception as e:
                counts[table] = f"Error: {e}"
        
        latest_samples = {}
        if 'cpu_gpu_load_samples' in tables:
            cursor.execute("SELECT * FROM cpu_gpu_load_samples ORDER BY id DESC LIMIT 5")
            columns = [description[0] for description in cursor.description]
            latest_samples['cpu_gpu_load_samples'] = [dict(zip(columns, row)) for row in cursor.fetchall()]
            
        conn.close()
        return {"tables": counts, "latest_samples": latest_samples}
    except Exception as e:
        return {"error": str(e)}

def main():
    print(f"Generating snapshots at {datetime.now().isoformat()}")
    
    battery_db = "hexnil_battery_db_final.sqlite"
    intel_db = "hexnil_app_intelligence_final.sqlite"
    
    snapshot = {
        "timestamp": datetime.now().isoformat(),
        "battery_db": get_table_counts(battery_db),
        "intel_db": get_table_counts(intel_db)
    }
    
    with open("db_snapshot_final.json", "w") as f:
        json.dump(snapshot, f, indent=4)
        
    print("Snapshot saved to db_snapshot_final.json")
    
    # Print summary
    print("\n--- Battery DB Summary ---")
    for b_table, count in snapshot['battery_db'].get('tables', {}).items():
        print(f"{b_table}: {count}")
        
    print("\n--- Intelligence DB Summary ---")
    for i_table, count in snapshot['intel_db'].get('tables', {}).items():
        print(f"{i_table}: {count}")

if __name__ == "__main__":
    main()
