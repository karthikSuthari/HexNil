# HexNil Three-State Data Model & Zero-Value Fix

## 1. Root-Cause Explanation: Why 0 Appeared Everywhere

HexNil stored **0** across many columns (`screenOn`, `wifiEnabled`, `mobileDataEnabled`, `cpuLoad`, `gpuLoad`, `currentNow`, `energyCounter`, etc.). These 0s were **not** always "false" or "measured zero". Root causes:

| Cause | Examples | Effect |
|-------|----------|--------|
| **Android refused access** | Background restrictions, Doze, permission/API limits | Polling `PowerManager.isInteractive()`, `WifiManager.isWifiEnabled()`, `ConnectivityManager.getActiveNetworkInfo()` returns false / empty; we stored 0. |
| **Metric unavailable on device/OEM** | No energy counter, no current sensor, no GPU API | `BatteryManager` returns 0 or `Long.MIN_VALUE`; we stored 0. |
| **Snapshot at wrong lifecycle timing** | WorkManager/Doze, app backgrounded | System state APIs unreliable; we captured "false" and stored 0. |
| **Schema forced NOT NULL** | All booleans → `INTEGER NOT NULL`, primitives `int`/`long` | No way to represent "unknown"; we collapsed UNAVAILABLE into 0. |
| **Collector never populated** | `wifiEnabled`, `mobileDataEnabled`, `bluetoothEnabled`, `gpsEnabled` | Fields exist in entity but were **never set**; default false → 0. |
| **Polling instead of events** | Screen/network read at sample time via APIs | In background, these often report "off" or "none"; we stored 0. |

**Canonical meaning of 0 (after fix):**

- **0** = **FALSE / INACTIVE / MEASURED ZERO** (we reliably know it is off or zero).
- **1** = **TRUE / ACTIVE / MEASURED POSITIVE**.
- **NULL** (or **-1** where documented) = **UNAVAILABLE / RESTRICTED / BLOCKED** (Android blocked access, OEM unsupported, or we did not have a confident reading).

---

## 2. Three-State Data Model (Mandatory)

For every such metric:

| Value | Meaning |
|-------|---------|
| **1** | TRUE / ACTIVE / MEASURED POSITIVE |
| **0** | FALSE / INACTIVE / MEASURED ZERO |
| **NULL** or **-1** | UNAVAILABLE / RESTRICTED / BLOCKED |

**Do not collapse these.** Never use 0 as a stand-in for "unknown".

---

## 3. Schema Migration Plan (Room + SQLite)

### 3.1 Columns That Must Allow NULL

**BatterySnapshot (and equivalent):**

- `screenOn` – 1/0/NULL (was `INTEGER NOT NULL` from boolean).
- `wifiEnabled`, `mobileDataEnabled`, `bluetoothEnabled`, `gpsEnabled` – 1/0/NULL.
- `currentNow`, `currentAverage` – NULL when unsupported (e.g. no current sensor).
- `energyCounter`, `chargeCounter` – NULL when unsupported.
- `cpuLoadTotal`, `gpuLoad` – NULL when blocked or unavailable (no `/proc` or GPU API).
- `screenBrightness` – NULL when unreadable (was -1; we use NULL for unavailable).
- `chargerVendorId`, `chargerProductId` – NULL for AC/wireless (no USB device).
- `signalStrength` – NULL when unknown.

### 3.2 New Metadata Columns (Snapshot Context)

- `snapshotTrigger` **TEXT** – `FOREGROUND` | `BACKGROUND` | `PERIODIC` | `CHARGING` | `SCREEN_ON` | `SCREEN_OFF` | `LEVEL_CHANGE` | etc.
- `dataSource` **TEXT** – `EVENT` | `POLLED` | `INFERRED`.
- `confidence` **REAL** – 0.0–1.0 (1.0 = high confidence, 0 = low).

### 3.3 Example (Conceptual)

