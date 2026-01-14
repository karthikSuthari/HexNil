@echo off
echo ============================================================
echo      HexNil Auto-Sync - Device Connection Monitor
echo ============================================================
echo.
echo This will automatically sync data when your mobile is connected.
echo Press Ctrl+C to stop.
echo.

cd /d "%~dp0"

REM Activate virtual environment and run auto-sync
call .venv\Scripts\activate.bat
python hexnil_auto_sync.py

pause
