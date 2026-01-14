@echo off
setlocal
title HexNil Pipeline

echo Starting HexNil backend + AI pipeline...
echo.

REM Start backend server
start "HexNil Backend" cmd /k "python run_hexnil_backend.py"

REM Start AI pipeline
start "HexNil AI" cmd /k "python run_hexnil_ai.py"

echo Done.
echo - Backend: http://localhost:8000/docs
echo - Health:  http://localhost:8000/health
echo.
endlocal
