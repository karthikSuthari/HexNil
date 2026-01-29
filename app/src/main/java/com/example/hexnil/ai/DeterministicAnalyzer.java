package com.example.hexnil.ai;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.DrainEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Deterministic Analyzer - MATH-ONLY, NO INTERPRETATION
 * 
 * This class handles all analysis that MUST stay deterministic:
 * - Pure mathematical calculations (averages, medians, percentiles, rates)
 * - Threshold comparisons (above/below limits)
 * - Exact measurements (voltage, temperature, capacity)
 * - Statistical calculations (variance, standard deviation, correlations)
 * - Trend detection (monotonic increase/decrease)
 * 
 * CORE PRINCIPLE: Output is REPRODUCIBLE. Same input = Same output. Always.
 * 
 * This class NEVER:
 * - Makes inferences about cause
 * - Provides recommendations
 * - Interprets user behavior
 * - Estimates future states
 * - Makes "soft" assessments
 * 
 * OEM Integration: All outputs are verifiable, auditable, and reproducible.
 */
public class DeterministicAnalyzer {
    
    private static final String TAG = "DeterministicAnalyzer";
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DETERMINISTIC RESULT STRUCTURES
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Deterministic calculation result - fully reproducible output.
     */
    public static class DeterministicResult {
        private final String calculationType;
        private final Map<String, Object> inputs;          // Exact inputs used
        private final Map<String, Object> outputs;         // Exact outputs produced
        private final String formula;                       // Formula/method used
        private final long calculationTimestamp;           // When calculated
        private final boolean isValid;                      // Input validation passed
        private final String validationNote;               // Why invalid if applicable
        
        public DeterministicResult(String calculationType, Map<String, Object> inputs,
                                    Map<String, Object> outputs, String formula,
                                    boolean isValid, String validationNote) {
            this.calculationType = calculationType;
            this.inputs = inputs;
            this.outputs = outputs;
            this.formula = formula;
            this.calculationTimestamp = System.currentTimeMillis();
            this.isValid = isValid;
            this.validationNote = validationNote;
        }
        
        public String getCalculationType() { return calculationType; }
        public Map<String, Object> getInputs() { return inputs; }
        public Map<String, Object> getOutputs() { return outputs; }
        public String getFormula() { return formula; }
        public long getCalculationTimestamp() { return calculationTimestamp; }
        public boolean isValid() { return isValid; }
        public String getValidationNote() { return validationNote; }
        
        @SuppressWarnings("unchecked")
        public <T> T getOutput(String key, Class<T> type) {
            Object value = outputs.get(key);
            if (value != null && type.isInstance(value)) {
                return (T) value;
            }
            return null;
        }
        
        public double getOutputDouble(String key, double defaultValue) {
            Object value = outputs.get(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return defaultValue;
        }
        
        public int getOutputInt(String key, int defaultValue) {
            Object value = outputs.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return defaultValue;
        }
        
        public boolean getOutputBoolean(String key, boolean defaultValue) {
            Object value = outputs.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return defaultValue;
        }
    }
    
    /**
     * Statistical summary for a numeric dataset - fully deterministic.
     */
    public static class StatisticalSummary {
        public final int count;
        public final double min;
        public final double max;
        public final double mean;
        public final double median;
        public final double standardDeviation;
        public final double variance;
        public final double p5;           // 5th percentile
        public final double p25;          // 25th percentile (Q1)
        public final double p75;          // 75th percentile (Q3)
        public final double p95;          // 95th percentile
        public final double iqr;          // Interquartile range
        public final double sum;
        public final double range;
        
