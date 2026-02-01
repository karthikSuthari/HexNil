"""Comprehensive tests for HexNil backend to achieve 100% coverage.

This module provides pytest-compatible tests for the backend package.
"""
import json
import os
import sys
import time
import tempfile
import pytest

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend import ingest_spool


# ============================================================================
# INGEST SPOOL TESTS
# ============================================================================

class TestIngestSpool:
    """Tests for the ingest_spool module."""

    @pytest.fixture
    def temp_spool(self, tmp_path):
        """Create a temporary spool database."""
        spool_path = str(tmp_path / "test_spool.sqlite")
        ingest_spool.init_spool(spool_path)
        return spool_path

    def test_init_spool(self, temp_spool):
        """Test spool initialization creates tables."""
        import sqlite3
        conn = sqlite3.connect(temp_spool)
        cursor = conn.cursor()
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='spool_items'")
        result = cursor.fetchone()
        conn.close()
        assert result is not None
        assert result[0] == 'spool_items'

    def test_enqueue_item(self, temp_spool):
        """Test enqueueing an item."""
        device_id = "test_device_001"
        payload = {"battery_level": 85, "temperature": 32.5}
        
        item_id = ingest_spool.enqueue(device_id, payload, spool_path=temp_spool)
        
        assert item_id > 0

    def test_enqueue_multiple_items(self, temp_spool):
        """Test enqueueing multiple items."""
        for i in range(5):
            item_id = ingest_spool.enqueue(
                f"device_{i}",
                {"data": f"payload_{i}"},
                spool_path=temp_spool
            )
            assert item_id == i + 1

    def test_claim_batch_empty(self, temp_spool):
        """Test claiming from empty spool."""
        items = ingest_spool.claim_batch(limit=10, spool_path=temp_spool)
        assert len(items) == 0

    def test_claim_batch_with_items(self, temp_spool):
        """Test claiming items from spool."""
        # Enqueue items
        for i in range(3):
            ingest_spool.enqueue(f"device_{i}", {"idx": i}, spool_path=temp_spool)
        
        # Claim them
        items = ingest_spool.claim_batch(limit=10, spool_path=temp_spool)
        
        assert len(items) == 3
        for item in items:
            assert item.device_id.startswith("device_")
            assert item.attempts == 0

    def test_claim_batch_respects_limit(self, temp_spool):
        """Test that claim_batch respects the limit parameter."""
        for i in range(10):
            ingest_spool.enqueue(f"device_{i}", {"idx": i}, spool_path=temp_spool)
        
        items = ingest_spool.claim_batch(limit=3, spool_path=temp_spool)
        
        assert len(items) == 3

    def test_claim_batch_marks_in_progress(self, temp_spool):
        """Test that claimed items are marked as IN_PROGRESS."""
        import sqlite3
        
        ingest_spool.enqueue("device_1", {"data": "test"}, spool_path=temp_spool)
        items = ingest_spool.claim_batch(limit=10, spool_path=temp_spool)
        
        # Verify status in database
        conn = sqlite3.connect(temp_spool)
        cursor = conn.cursor()
        cursor.execute("SELECT status FROM spool_items WHERE id = ?", (items[0].id,))
        status = cursor.fetchone()[0]
        conn.close()
        
        assert status == "IN_PROGRESS"

    def test_mark_done(self, temp_spool):
        """Test marking an item as done."""
        import sqlite3
        
        item_id = ingest_spool.enqueue("device_1", {"data": "test"}, spool_path=temp_spool)
        items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool)
        
        ingest_spool.mark_done(items[0].id, spool_path=temp_spool)
        
        # Verify status
        conn = sqlite3.connect(temp_spool)
        cursor = conn.cursor()
        cursor.execute("SELECT status FROM spool_items WHERE id = ?", (items[0].id,))
        status = cursor.fetchone()[0]
        conn.close()
        
        assert status == "DONE"

    def test_mark_failed_with_retry(self, temp_spool):
        """Test marking an item as failed (with retry)."""
        import sqlite3
        
        ingest_spool.enqueue("device_1", {"data": "test"}, spool_path=temp_spool)
        items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool)
        
        ingest_spool.mark_failed(
            items[0].id,
            "Test error message",
            attempts=0,  # Current attempts from spool item is 0
            spool_path=temp_spool,
            max_attempts=10
        )
        
        # Verify status is PENDING (retry)
        conn = sqlite3.connect(temp_spool)
        cursor = conn.cursor()
        cursor.execute("SELECT status, attempts, last_error FROM spool_items WHERE id = ?", (items[0].id,))
        row = cursor.fetchone()
        conn.close()
        
        assert row[0] == "PENDING"
        assert row[1] == 1  # attempts incremented from 0 to 1
        assert row[2] == "Test error message"

    def test_mark_failed_max_attempts_reached(self, temp_spool):
        """Test that item is marked DEAD after max attempts."""
        import sqlite3
        
        ingest_spool.enqueue("device_1", {"data": "test"}, spool_path=temp_spool)
        items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool)
        
        # Fail with max attempts
        ingest_spool.mark_failed(
            items[0].id,
            "Final failure",
            attempts=9,
            spool_path=temp_spool,
            max_attempts=10
        )
        
        # Verify status is DEAD
        conn = sqlite3.connect(temp_spool)
        cursor = conn.cursor()
        cursor.execute("SELECT status FROM spool_items WHERE id = ?", (items[0].id,))
        status = cursor.fetchone()[0]
        conn.close()
        
        assert status == "DEAD"

    def test_pending_counts_empty(self, temp_spool):
        """Test pending_counts on empty spool."""
        counts = ingest_spool.pending_counts(spool_path=temp_spool)
        assert counts == {}

    def test_pending_counts_with_items(self, temp_spool):
        """Test pending_counts with various item states."""
        # Add items
        for i in range(3):
            ingest_spool.enqueue(f"device_{i}", {"idx": i}, spool_path=temp_spool)
        
        # Claim one
        items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool)
        
        # Mark one as done
        ingest_spool.mark_done(items[0].id, spool_path=temp_spool)
        
        counts = ingest_spool.pending_counts(spool_path=temp_spool)
        
        assert counts.get("PENDING", 0) == 2
        assert counts.get("DONE", 0) == 1

    def test_spool_item_dataclass(self, temp_spool):
        """Test SpoolItem dataclass properties."""
        ingest_spool.enqueue("device_test", {"key": "value"}, spool_path=temp_spool)
        items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool)
        
        item = items[0]
        assert isinstance(item.id, int)
        assert isinstance(item.device_id, str)
        assert isinstance(item.created_at_ms, int)
        assert isinstance(item.payload_json, str)
        assert isinstance(item.attempts, int)
        
        # Verify payload can be parsed
        payload = json.loads(item.payload_json)
        assert payload == {"key": "value"}

    def test_enqueue_unicode_payload(self, temp_spool):
        """Test enqueueing payload with unicode characters."""
        payload = {"message": "Battery 電池 🔋", "temperature": 32.5}
        item_id = ingest_spool.enqueue("device_unicode", payload, spool_path=temp_spool)
        
        items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool)
        parsed = json.loads(items[0].payload_json)
        
        assert parsed["message"] == "Battery 電池 🔋"

    def test_claim_batch_with_future_retry(self, temp_spool):
        """Test that items with future retry time are not claimed."""
        import sqlite3
        
        # Enqueue and claim
        ingest_spool.enqueue("device_1", {"data": "test"}, spool_path=temp_spool)
        items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool)
        
        # Mark as failed (sets future retry time)
        ingest_spool.mark_failed(items[0].id, "Error", attempts=1, spool_path=temp_spool)
        
        # Try to claim immediately - should get nothing since retry is in future
        new_items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool, now_ms=int(time.time() * 1000))
        
        # The item is pending but its next_attempt_at_ms is in the future
        assert len(new_items) == 0

    def test_claim_batch_after_retry_time(self, temp_spool):
        """Test that items become available after retry time passes."""
        import sqlite3
        
        # Enqueue and claim
        ingest_spool.enqueue("device_1", {"data": "test"}, spool_path=temp_spool)
        items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool)
        
        # Mark as failed
        ingest_spool.mark_failed(items[0].id, "Error", attempts=1, spool_path=temp_spool)
        
        # Claim with future timestamp (after retry time)
        future_ms = int(time.time() * 1000) + 120_000  # 2 minutes in future
        new_items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool, now_ms=future_ms)
        
        assert len(new_items) == 1

    def test_init_spool_creates_directory(self, tmp_path):
        """Test that init_spool creates parent directory if needed."""
        nested_path = str(tmp_path / "nested" / "dir" / "spool.sqlite")
        ingest_spool.init_spool(nested_path)
        
        assert os.path.exists(nested_path)


