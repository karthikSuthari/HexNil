import sqlite3

conn = sqlite3.connect('sync_data/hexnil_battery_db')
cur = conn.cursor()

# Check field population rates
cur.execute("SELECT COUNT(*) FROM crash_events WHERE exceptionType IS NOT NULL AND exceptionType != ''")
exc_count = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM crash_events WHERE stackTraceHash IS NOT NULL AND stackTraceHash != ''")
hash_count = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM crash_events WHERE pss > 0")
pss_count = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM crash_events WHERE reason IS NOT NULL AND reason != ''")
reason_count = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM crash_events WHERE description IS NOT NULL AND description != ''")
desc_count = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM crash_events")
total = cur.fetchone()[0]

print(f"Total records: {total}")
print(f"exceptionType populated: {exc_count} ({100*exc_count/total:.1f}%)")
print(f"stackTraceHash populated: {hash_count} ({100*hash_count/total:.1f}%)")
print(f"pss > 0: {pss_count} ({100*pss_count/total:.1f}%)")
print(f"reason populated: {reason_count} ({100*reason_count/total:.1f}%)")
print(f"description populated: {desc_count} ({100*desc_count/total:.1f}%)")

# Check crashType distribution
print("\ncrashType distribution:")
cur.execute("SELECT crashType, COUNT(*) as cnt FROM crash_events GROUP BY crashType ORDER BY cnt DESC")
for row in cur.fetchall():
    print(f"  {row[0]}: {row[1]}")

# Check apps with most crashes
print("\nTop 5 crashing apps:")
cur.execute("SELECT packageName, appLabel, COUNT(*) as cnt FROM crash_events GROUP BY packageName ORDER BY cnt DESC LIMIT 5")
for row in cur.fetchall():
    print(f"  {row[0]} ({row[1]}): {row[2]} crashes")

# Show a record with non-empty exceptionType if any
cur.execute("SELECT * FROM crash_events WHERE exceptionType IS NOT NULL AND exceptionType != '' LIMIT 1")
row = cur.fetchone()
if row:
    cols = [desc[0] for desc in cur.description]
    print("\nSample record with exceptionType:")
    for i, col in enumerate(cols):
        val = row[i]
        if val is not None and isinstance(val, str) and len(str(val)) > 60:
            val = str(val)[:60] + '...'
        print(f"  {col}: {val}")
else:
    print("\nNo records with exceptionType populated")

conn.close()