```sql
-- BEFORE
screenOn INTEGER NOT NULL
wifiEnabled INTEGER NOT NULL
currentNow INTEGER NOT NULL

-- AFTER
screenOn INTEGER        -- 1=on, 0=off, NULL=unavailable
wifiEnabled INTEGER     -- 1=enabled, 0=disabled, NULL=unavailable
currentNow INTEGER      -- µA, NULL=unsupported
snapshotTrigger TEXT
dataSource TEXT
confidence REAL
```

### 3.4 Room Migration

- Add **Migration 25 → 26**:
  - Add `snapshotTrigger`, `dataSource`, `confidence`.
  - For each "three-state" column: `ALTER TABLE ... ADD COLUMN <col>_new ...` (nullable), backfill from existing, drop old, rename. Alternatively, use a new table + copy + drop + rename if Room schema handling is simpler.
- In practice we add new nullable columns, backfill with `CASE WHEN existing = 0 AND we_treat_as_unknown THEN NULL ELSE existing END`, then switch entity to new columns. Simpler approach: add new columns only, migrate data via SQL, then update entities to use them and deprecate old names.

---

## 4. Collector Refactor Strategy

1. **Stop polling system state in background**  
   Do **not** use `PowerManager.isInteractive()`, `WifiManager.isWifiEnabled()`, `ConnectivityManager.getActiveNetworkInfo()` etc. at sample time when running in WorkManager/Doze/background.

2. **Event-driven state tracking (mandatory)**  
   - **Screen:** `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` → update a **persistent cached state**. Snapshots read from cache only.
   - **Network:** `ConnectivityManager.NetworkCallback` → update cached connectivity (e.g. WiFi vs cellular, connected vs not). Use cache for "network state" in snapshots.
   - **Charging:** `ACTION_BATTERY_CHANGED` / `ACTION_POWER_*` → update cached charging state. Snapshots use cache.

3. **Snapshots read cached truth only**  
   No live "is X enabled?" calls at snapshot time for screen/network/charging. Use cache; if no cached value, store **NULL**.

4. **Explicit UNAVAILABLE handling**  
   - If we **cannot** obtain a metric (blocked, unsupported, no cache): store **NULL** (or -1 only where explicitly documented).  
   - **Never** write 0 to mean "we don't know".

5. **Metric-specific rules**  
   See section 7 below.

---

## 5. Code Patterns

### 5.1 Screen Tracking (Event-Driven)

- Register `IntentFilter` for `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF`.
- On receive → update `DeviceStateCache.screenOn` (true/false).
- Snapshot:
  - If cache has value → write 1 or 0.
  - If no value (e.g. before first event) → write **NULL**.

### 5.2 Network Tracking (NetworkCallback)

- Register `ConnectivityManager.NetworkCallback` (e.g. `onAvailable` / `onLost` / `onCapabilitiesChanged`).
- Update cache: "connected via WiFi", "connected via cellular", "disconnected".
- For "wifi enabled" / "mobile data enabled": use cache from capabilities where possible; if we only know "connected via X", we can infer "X enabled" for that moment. If we truly don't know → **NULL**.

### 5.3 Safe Snapshot Insertion (Three-State Booleans)

```java
// Good: nullable Integer, 1/0/NULL
Integer wifiEnabled = null;
if (cachedWifiState != null) {
    wifiEnabled = cachedWifiState ? 1 : 0;
}
snapshot.wifiEnabled = wifiEnabled;  // room Integer column
```

```java
// Bad: collapsing unknown to 0
boolean wifiEnabled = cachedWifiState != null ? cachedWifiState : false;  // DON'T
```

### 5.4 Battery Current / Energy