        public StatisticalSummary(int count, double min, double max, double mean, double median,
                                   double standardDeviation, double variance, double p5,
                                   double p25, double p75, double p95, double sum) {
            this.count = count;
            this.min = min;
            this.max = max;
            this.mean = mean;
            this.median = median;
            this.standardDeviation = standardDeviation;
            this.variance = variance;
            this.p5 = p5;
            this.p25 = p25;
            this.p75 = p75;
            this.p95 = p95;
            this.iqr = p75 - p25;
            this.sum = sum;
            this.range = max - min;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("count", count);
            map.put("min", min);
            map.put("max", max);
            map.put("mean", mean);
            map.put("median", median);
            map.put("standard_deviation", standardDeviation);
            map.put("variance", variance);
            map.put("p5", p5);
            map.put("p25", p25);
            map.put("p75", p75);
            map.put("p95", p95);
            map.put("iqr", iqr);
            map.put("sum", sum);
            map.put("range", range);
            return map;
        }
    }
    
    /**
     * Threshold comparison result - binary yes/no with exact values.
     */
    public static class ThresholdResult {
        public final String metricName;
        public final double actualValue;
        public final double thresholdValue;
        public final String comparisonOperator;   // "GT", "GTE", "LT", "LTE", "EQ", "BETWEEN"
        public final boolean passed;
        public final String exactComparison;      // "42.5 > 40.0 = true"
        
        public ThresholdResult(String metricName, double actualValue, double thresholdValue,
                                String comparisonOperator, boolean passed) {
            this.metricName = metricName;
            this.actualValue = actualValue;
            this.thresholdValue = thresholdValue;
            this.comparisonOperator = comparisonOperator;
            this.passed = passed;
            this.exactComparison = String.format("%.2f %s %.2f = %b", 
                    actualValue, getOperatorSymbol(comparisonOperator), thresholdValue, passed);
        }
        
        private String getOperatorSymbol(String op) {
            switch (op) {
                case "GT": return ">";
                case "GTE": return ">=";
                case "LT": return "<";
                case "LTE": return "<=";
                case "EQ": return "==";
                default: return op;
            }
        }
    }
    
    /**
     * Trend analysis result - monotonic detection with exact slopes.
     */
    public static class TrendResult {
        public final String trendType;           // "INCREASING", "DECREASING", "STABLE", "FLUCTUATING"
        public final double slope;               // Rate of change per unit time
        public final double rSquared;            // Correlation coefficient squared
        public final double intercept;           // Y-intercept of linear fit
        public final int dataPoints;
        public final double startValue;
        public final double endValue;
        public final double totalChange;
        public final double percentChange;
        
