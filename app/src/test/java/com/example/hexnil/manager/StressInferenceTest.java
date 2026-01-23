package com.example.hexnil.manager;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;

public class StressInferenceTest {

    private StressAnalyzer analyzer;

    @Before
    public void setUp() {
        analyzer = new StressAnalyzer();
    }

    @Test
    public void testHighLoadInference() {
        // Simulate high drain + rising temp + screen ON
        // Android 10+ restricted scenario (CPU/GPU = -1)
        for (int i = 0; i < 20; i++) {
            StressAnalyzer.StressSample sample = new StressAnalyzer.StressSample(
                    -1, -1, 35.0f + (i * 0.1f), 80, 20.0f,
                    false, 0, 0, true, 200, "com.heavy.game", false);
            analyzer.addSample(sample);
        }

        StressAnalyzer.StressResult result = analyzer.analyze();

        assertEquals("Indirect behavioral inference", result.detectionMethod);
        assertEquals("HIGH", result.inferredLoad);
        assertEquals("CRITICAL", result.stressCategory); // 90/100 maps to CRITICAL
        assertTrue(result.stressScore > 80);
        assertEquals("HIGH", result.confidenceLevel);
        assertTrue(result.observedSignals.contains("Screen ON"));
        assertTrue(result.observedSignals.contains("Active category: gaming"));
    }

    @Test
    public void testModerateLoadInference() {
        // Simulate gaming category but moderate drain
        for (int i = 0; i < 20; i++) {
            StressAnalyzer.StressSample sample = new StressAnalyzer.StressSample(
                    -1, -1, 35.0f, 80, 5.0f,
                    false, 0, 0, true, 200, "com.tencent.ig", false);
            analyzer.addSample(sample);
        }

        StressAnalyzer.StressResult result = analyzer.analyze();

        assertEquals("MODERATE", result.inferredLoad);
        assertEquals("MODERATE", result.confidenceLevel);
        assertTrue(result.stressScore >= 40 && result.stressScore <= 60);
    }

    @Test
    public void testLowLoadInference() {
        // Simulate idle state
        for (int i = 0; i < 20; i++) {
            StressAnalyzer.StressSample sample = new StressAnalyzer.StressSample(
                    -1, -1, 30.0f, 80, 1.5f,
                    false, 0, 0, false, 0, "com.android.launcher", false);
            analyzer.addSample(sample);
        }

        StressAnalyzer.StressResult result = analyzer.analyze();

        assertEquals("LOW", result.inferredLoad);
        assertEquals("HIGH", result.confidenceLevel); // Low drain is a high confidence signal for LOW load
        assertTrue(result.stressScore < 20);
    }

    @Test
    public void testDirectMetricsInference() {
        // Test that it still uses direct metrics if available
        for (int i = 0; i < 20; i++) {
            StressAnalyzer.StressSample sample = new StressAnalyzer.StressSample(
                    80, 70, 40.0f, 80, 15.0f,
                    false, 0, 0, true, 200, "com.heavy.game", false);
            analyzer.addSample(sample);
        }

        StressAnalyzer.StressResult result = analyzer.analyze();

        assertEquals("Direct hardware counters", result.detectionMethod);
    }
}
