package com.wildfire.tracker.service;

import com.wildfire.tracker.controller.ApiServer;
import com.wildfire.tracker.model.DataSourceStatus;
import com.wildfire.tracker.model.RiskPrediction;
import com.wildfire.tracker.model.RiskZone;
import com.wildfire.tracker.model.WeatherFeatures;

import java.time.Instant;
import java.util.List;

/**
 * Unit and integration tests for {@link WildfireRiskInferenceService}, {@link FosbergFireWeatherIndexModel},
 * and model output serialization.
 */
public class WildfireRiskInferenceServiceTest {

    private final WildfireRiskInferenceService service = new WildfireRiskInferenceService();

    public static void main(String[] args) {
        WildfireRiskInferenceServiceTest tester = new WildfireRiskInferenceServiceTest();
        tester.runAllTests();
    }

    public void runAllTests() {
        System.out.println("Running WildfireRiskInferenceServiceTest suite...");

        testCaliforniaSamplePredictions();
        testThresholdBoundaries();
        testInputValidationOutOfRange();
        testMetricConversion();
        testDroughtMultiplierMonotonicity();
        testFuelMoisturePhysics();
        testDataSourceStatusLabeling();
        testJsonSerializationFormat();

        System.out.println("All WildfireRiskInferenceServiceTest tests passed successfully!");
    }

    public void testCaliforniaSamplePredictions() {
        System.out.print("  - testCaliforniaSamplePredictions: ");
        // ca-001 Angeles Foothills: 101°F, 9% humidity, 32 mph wind, 41 dry days -> Extreme
        RiskPrediction p1 = service.predict(101, 9, 32, 41);
        assertEquals("Extreme", p1.getRiskLevel(), "ca-001 risk level");
        assertTrue(p1.getRiskPct() >= 75, "ca-001 riskPct >= 75");

        // ca-002 Feather River Canyon: 88°F, 24% humidity, 12 mph wind, 18 dry days -> Moderate
        RiskPrediction p2 = service.predict(88, 24, 12, 18);
        assertEquals("Moderate", p2.getRiskLevel(), "ca-002 risk level");
        assertTrue(p2.getRiskPct() >= 25 && p2.getRiskPct() < 50, "ca-002 riskPct in [25, 49]");

        // ca-004 Sequoia Foothills: 82°F, 33% humidity, 8 mph wind, 10 dry days -> Low
        RiskPrediction p4 = service.predict(82, 33, 8, 10);
        assertEquals("Low", p4.getRiskLevel(), "ca-004 risk level");
        assertTrue(p4.getRiskPct() < 25, "ca-004 riskPct < 25");

        // ca-007 Santa Ana Foothills: 104°F, 7% humidity, 35 mph wind, 45 dry days -> Extreme
        RiskPrediction p7 = service.predict(104, 7, 35, 45);
        assertEquals("Extreme", p7.getRiskLevel(), "ca-007 risk level");
        assertTrue(p7.getRiskPct() >= 90, "ca-007 riskPct >= 90");

        System.out.println("PASSED");
    }

