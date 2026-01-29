-- Migration: Three-state data model (NULL = UNAVAILABLE)
-- Run after hexnil_mysql_schema.sql. Ensures battery_snapshots preserves NULL
-- for screen_on, current_now; adds snapshot context columns.

USE hexnil;

-- Allow NULL for three-state columns (no coercion to 0)
ALTER TABLE battery_snapshots MODIFY COLUMN screen_on TINYINT(1) NULL COMMENT '1=on, 0=off, NULL=unavailable';
ALTER TABLE battery_snapshots MODIFY COLUMN current_now FLOAT NULL COMMENT 'µA; NULL if unsupported';

-- Add snapshot context metadata (run once; ignore "duplicate column" if re-run)
ALTER TABLE battery_snapshots ADD COLUMN snapshot_trigger VARCHAR(64) NULL COMMENT 'FOREGROUND|PERIODIC|SCREEN_ON|...';
ALTER TABLE battery_snapshots ADD COLUMN data_source VARCHAR(32) NULL COMMENT 'EVENT|POLLED|INFERRED';
ALTER TABLE battery_snapshots ADD COLUMN confidence FLOAT NULL COMMENT '0.0-1.0';

SELECT 'Three-state migration applied.' AS status;