- If `BatteryManager.getLongProperty(ENERGY_COUNTER)` or `CURRENT_NOW` returns 0 or `Long.MIN_VALUE` **and** we know the device doesn't support it (e.g. from `LimitationsRegistry`) → store **NULL**.
- Otherwise store the measured value (including 0 when it's a real zero).

---

## 6. Sync & MySQL Integrity

- **Do not** coerce **NULL → 0** during sync.  
- Preserve **NULL** and sentinel values in JSON/API and in MySQL.
- MySQL columns that mirror three-state metrics must **allow NULL** (no `NOT NULL` + `DEFAULT 0` for those).
- Sync transformation: if Room has `NULL`, send `null` in payload; backend stores `NULL` in MySQL.

---

## 7. Metric-Specific Rules

| Metric type | Correct handling |
|-------------|------------------|
| **Booleans** (screen, wifi, mobile, BT, GPS) | 1 / 0 / **NULL** |
| **CPU/GPU load** | **NULL** when blocked or unavailable |
| **Battery current** (`currentNow` etc.) | **NULL** if unsupported |
| **Energy / charge counter** | **NULL** if unsupported |
| **Jank count, slow frames** | **0 is valid** (no jank) |
| **Charger IDs** | **NULL** for AC / wireless (no USB device) |
| **Screen brightness** | **NULL** when unreadable |

**Never fake data.** Accuracy over completeness.

---

## 8. Rules HexNil Must Follow Forever

1. **Three-state semantics:** 1 = true/active, 0 = false/inactive/zero, NULL = unavailable. Never use 0 for "unknown".
2. **No background polling** of screen/network/charging; use **event-driven** updates and **cached state** only for snapshots.
3. **Snapshot context:** Always set `snapshotTrigger`, `dataSource`, and `confidence` where applicable.
4. **Explicit UNAVAILABLE:** When we cannot obtain a metric, store **NULL** (or documented -1); never store 0 to mean "we don't know".
5. **Schema:** Allow **NULL** for all "three-state" and "may be unsupported" columns; do not use `NOT NULL` + default 0 for those.
6. **Sync:** Preserve **NULL** and sentinels end-to-end (Room → API → MySQL). No silent coercion of NULL to 0.
7. **Metric-specific rules:** Apply the table above (e.g. jank 0 = valid; charger IDs NULL for AC).
8. **Prefer accuracy over completeness.** Do not infer or invent values to fill tables.

---

## 9. Canonical Meaning of Every "0" (Post-Fix)

After implementation:

- **0** appears **only** when it is a **true** measured zero or a **reliable** "false/inactive" from cached state.
- **NULL** explicitly means "Android did not allow access" or "metric unavailable".
- Every table stores **truthful, explainable** data for OEM engineers and reviewers.

---

## 10. Implementation Summary (Done)

- **Room migration 25→26:** `battery_snapshots` recreated with nullable `screenOn`, `wifiEnabled`, `mobileDataEnabled`, `bluetoothEnabled`, `gpsEnabled`, `signalStrength`, `currentNow`, `currentAverage`, `energyCounter`, `chargeCounter`, `cpuLoadTotal`, `gpuLoad`, `screenBrightness`, `chargerVendorId`, `chargerProductId`; added `snapshotTrigger`, `dataSource`, `confidence`.
- **`DeviceStateCache` + `DefaultNetworkTracker`:** Event-driven screen (ACTION_SCREEN_ON/OFF), network (NetworkCallback), charging (ACTION_BATTERY_*). `BatteryMonitorService` updates cache; collectors read from it only.
- **`BatteryDataCollector`:** Uses cache for screen/network; `LimitationsRegistry` for current/energy/charge; stores NULL when unavailable; sets `snapshotTrigger` / `dataSource` / `confidence`.
- **Consumers:** Use `isScreenOnOrFalse()`, null-safe `screenBrightness` / `cpuLoadTotal` / `gpuLoad` / `wifiEnabled` etc. where needed.
- **Sync + MySQL:** `transform_snapshot` preserves NULL for `screen_on`, `current_now`; forwards `snapshot_trigger`, `data_source`, `confidence`. Backend `BatterySnapshot` and insert handle optional fields. Run `backend/hexnil_migration_three_state.sql` so MySQL allows NULL for `screen_on` / `current_now` and adds context columns.
