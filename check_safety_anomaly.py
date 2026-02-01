import sqlite3

conn = sqlite3.connect('sync_data/hexnil_battery_db')
cur = conn.cursor()

# Check safety_events
cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='safety_events'")
if cur.fetchone():
    cur.execute('SELECT COUNT(*) FROM safety_events')
    count = cur.fetchone()[0]
    print(f'=== SAFETY_EVENTS: {count} records ===')
    if count > 0:
        cur.execute('SELECT * FROM safety_events ORDER BY timestamp DESC LIMIT 2')
        rows = cur.fetchall()
        cols = [desc[0] for desc in cur.description]
        for row in rows:
            print()
            for i, col in enumerate(cols):
                val = row[i]
                if val is not None and isinstance(val, str) and len(str(val)) > 50:
                    val = str(val)[:50] + '...'
                print(f'  {col}: {val}')
    else:
        print('  (no records)')
else:
    print('safety_events table NOT FOUND')

print()
print('=' * 60)
print()

# Check anomaly_records
cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='anomaly_records'")
if cur.fetchone():
    cur.execute('SELECT COUNT(*) FROM anomaly_records')
    count = cur.fetchone()[0]
    print(f'=== ANOMALY_RECORDS: {count} records ===')
    if count > 0:
        cur.execute('SELECT * FROM anomaly_records ORDER BY detectedAt DESC LIMIT 2')
        rows = cur.fetchall()
        cols = [desc[0] for desc in cur.description]
        for row in rows:
            print()
            for i, col in enumerate(cols):
                val = row[i]
                if val is not None and isinstance(val, str) and len(str(val)) > 50:
                    val = str(val)[:50] + '...'
                print(f'  {col}: {val}')
    else:
        print('  (no records)')
else:
    print('anomaly_records table NOT FOUND')

conn.close()