    public void testThresholdBoundaries() {
        System.out.print("  - testThresholdBoundaries: ");

        // Low threshold (< 25)
        assertEquals("Low", FosbergFireWeatherIndexModel.categorizeRiskLevel(0), "0% Low");
        assertEquals("Low", FosbergFireWeatherIndexModel.categorizeRiskLevel(24), "24% Low");

        // Moderate threshold (25 - 49)
        assertEquals("Moderate", FosbergFireWeatherIndexModel.categorizeRiskLevel(25), "25% Moderate");
        assertEquals("Moderate", FosbergFireWeatherIndexModel.categorizeRiskLevel(49), "49% Moderate");

        // High threshold (50 - 74)
        assertEquals("High", FosbergFireWeatherIndexModel.categorizeRiskLevel(50), "50% High");
        assertEquals("High", FosbergFireWeatherIndexModel.categorizeRiskLevel(74), "74% High");

        // Extreme threshold (>= 75)
        assertEquals("Extreme", FosbergFireWeatherIndexModel.categorizeRiskLevel(75), "75% Extreme");
        assertEquals("Extreme", FosbergFireWeatherIndexModel.categorizeRiskLevel(100), "100% Extreme");

        // Verify index-to-percentage mapping monotonicity
        double p10 = FosbergFireWeatherIndexModel.mapIndexToRiskPercentage(10.0);
        double p30 = FosbergFireWeatherIndexModel.mapIndexToRiskPercentage(30.0);
        double p55 = FosbergFireWeatherIndexModel.mapIndexToRiskPercentage(55.0);
        double p90 = FosbergFireWeatherIndexModel.mapIndexToRiskPercentage(90.0);

        assertTrue(p10 < p30, "Monotonicity: p10 < p30");
        assertTrue(p30 < p55, "Monotonicity: p30 < p55");
        assertTrue(p55 < p90, "Monotonicity: p55 < p90");

        System.out.println("PASSED");
    }

    public void testInputValidationOutOfRange() {
        System.out.print("  - testInputValidationOutOfRange: ");

        // Temperature out of range
        assertThrows(() -> WeatherFeatures.of(-50, 20, 10, 5), "Temp < -40°F");
        assertThrows(() -> WeatherFeatures.of(150, 20, 10, 5), "Temp > 140°F");
        assertThrows(() -> WeatherFeatures.of(Double.NaN, 20, 10, 5), "Temp NaN");

        // Humidity out of range
        assertThrows(() -> WeatherFeatures.of(75, -5, 10, 5), "Humidity < 0%");
        assertThrows(() -> WeatherFeatures.of(75, 105, 10, 5), "Humidity > 100%");

        // Wind out of range
        assertThrows(() -> WeatherFeatures.of(75, 20, -1, 5), "Wind < 0");
        assertThrows(() -> WeatherFeatures.of(75, 20, 200, 5), "Wind > 150");

        // Dry spell days negative
        assertThrows(() -> WeatherFeatures.of(75, 20, 10, -3), "Dry days < 0");

        System.out.println("PASSED");
    }

    public void testMetricConversion() {
        System.out.print("  - testMetricConversion: ");

        // 35°C should be 95°F, 40 km/h should be ~24.85 mph
        WeatherFeatures metric = WeatherFeatures.fromMetric(35.0, 15.0, 40.0, 20);
        assertEquals(95, metric.getTempFRounded(), "35°C to °F");
        assertEquals(25, metric.getWindMphRounded(), "40 km/h to mph");
        assertEquals(15, metric.getHumidityPctRounded(), "Humidity preservation");

        RiskPrediction p = service.predict(metric);
        assertNotNull(p, "Prediction not null");
        assertTrue(p.getRiskPct() > 0, "Risk pct > 0");

        System.out.println("PASSED");
    }

    public void testDroughtMultiplierMonotonicity() {
        System.out.print("  - testDroughtMultiplierMonotonicity: ");

        RiskPrediction baseline = service.predict(90, 15, 20, 0);
        RiskPrediction dry15 = service.predict(90, 15, 20, 15);
        RiskPrediction dry30 = service.predict(90, 15, 20, 30);
        RiskPrediction dry60 = service.predict(90, 15, 20, 60);

        assertTrue(baseline.getRawIndex() < dry15.getRawIndex(), "0d vs 15d drought");
        assertTrue(dry15.getRawIndex() < dry30.getRawIndex(), "15d vs 30d drought");
        assertEquals(dry30.getRawIndex(), dry60.getRawIndex(), 0.001, "Drought factor saturates at 30d");

        System.out.println("PASSED");
    }