        public TrendResult(String trendType, double slope, double rSquared, double intercept,
                           int dataPoints, double startValue, double endValue) {
            this.trendType = trendType;
            this.slope = slope;
            this.rSquared = rSquared;
            this.intercept = intercept;
            this.dataPoints = dataPoints;
            this.startValue = startValue;
            this.endValue = endValue;
            this.totalChange = endValue - startValue;
            this.percentChange = startValue != 0 ? ((endValue - startValue) / startValue) * 100 : 0;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // STATISTICAL CALCULATIONS - PURE MATH
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Calculate comprehensive statistical summary for any numeric dataset.
     * Formula: Standard statistical measures with exact IEEE 754 arithmetic.
     */
    public StatisticalSummary calculateStatistics(@NonNull double[] values) {
        if (values.length == 0) {
            return new StatisticalSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        
        int count = values.length;
        
        // Sort for percentiles (creates copy to avoid mutation)
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        
        double min = sorted[0];
        double max = sorted[count - 1];
        
        // Sum and mean
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        double mean = sum / count;
        
        // Variance and standard deviation
        double varianceSum = 0;
        for (double v : values) {
            varianceSum += Math.pow(v - mean, 2);
        }
        double variance = varianceSum / count;
        double standardDeviation = Math.sqrt(variance);
        
        // Percentiles using linear interpolation
        double median = getPercentile(sorted, 50);
        double p5 = getPercentile(sorted, 5);
        double p25 = getPercentile(sorted, 25);
        double p75 = getPercentile(sorted, 75);
        double p95 = getPercentile(sorted, 95);
        
        return new StatisticalSummary(count, min, max, mean, median, 
                standardDeviation, variance, p5, p25, p75, p95, sum);
    }
    
    /**
     * Calculate percentile using linear interpolation.
     * Formula: p = (n-1) * percentile/100, linear interpolate between floor and ceil.
     */
    private double getPercentile(double[] sorted, double percentile) {
        if (sorted.length == 0) return 0;
        if (sorted.length == 1) return sorted[0];
        
        double index = (sorted.length - 1) * percentile / 100.0;
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        
        if (lower == upper || upper >= sorted.length) {
            return sorted[lower];
        }
        
        double fraction = index - lower;
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
    }
    
    /**
     * Calculate linear regression for trend analysis.
     * Formula: y = mx + b using least squares method.
     */
    public TrendResult calculateTrend(@NonNull double[] values) {
        int n = values.length;
        if (n < 2) {
            return new TrendResult("INSUFFICIENT_DATA", 0, 0, 0, n, 
                    n > 0 ? values[0] : 0, n > 0 ? values[0] : 0);
        }
        
        // X values are indices (0, 1, 2, ...)
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values[i];
            sumXY += i * values[i];
            sumX2 += i * i;
            sumY2 += values[i] * values[i];
        }
        
        double meanX = sumX / n;
        double meanY = sumY / n;
        
        // Slope (m) = Σ(xi - x̄)(yi - ȳ) / Σ(xi - x̄)²
        double numerator = sumXY - n * meanX * meanY;
        double denominator = sumX2 - n * meanX * meanX;
        
        double slope = denominator != 0 ? numerator / denominator : 0;
        double intercept = meanY - slope * meanX;
        
        // R-squared calculation
        double ssRes = 0, ssTot = 0;
        for (int i = 0; i < n; i++) {
            double predicted = slope * i + intercept;
            ssRes += Math.pow(values[i] - predicted, 2);
            ssTot += Math.pow(values[i] - meanY, 2);
        }
        double rSquared = ssTot != 0 ? 1 - (ssRes / ssTot) : 0;
        
        // Determine trend type
        String trendType;
        double slopeThreshold = 0.01;  // Minimum significant slope
        
        if (Math.abs(slope) < slopeThreshold) {
            trendType = "STABLE";
        } else if (slope > 0 && rSquared > 0.5) {
            trendType = "INCREASING";
        } else if (slope < 0 && rSquared > 0.5) {
            trendType = "DECREASING";
        } else {
            trendType = "FLUCTUATING";
        }
        
        return new TrendResult(trendType, slope, rSquared, intercept, n, values[0], values[n-1]);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // THRESHOLD COMPARISONS - BINARY YES/NO
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Compare value against threshold - greater than.
     */
    public ThresholdResult compareGreaterThan(String metric, double value, double threshold) {
        return new ThresholdResult(metric, value, threshold, "GT", value > threshold);
    }
    
    /**
     * Compare value against threshold - greater than or equal.
     */
    public ThresholdResult compareGreaterThanOrEqual(String metric, double value, double threshold) {
        return new ThresholdResult(metric, value, threshold, "GTE", value >= threshold);
    }
    
    /**
     * Compare value against threshold - less than.
     */
    public ThresholdResult compareLessThan(String metric, double value, double threshold) {
        return new ThresholdResult(metric, value, threshold, "LT", value < threshold);
    }
    
    /**
     * Compare value against threshold - less than or equal.
     */
    public ThresholdResult compareLessThanOrEqual(String metric, double value, double threshold) {
        return new ThresholdResult(metric, value, threshold, "LTE", value <= threshold);
    }
    
    /**
     * Check if value is within range (inclusive).
     */
    public ThresholdResult checkWithinRange(String metric, double value, double min, double max) {
        boolean passed = value >= min && value <= max;
        String comparison = String.format("%.2f BETWEEN [%.2f, %.2f]", value, min, max);
        ThresholdResult result = new ThresholdResult(metric, value, (min + max) / 2, "BETWEEN", passed);
        return result;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // BATTERY-SPECIFIC DETERMINISTIC CALCULATIONS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Calculate exact drain rate in %/hour.
     * Formula: (startLevel - endLevel) / durationHours
     */
    public DeterministicResult calculateExactDrainRate(int startLevel, int endLevel, 
                                                        long startTime, long endTime) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("start_level", startLevel);
        inputs.put("end_level", endLevel);
        inputs.put("start_time", startTime);
        inputs.put("end_time", endTime);
        
        // Validation
        if (endTime <= startTime) {
            return new DeterministicResult("DRAIN_RATE", inputs, new HashMap<>(),
                    "(startLevel - endLevel) / durationHours", false, 
                    "Invalid time range: endTime must be > startTime");
        }
        
        if (startLevel < endLevel) {
            return new DeterministicResult("DRAIN_RATE", inputs, new HashMap<>(),
                    "(startLevel - endLevel) / durationHours", false, 
                    "Not a drain event: battery level increased");
        }
        
        double durationHours = (endTime - startTime) / (double) TimeUnit.HOURS.toMillis(1);
        double drainPercent = startLevel - endLevel;
        double drainRatePerHour = drainPercent / durationHours;
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("duration_hours", durationHours);
        outputs.put("drain_percent", drainPercent);
        outputs.put("drain_rate_per_hour", drainRatePerHour);
        outputs.put("drain_rate_per_minute", drainRatePerHour / 60);
        
        return new DeterministicResult("DRAIN_RATE", inputs, outputs,
                "(startLevel - endLevel) / durationHours", true, "Valid calculation");
    }
    
    /**
     * Calculate exact charging rate in %/hour.
     * Formula: (endLevel - startLevel) / durationHours
     */
    public DeterministicResult calculateExactChargingRate(int startLevel, int endLevel,
                                                           long startTime, long endTime) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("start_level", startLevel);
        inputs.put("end_level", endLevel);
        inputs.put("start_time", startTime);
        inputs.put("end_time", endTime);
        
        // Validation
        if (endTime <= startTime) {
            return new DeterministicResult("CHARGING_RATE", inputs, new HashMap<>(),
                    "(endLevel - startLevel) / durationHours", false, 
                    "Invalid time range: endTime must be > startTime");
        }
        
        if (endLevel < startLevel) {
            return new DeterministicResult("CHARGING_RATE", inputs, new HashMap<>(),
                    "(endLevel - startLevel) / durationHours", false, 
                    "Not a charging event: battery level decreased");
        }
        
        double durationHours = (endTime - startTime) / (double) TimeUnit.HOURS.toMillis(1);
        double chargePercent = endLevel - startLevel;
        double chargeRatePerHour = chargePercent / durationHours;
        
        // Estimate time to full
        double percentRemaining = 100 - endLevel;
        double estimatedTimeToFullHours = chargeRatePerHour > 0 ? percentRemaining / chargeRatePerHour : 0;
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("duration_hours", durationHours);
        outputs.put("charge_percent", chargePercent);
        outputs.put("charge_rate_per_hour", chargeRatePerHour);
        outputs.put("charge_rate_per_minute", chargeRatePerHour / 60);
        outputs.put("estimated_time_to_full_hours", estimatedTimeToFullHours);
        
        return new DeterministicResult("CHARGING_RATE", inputs, outputs,
                "(endLevel - startLevel) / durationHours", true, "Valid calculation");
    }
    
    /**
     * Calculate exact temperature statistics from snapshots.
     */
    public DeterministicResult calculateTemperatureStats(@NonNull List<BatterySnapshot> snapshots) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("snapshot_count", snapshots.size());
        
        if (snapshots.isEmpty()) {
            return new DeterministicResult("TEMPERATURE_STATS", inputs, new HashMap<>(),
                    "Statistical summary of temperature readings", false, 
                    "No snapshots provided");
        }
        
        // Extract temperatures (convert from tenths of degrees)
        List<Double> temps = new ArrayList<>();
        for (BatterySnapshot s : snapshots) {
            if (s.temperature > 0) {
                temps.add(s.temperature / 10.0);
            }
        }
        
        if (temps.isEmpty()) {
            return new DeterministicResult("TEMPERATURE_STATS", inputs, new HashMap<>(),
                    "Statistical summary of temperature readings", false, 
                    "No valid temperature readings");
        }
        
        double[] tempArray = new double[temps.size()];
        for (int i = 0; i < temps.size(); i++) {
            tempArray[i] = temps.get(i);
        }
        
        StatisticalSummary stats = calculateStatistics(tempArray);
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("reading_count", stats.count);
        outputs.put("min_celsius", stats.min);
        outputs.put("max_celsius", stats.max);
        outputs.put("mean_celsius", stats.mean);
        outputs.put("median_celsius", stats.median);
        outputs.put("stddev_celsius", stats.standardDeviation);
        outputs.put("p5_celsius", stats.p5);
        outputs.put("p95_celsius", stats.p95);
        outputs.put("range_celsius", stats.range);
        
        // Threshold checks
        outputs.put("ever_exceeded_40c", stats.max > 40.0);
        outputs.put("ever_exceeded_45c", stats.max > 45.0);
        outputs.put("ever_below_10c", stats.min < 10.0);
        outputs.put("time_in_optimal_range_estimate", 
                (stats.p75 <= 35.0 && stats.p25 >= 20.0) ? "HIGH" : "LOW");
        
        return new DeterministicResult("TEMPERATURE_STATS", inputs, outputs,
                "Statistical summary of temperature readings", true, "Valid calculation");
    }
    
