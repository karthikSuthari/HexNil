"""Tests for backend/__init__.py module."""
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def test_backend_package_import():
    """Test that backend package can be imported."""
    import backend
    assert backend is not None


def test_backend_docstring():
    """Test that backend package has docstring."""
    import backend
    assert backend.__doc__ is not None
