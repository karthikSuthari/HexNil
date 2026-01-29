import sqlite3
import os

db_path = 'hexnil_battery_db.sqlite'
if not os.path.exists(db_path):
    print(f"Error: {db_path} not found")
    exit(1)

conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

try:
    cursor.execute("SELECT temperature, voltage, currentNow, isCharging, chargingType FROM battery_snapshots LIMIT 20")
    rows = cursor.fetchall()
    
    print(f"{'Temp':<6} | {'Volt':<6} | {'CurrNow':<8} | {'isChar':<6} | {'CharType'}")
    print("-" * 50)
    for row in rows:
        print(f"{row['temperature']:<6} | {row['voltage']:<6} | {row[2]:<8} | {row['isCharging']:<6} | {row['chargingType']}")
except sqlite3.Error as e:
    print(f"SQLite error: {e}")
finally:
    conn.close()