    /**
     * Calculate exact voltage statistics from snapshots.
     */
    public DeterministicResult calculateVoltageStats(@NonNull List<BatterySnapshot> snapshots) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("snapshot_count", snapshots.size());
        
        if (snapshots.isEmpty()) {
            return new DeterministicResult("VOLTAGE_STATS", inputs, new HashMap<>(),
                    "Statistical summary of voltage readings", false, 
                    "No snapshots provided");
        }
        
        // Extract voltages (in mV)
        List<Double> voltages = new ArrayList<>();
        for (BatterySnapshot s : snapshots) {
            if (s.voltage > 0) {
                voltages.add((double) s.voltage);
            }
        }
        
        if (voltages.isEmpty()) {
            return new DeterministicResult("VOLTAGE_STATS", inputs, new HashMap<>(),
                    "Statistical summary of voltage readings", false, 
                    "No valid voltage readings");
        }
        
        double[] voltArray = new double[voltages.size()];
        for (int i = 0; i < voltages.size(); i++) {
            voltArray[i] = voltages.get(i);
        }
        
        StatisticalSummary stats = calculateStatistics(voltArray);
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("reading_count", stats.count);
        outputs.put("min_mv", stats.min);
        outputs.put("max_mv", stats.max);
        outputs.put("mean_mv", stats.mean);
        outputs.put("median_mv", stats.median);
        outputs.put("stddev_mv", stats.standardDeviation);
        outputs.put("range_mv", stats.range);
        