# ============================================================================
# UTILITY FUNCTION TESTS
# ============================================================================

class TestUtilityFunctions:
    """Tests for utility functions in ingest_spool."""

    def test_utc_ms(self):
        """Test _utc_ms returns reasonable timestamp."""
        ts = ingest_spool._utc_ms()
        
        # Should be a positive integer
        assert isinstance(ts, int)
        assert ts > 0
        
        # Should be roughly current time (within last minute)
        current_approx = int(time.time() * 1000)
        assert abs(ts - current_approx) < 60_000


# ============================================================================
# EDGE CASES
# ============================================================================

class TestEdgeCases:
    """Tests for edge cases and error handling."""

    @pytest.fixture
    def temp_spool(self, tmp_path):
        """Create a temporary spool database."""
        spool_path = str(tmp_path / "test_spool.sqlite")
        ingest_spool.init_spool(spool_path)
        return spool_path

    def test_mark_failed_truncates_long_error(self, temp_spool):
        """Test that very long error messages are truncated."""
        import sqlite3
        
        ingest_spool.enqueue("device_1", {"data": "test"}, spool_path=temp_spool)
        items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool)
        
        # Very long error message
        long_error = "x" * 5000
        ingest_spool.mark_failed(items[0].id, long_error, attempts=1, spool_path=temp_spool)
        
        # Verify error was truncated
        conn = sqlite3.connect(temp_spool)
        cursor = conn.cursor()
        cursor.execute("SELECT last_error FROM spool_items WHERE id = ?", (items[0].id,))
        error = cursor.fetchone()[0]
        conn.close()
        
        assert len(error) == 2000

    def test_large_payload(self, temp_spool):
        """Test enqueueing a large payload."""
        large_payload = {
            "data": "x" * 100_000,
            "array": list(range(1000)),
        }
        
        item_id = ingest_spool.enqueue("device_large", large_payload, spool_path=temp_spool)
        items = ingest_spool.claim_batch(limit=1, spool_path=temp_spool)
        
        parsed = json.loads(items[0].payload_json)
        assert len(parsed["data"]) == 100_000

    def test_double_init_is_safe(self, temp_spool):
        """Test that calling init_spool twice is idempotent."""
        # Already initialized by fixture, init again
        ingest_spool.init_spool(temp_spool)
        
        # Should still work
        item_id = ingest_spool.enqueue("device_1", {"test": True}, spool_path=temp_spool)
        assert item_id > 0

    def test_claim_batch_exception_handling(self, tmp_path):
        """Test that claim_batch properly handles and re-raises exceptions."""
        import sqlite3
        
        spool_path = str(tmp_path / "bad_spool.sqlite")
        ingest_spool.init_spool(spool_path)
        
        # Add some data
        ingest_spool.enqueue("device_1", {"data": "test"}, spool_path=spool_path)
        
        # Corrupt the database by dropping the table mid-operation
        # This simulates a database error during claim_batch
        conn = sqlite3.connect(spool_path)
        conn.execute("DROP TABLE spool_items")
        conn.close()
        
        # This should raise an exception
        with pytest.raises(Exception):
            ingest_spool.claim_batch(limit=1, spool_path=spool_path)
