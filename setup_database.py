"""Apply HexNil MySQL schema locally.

Usage (PowerShell):
  $env:HEXNIL_SETUP_USER='root'
  $env:HEXNIL_SETUP_PASSWORD='your_root_password'
  python setup_database.py

This script executes backend/hexnil_mysql_schema.sql against your local MySQL.
"""

from __future__ import annotations

import os
import sys
import getpass
from pathlib import Path

try:
    import mysql.connector
    from mysql.connector import Error as MySQLError
except ModuleNotFoundError as e:
    # Most common cause: running with a different interpreter than the workspace venv.
    print(f"ERROR: Missing dependency in this Python: {e}")
    print(f"Python executable: {sys.executable}")
    print("\nFix:")
    print("- Run with the workspace venv: .\\.venv\\Scripts\\python.exe .\\setup_database.py")
    print("- Or install into this interpreter: python -m pip install mysql-connector-python")
    raise SystemExit(2)


SCHEMA_PATH = Path(__file__).parent / "backend" / "hexnil_mysql_schema.sql"

HOST = os.getenv("HEXNIL_DB_HOST", "localhost")
PORT = int(os.getenv("HEXNIL_DB_PORT", "3306"))
USER = os.getenv("HEXNIL_SETUP_USER", "root")
PASSWORD = os.getenv("HEXNIL_SETUP_PASSWORD", "")


def main() -> int:
    if not SCHEMA_PATH.exists():
        print(f"Schema file not found: {SCHEMA_PATH}")
        return 2

    password = PASSWORD
    if not password:
        # Avoid storing credentials in env vars; prompt interactively when possible.
        if sys.stdin is not None and sys.stdin.isatty():
            password = getpass.getpass(prompt=f"MySQL password for {USER}@{HOST}:{PORT}: ")
        elif USER and USER != "root":
            print("HEXNIL_SETUP_PASSWORD is required for non-root users (no TTY available to prompt)")
            return 2

    sql = SCHEMA_PATH.read_text(encoding="utf-8")

    try:
        conn = mysql.connector.connect(host=HOST, port=PORT, user=USER, password=password)
        cur = conn.cursor(buffered=True)


                # Execute statements one by one (Windows-safe)
        statements = [s.strip() for s in sql.split(";") if s.strip()]

        for stmt in statements:
            cur.execute(stmt)


        conn.commit()
        cur.close()
        conn.close()

        print("OK: schema applied")
        print("Created/updated DB: hexnil")
        print("Users: hexnil_backend, hexnil_ai")
        return 0
    except MySQLError as e:
        print(f"ERROR applying schema: {e}")
        print("Tip: ensure MySQL is running and credentials are correct.")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
