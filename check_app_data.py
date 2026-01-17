import sqlite3
import os

# Check pulled hexnil db
if os.path.exists('pulled_hexnil_db.sqlite'):
    print("=== Pulled HexNil DB (Room DB from Android) ===")
    conn = sqlite3.connect('pulled_hexnil_db.sqlite')
    cur = conn.cursor()
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
    tables = [r[0] for r in cur.fetchall()]
    print("All tables:")
    for table in tables:
        try:
            cur.execute(f"SELECT COUNT(*) FROM [{table}]")
            count = cur.fetchone()[0]
            print(f"  {table}: {count} records")
        except Exception as e:
            print(f"  {table}: error - {e}")
    conn.close()

# Check spool db
if os.path.exists('backend/hexnil_ingest_spool.sqlite'):
    print("\n=== Ingest Spool DB ===")
    conn = sqlite3.connect('backend/hexnil_ingest_spool.sqlite')
    cur = conn.cursor()
    cur.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = [r[0] for r in cur.fetchall()]
    print(f"Tables: {tables}")
    
    for table in tables:
        try:
            cur.execute(f"SELECT COUNT(*) FROM {table}")
            count = cur.fetchone()[0]
            print(f"  {table}: {count} records")
        except:
            pass
    conn.close()

# Check MySQL
print("\n=== MySQL hexnil DB ===")
try:
    import mysql.connector
    conn = mysql.connector.connect(
        host='localhost',
        port=3306,
        database='hexnil',
        user='hexnil_backend',
        password='HexNil_Backend_2026!'
    )
    cur = conn.cursor()
    
    # Check app intelligence tables
    tables = ['ai_app_registry', 'ai_app_usage_sessions', 'ai_app_battery_attribution', 
              'ai_app_thermal_impact', 'ai_app_anomalies']
    for table in tables:
        cur.execute(f"SELECT COUNT(*) FROM {table}")
        count = cur.fetchone()[0]
        print(f"  {table}: {count} records")
    conn.close()
except Exception as e:
    print(f"Error: {e}")
