import sqlite3
import os

# The Room database synced from Android device
db_path = 'sync_data/hexnil_battery_db'
if os.path.exists(db_path):
    print(f'Room Database: {db_path}')
    print(f'Size: {os.path.getsize(db_path):,} bytes')
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    
    cursor = conn.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
    tables = [row[0] for row in cursor.fetchall()]
    print(f'Total tables: {len(tables)}')
    
    target_tables = ['calibration_data', 'ai_analysis_results', 'regression_events', 'daily_aggregates']
    
    for table in target_tables:
        if table in tables:
            sep = '=' * 80
            print(f'\n{sep}')
            print(f'TABLE: {table}')
            print(sep)
            
            cursor = conn.execute(f'SELECT COUNT(*) as cnt FROM {table}')
            count = cursor.fetchone()['cnt']
            print(f'Total records: {count}')
            
            # Get column info
            cursor = conn.execute(f'PRAGMA table_info({table})')
            columns = [row['name'] for row in cursor.fetchall()]
            print(f'Columns: {len(columns)}')
            
            if count > 0:
                cursor = conn.execute(f'SELECT * FROM {table} ORDER BY id DESC LIMIT 2')
                rows = cursor.fetchall()
                
                for i, row in enumerate(rows):
                    print(f'\n--- Record {i+1} (id={row["id"]}) ---')
                    populated = 0
                    empty = 0
                    for key in row.keys():
                        val = row[key]
                        if val is not None and val != 0 and val != '' and val != 0.0:
                            populated += 1
                            if isinstance(val, str) and len(val) > 60:
                                val = val[:60] + '...'
                            print(f'  {key}: {val}')
                        else:
                            empty += 1
                    print(f'\n  [Populated: {populated}/{len(columns)}, Empty: {empty}]')
        else:
            print(f'\nTable {table} not found')
    
    conn.close()
else:
    print(f'Database not found at {db_path}')