        // Voltage thresholds (typical Li-ion)
        outputs.put("min_volts", stats.min / 1000.0);
        outputs.put("max_volts", stats.max / 1000.0);
        outputs.put("ever_below_3400mv", stats.min < 3400);
        outputs.put("ever_above_4400mv", stats.max > 4400);
        
        return new DeterministicResult("VOLTAGE_STATS", inputs, outputs,
                "Statistical summary of voltage readings", true, "Valid calculation");
    }
    
    /**
     * Calculate exact SOC (State of Charge) distribution.
     */
    public DeterministicResult calculateSOCDistribution(@NonNull List<BatterySnapshot> snapshots) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("snapshot_count", snapshots.size());
        
        if (snapshots.isEmpty()) {
            return new DeterministicResult("SOC_DISTRIBUTION", inputs, new HashMap<>(),
                    "Distribution of battery level readings", false, 
                    "No snapshots provided");
        }
        
        // Count readings in each 10% bucket
        int[] buckets = new int[10];  // 0-9, 10-19, ..., 90-100
        int totalReadings = 0;
        int minLevel = 100;
        int maxLevel = 0;
        
        for (BatterySnapshot s : snapshots) {
            int level = s.level;
            if (level >= 0 && level <= 100) {
                int bucket = Math.min(level / 10, 9);
                buckets[bucket]++;
                totalReadings++;
                if (level < minLevel) minLevel = level;
                if (level > maxLevel) maxLevel = level;
            }
        }
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("total_readings", totalReadings);
        outputs.put("min_soc", minLevel);
        outputs.put("max_soc", maxLevel);
        outputs.put("soc_range", maxLevel - minLevel);
        
