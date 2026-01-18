"""
HexNil Database Forensics - Zero Value Column Analysis
Analyzes Room DB for columns that are always 0 or NULL
"""
import sqlite3
import os
from collections import defaultdict

def analyze_room_db(db_path):
    """Analyze Room DB for zero-value columns."""
    if not os.path.exists(db_path):
        print(f"ERROR: Database not found: {db_path}")
        return
    
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # Get all tables
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite%' AND name NOT LIKE 'android%' AND name NOT LIKE 'room_%'")
    tables = [row[0] for row in cursor.fetchall()]
    
    print("=" * 80)
    print("HEXNIL ROOM DATABASE FORENSICS - ZERO VALUE ANALYSIS")
    print("=" * 80)
    print(f"\nDatabase: {db_path}")
    print(f"Tables: {len(tables)}\n")
    
    all_zero_columns = {}
    
    for table in sorted(tables):
        cursor.execute(f"SELECT COUNT(*) FROM [{table}]")
        row_count = cursor.fetchone()[0]
        
        if row_count == 0:
            print(f"\n{table}: [EMPTY - 0 rows]")
            continue
            
        # Get columns
        cursor.execute(f"PRAGMA table_info([{table}])")
        columns = [(row[1], row[2]) for row in cursor.fetchall()]
        
        print(f"\n{'=' * 60}")
        print(f"TABLE: {table} ({row_count} rows)")
        print(f"{'=' * 60}")
        
        zero_cols = []
        null_cols = []
        normal_cols = []
        
        for col_name, col_type in columns:
            # Check for all zeros
            cursor.execute(f"SELECT COUNT(*) FROM [{table}] WHERE [{col_name}] = 0")
            zero_count = cursor.fetchone()[0]
            
            # Check for all nulls
            cursor.execute(f"SELECT COUNT(*) FROM [{table}] WHERE [{col_name}] IS NULL")
            null_count = cursor.fetchone()[0]
            
            # Check for all empty strings
            cursor.execute(f"SELECT COUNT(*) FROM [{table}] WHERE [{col_name}] = ''")
            empty_count = cursor.fetchone()[0]
            
            # Get sample values
            cursor.execute(f"SELECT DISTINCT [{col_name}] FROM [{table}] WHERE [{col_name}] IS NOT NULL AND [{col_name}] != '' AND [{col_name}] != 0 LIMIT 3")
            samples = [str(row[0])[:50] for row in cursor.fetchall()]
            
            is_problematic = False
            issue = ""
            
            if zero_count == row_count and col_type.upper() in ('INTEGER', 'REAL', 'FLOAT', 'INT', 'DOUBLE'):
                issue = "ALL ZEROS"
                is_problematic = True
                zero_cols.append((col_name, col_type, issue))
            elif null_count == row_count:
                issue = "ALL NULL"
                is_problematic = True
                null_cols.append((col_name, col_type, issue))
            elif empty_count == row_count and col_type.upper() == 'TEXT':
                issue = "ALL EMPTY"
                is_problematic = True
                null_cols.append((col_name, col_type, issue))
            elif zero_count + null_count == row_count and row_count > 0:
                issue = "ONLY 0/NULL"
                is_problematic = True
                zero_cols.append((col_name, col_type, issue))
            else:
                normal_cols.append((col_name, col_type, samples))
                
            if is_problematic:
                if table not in all_zero_columns:
                    all_zero_columns[table] = []
                all_zero_columns[table].append((col_name, col_type, issue))
        
        # Print zero columns
        if zero_cols or null_cols:
            print(f"\n⚠️  ZERO/NULL COLUMNS ({len(zero_cols) + len(null_cols)}):")
            for col_name, col_type, issue in zero_cols + null_cols:
                print(f"   ❌ {col_name} ({col_type}): {issue}")
        
        # Print normal columns count
        if normal_cols:
            print(f"\n✅ POPULATED COLUMNS ({len(normal_cols)}):")
            for col_name, col_type, samples in normal_cols[:5]:  # Show first 5
                sample_str = ", ".join(samples[:2]) if samples else "mixed"
                print(f"   • {col_name} ({col_type}): {sample_str}")
            if len(normal_cols) > 5:
                print(f"   ... and {len(normal_cols) - 5} more")
    
    # Summary
    print("\n" + "=" * 80)
    print("ZERO VALUE SUMMARY")
    print("=" * 80)
    
    total_zero_cols = sum(len(v) for v in all_zero_columns.values())
    print(f"\nTotal problematic columns: {total_zero_cols}")
    
    for table, cols in sorted(all_zero_columns.items()):
        print(f"\n{table}:")
        for col_name, col_type, issue in cols:
            print(f"  - {col_name}: {issue}")
    
    conn.close()
    
    return all_zero_columns

if __name__ == "__main__":
    db_path = "pulled_hexnil_db.sqlite"
    if os.path.exists(db_path):
        analyze_room_db(db_path)
    else:
        print(f"Database not found at {db_path}")
        print("Looking for alternatives...")
        for f in os.listdir("."):
            if f.endswith(".db") or f.endswith(".sqlite"):
                print(f"  Found: {f}")