    public void testFuelMoisturePhysics() {
        System.out.print("  - testFuelMoisturePhysics: ");

        // Lower humidity should yield lower fuel moisture
        double mLowHumid = FosbergFireWeatherIndexModel.calculateEquilibriumMoistureContent(8.0, 95.0);
        double mHighHumid = FosbergFireWeatherIndexModel.calculateEquilibriumMoistureContent(60.0, 95.0);
        assertTrue(mLowHumid < mHighHumid, "Fuel moisture lower in dry air");

        // Moisture damping drops to 0 when m > 30%
        double dampingWet = FosbergFireWeatherIndexModel.calculateMoistureDamping(35.0);
        assertEquals(0.0, dampingWet, 0.001, "Moisture damping 0 for wet fuels");

        double dampingDry = FosbergFireWeatherIndexModel.calculateMoistureDamping(5.0);
        assertTrue(dampingDry > 0.6, "Moisture damping high for dry fuels");

        System.out.println("PASSED");
    }

    public void testDataSourceStatusLabeling() {
        System.out.print("  - testDataSourceStatusLabeling: ");

        RiskZoneService rzs = new RiskZoneService(service);
        List<RiskZone> zones = rzs.getAllRiskZones();
        assertEquals(7, zones.size(), "Zone count");

        for (RiskZone z : zones) {
            assertEquals(DataSourceStatus.FALLBACK, z.getDataSourceStatus(), "Default status is fallback");
            assertEquals("ffwi-v1.0-california", z.getModelVersion(), "Model version");
            assertNotNull(z.getPredictionTimestamp(), "Timestamp present");
            assertTrue(z.getRiskPct() >= 0 && z.getRiskPct() <= 100, "RiskPct valid");
        }

        // Live evaluation
        RiskZone liveZone = service.evaluateZone(
                "live-001", "Sierra Crest", "Placer County", 39.1, -120.2,
                WeatherFeatures.of(85, 20, 15, 12), null, DataSourceStatus.LIVE
        );
        assertEquals(DataSourceStatus.LIVE, liveZone.getDataSourceStatus(), "Live status set");

        System.out.println("PASSED");
    }

    public void testJsonSerializationFormat() {
        System.out.print("  - testJsonSerializationFormat: ");

        RiskZone zone = service.evaluateZone(
                "test-01", "Test Zone", "Orange County", 33.7, -117.8,
                WeatherFeatures.of(90, 15, 20, 10, "2026-07-25T10:00:00Z"),
                "Test summary description",
                DataSourceStatus.LIVE
        );

        String json = ApiServer.toJson(zone);
        assertTrue(json.contains("\"id\":\"test-01\""), "JSON id");
        assertTrue(json.contains("\"modelVersion\":\"ffwi-v1.0-california\""), "JSON modelVersion");
        assertTrue(json.contains("\"dataSourceStatus\":\"live\""), "JSON dataSourceStatus");
        assertTrue(json.contains("\"riskPct\":"), "JSON riskPct");
        assertTrue(json.contains("\"riskLevel\":"), "JSON riskLevel");

        RiskPrediction pred = service.predict(WeatherFeatures.of(85, 25, 15, 5));
        String predJson = ApiServer.toJson(pred);
        assertTrue(predJson.contains("\"riskPct\":"), "Pred JSON riskPct");
        assertTrue(predJson.contains("\"rawIndex\":"), "Pred JSON rawIndex");
        assertTrue(predJson.contains("\"fuelMoisturePct\":"), "Pred JSON fuelMoisturePct");

        System.out.println("PASSED");
    }

    // --- Helpers ---
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Assertion failed: " + message + " (expected: " + expected + ", actual: " + actual + ")");
    }

    private static void assertEquals(double expected, double actual, double delta, String message) {
        if (Math.abs(expected - actual) <= delta) return;
        throw new AssertionError("Assertion failed: " + message + " (expected: " + expected + ", actual: " + actual + ")");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError("Assertion failed: " + message + " (expected not null)");
        }
    }

    private static void assertThrows(Runnable runnable, String message) {
        try {
            runnable.run();
            throw new AssertionError("Expected exception was not thrown: " + message);
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }
}