        // Bucket distribution
        for (int i = 0; i < 10; i++) {
            String key = String.format("bucket_%d0_%d9_count", i, i);
            outputs.put(key, buckets[i]);
            outputs.put(key + "_percent", totalReadings > 0 ? 
                    (double) buckets[i] / totalReadings * 100 : 0);
        }
        
        // Critical zone counts
        outputs.put("critical_low_count", buckets[0]);  // 0-9%
        outputs.put("low_count", buckets[0] + buckets[1]);  // 0-19%
        outputs.put("high_count", buckets[8] + buckets[9]);  // 80-100%
        outputs.put("optimal_range_count", buckets[2] + buckets[3] + buckets[4] + 
                buckets[5] + buckets[6] + buckets[7]);  // 20-79%
        
        return new DeterministicResult("SOC_DISTRIBUTION", inputs, outputs,
                "Distribution of battery level readings", true, "Valid calculation");
    }
    
    /**
     * Calculate exact charge cycle count.
     * Formula: Total discharge from 100% divided by battery capacity.
     */
    public DeterministicResult calculateChargeCycles(@NonNull List<BatterySnapshot> snapshots) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("snapshot_count", snapshots.size());
        
        if (snapshots.size() < 2) {
            return new DeterministicResult("CHARGE_CYCLES", inputs, new HashMap<>(),
                    "Sum of all discharge / 100", false, 
                    "Insufficient snapshots for cycle calculation");
        }
        
        // Sort by timestamp
        List<BatterySnapshot> sorted = new ArrayList<>(snapshots);
        sorted.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        
        double totalDischarge = 0;
        int lastLevel = sorted.get(0).level;
        int fullDischarges = 0;
        
        for (int i = 1; i < sorted.size(); i++) {
            int currentLevel = sorted.get(i).level;
            
            // Only count discharge (level decrease)
            if (currentLevel < lastLevel) {
                totalDischarge += (lastLevel - currentLevel);
            }
            
            // Count times reached < 5%
            if (lastLevel >= 5 && currentLevel < 5) {
                fullDischarges++;
            }
            
            lastLevel = currentLevel;
        }
        
        double equivalentCycles = totalDischarge / 100.0;
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("total_discharge_percent", totalDischarge);
        outputs.put("equivalent_cycles", equivalentCycles);
        outputs.put("full_discharge_count", fullDischarges);
        outputs.put("average_discharge_depth", snapshots.size() > 0 ? 
                totalDischarge / snapshots.size() : 0);
        
