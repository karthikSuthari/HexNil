"""Pytest configuration and fixtures for HexNil tests."""
import os
import sys
import pytest

# Ensure backend package is importable
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