        return new DeterministicResult("CHARGE_CYCLES", inputs, outputs,
                "Sum of all discharge / 100", true, "Valid calculation");
    }
    
    /**
     * Calculate exact time spent in different states.
     */
    public DeterministicResult calculateTimeDistribution(@NonNull List<BatterySnapshot> snapshots) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("snapshot_count", snapshots.size());
        
        if (snapshots.size() < 2) {
            return new DeterministicResult("TIME_DISTRIBUTION", inputs, new HashMap<>(),
                    "Sum of time in each state", false, 
                    "Insufficient snapshots for time calculation");
        }
        
        // Sort by timestamp
        List<BatterySnapshot> sorted = new ArrayList<>(snapshots);
        sorted.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        
        long chargingTime = 0;
        long dischargingTime = 0;
        long screenOnTime = 0;
        long screenOffTime = 0;
        long highTempTime = 0;      // > 35°C
        long lowBatteryTime = 0;    // < 20%
        
        for (int i = 1; i < sorted.size(); i++) {
            BatterySnapshot prev = sorted.get(i - 1);
            BatterySnapshot curr = sorted.get(i);
            
            long interval = curr.timestamp - prev.timestamp;
            
            // Skip unreasonable gaps (> 1 hour suggests missing data)
            if (interval > TimeUnit.HOURS.toMillis(1)) {
                continue;
            }
            
            // Accumulate by state
            if (prev.isCharging) {
                chargingTime += interval;
            } else {
                dischargingTime += interval;
            }
            
            if (prev.isScreenOnOrFalse()) {
                screenOnTime += interval;
            } else {
                screenOffTime += interval;
            }
            
            if (prev.temperature > 350) {  // > 35°C (stored as tenths)
                highTempTime += interval;
            }
            
            if (prev.level < 20) {
                lowBatteryTime += interval;
            }
        }
        
        long totalTime = chargingTime + dischargingTime;
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("total_time_ms", totalTime);
        outputs.put("total_time_hours", totalTime / (double) TimeUnit.HOURS.toMillis(1));
        outputs.put("charging_time_ms", chargingTime);
        outputs.put("charging_time_hours", chargingTime / (double) TimeUnit.HOURS.toMillis(1));
        outputs.put("discharging_time_ms", dischargingTime);
        outputs.put("discharging_time_hours", dischargingTime / (double) TimeUnit.HOURS.toMillis(1));
        outputs.put("screen_on_time_ms", screenOnTime);
        outputs.put("screen_on_time_hours", screenOnTime / (double) TimeUnit.HOURS.toMillis(1));
        outputs.put("screen_off_time_ms", screenOffTime);
        outputs.put("screen_off_time_hours", screenOffTime / (double) TimeUnit.HOURS.toMillis(1));
        outputs.put("high_temp_time_ms", highTempTime);
        outputs.put("low_battery_time_ms", lowBatteryTime);
        
        // Percentages
        if (totalTime > 0) {
            outputs.put("charging_percent", (double) chargingTime / totalTime * 100);
            outputs.put("screen_on_percent", (double) screenOnTime / totalTime * 100);
            outputs.put("high_temp_percent", (double) highTempTime / totalTime * 100);
            outputs.put("low_battery_percent", (double) lowBatteryTime / totalTime * 100);
        }
        
        return new DeterministicResult("TIME_DISTRIBUTION", inputs, outputs,
                "Sum of time in each state", true, "Valid calculation");
    }
    
    /**
     * Calculate exact capacity from design and current values.
     * Formula: (currentCapacity / designCapacity) * 100
     */
    public DeterministicResult calculateCapacityHealth(int designCapacityMah, int currentCapacityMah) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("design_capacity_mah", designCapacityMah);
        inputs.put("current_capacity_mah", currentCapacityMah);
        
        if (designCapacityMah <= 0) {
            return new DeterministicResult("CAPACITY_HEALTH", inputs, new HashMap<>(),
                    "(currentCapacity / designCapacity) * 100", false, 
                    "Invalid design capacity");
        }
        
        double healthPercent = (double) currentCapacityMah / designCapacityMah * 100;
        double capacityLoss = designCapacityMah - currentCapacityMah;
        double capacityLossPercent = (capacityLoss / designCapacityMah) * 100;
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("health_percent", healthPercent);
        outputs.put("capacity_loss_mah", capacityLoss);
        outputs.put("capacity_loss_percent", capacityLossPercent);
        outputs.put("remaining_capacity_mah", currentCapacityMah);
        
        // Threshold checks
        outputs.put("above_80_percent", healthPercent >= 80);
        outputs.put("above_70_percent", healthPercent >= 70);
        outputs.put("critical_below_60", healthPercent < 60);
        
        return new DeterministicResult("CAPACITY_HEALTH", inputs, outputs,
                "(currentCapacity / designCapacity) * 100", true, "Valid calculation");
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CORRELATION CALCULATIONS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Calculate Pearson correlation coefficient between two datasets.
     * Formula: r = Σ(xi - x̄)(yi - ȳ) / √(Σ(xi - x̄)² × Σ(yi - ȳ)²)
     */
    public DeterministicResult calculateCorrelation(@NonNull double[] x, @NonNull double[] y, 
                                                     String xName, String yName) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("x_name", xName);
        inputs.put("y_name", yName);
        inputs.put("x_count", x.length);
        inputs.put("y_count", y.length);
        
        if (x.length != y.length) {
            return new DeterministicResult("CORRELATION", inputs, new HashMap<>(),
                    "Pearson correlation coefficient", false, 
                    "Array lengths must match: " + x.length + " vs " + y.length);
        }
        
        if (x.length < 3) {
            return new DeterministicResult("CORRELATION", inputs, new HashMap<>(),
                    "Pearson correlation coefficient", false, 
                    "Need at least 3 data points for correlation");
        }
        
        int n = x.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
            sumY2 += y[i] * y[i];
        }
        
        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        
        double correlation = denominator != 0 ? numerator / denominator : 0;
        double rSquared = correlation * correlation;
        
        // Determine correlation strength
        String strength;
        double absCorr = Math.abs(correlation);
        if (absCorr >= 0.8) strength = "STRONG";
        else if (absCorr >= 0.5) strength = "MODERATE";
        else if (absCorr >= 0.3) strength = "WEAK";
        else strength = "NEGLIGIBLE";
        
        String direction = correlation > 0 ? "POSITIVE" : correlation < 0 ? "NEGATIVE" : "NONE";
        
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("pearson_r", correlation);
        outputs.put("r_squared", rSquared);
        outputs.put("correlation_strength", strength);
        outputs.put("correlation_direction", direction);
        outputs.put("interpretation", strength + "_" + direction);
        
        return new DeterministicResult("CORRELATION", inputs, outputs,
                "Pearson correlation coefficient", true, "Valid calculation");
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Convert list of battery levels to double array for statistical analysis.
     */
    public double[] extractLevels(@NonNull List<BatterySnapshot> snapshots) {
        double[] levels = new double[snapshots.size()];
        for (int i = 0; i < snapshots.size(); i++) {
            levels[i] = snapshots.get(i).level;
        }
        return levels;
    }
    
    /**
     * Convert list of temperatures to double array for statistical analysis.
     */
    public double[] extractTemperatures(@NonNull List<BatterySnapshot> snapshots) {
        List<Double> temps = new ArrayList<>();
        for (BatterySnapshot s : snapshots) {
            if (s.temperature > 0) {
                temps.add(s.temperature / 10.0);
            }
        }
        double[] result = new double[temps.size()];
        for (int i = 0; i < temps.size(); i++) {
            result[i] = temps.get(i);
        }
        return result;
    }
    
    /**
     * Convert list of voltages to double array for statistical analysis.
     */
    public double[] extractVoltages(@NonNull List<BatterySnapshot> snapshots) {
        List<Double> volts = new ArrayList<>();
        for (BatterySnapshot s : snapshots) {
            if (s.voltage > 0) {
                volts.add((double) s.voltage);
            }
        }
        double[] result = new double[volts.size()];
        for (int i = 0; i < volts.size(); i++) {
            result[i] = volts.get(i);
        }
        return result;
    }
    
    /**
     * Extract drain rates from drain events.
     */
    public double[] extractDrainRates(@NonNull List<DrainEvent> events) {
        List<Double> rates = new ArrayList<>();
        for (DrainEvent e : events) {
            if (e.drainRatePerHour > 0) {
                rates.add((double) e.drainRatePerHour);
            }
        }
        double[] result = new double[rates.size()];
        for (int i = 0; i < rates.size(); i++) {
            result[i] = rates.get(i);
        }
        return result;
    }
}
