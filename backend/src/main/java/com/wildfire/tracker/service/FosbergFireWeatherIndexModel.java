package com.wildfire.tracker.service;

import com.wildfire.tracker.model.RiskPrediction;
import com.wildfire.tracker.model.WeatherFeatures;

import java.time.Instant;

/**
 * Wildfire ignition and spread risk prediction based on the Fosberg Fire Weather Index (FFWI).
 *
 * <p>Scientific foundations:
 * <ul>
 *   <li>Fosberg, M. A. (1978). "Weather in wildland fire management: the fire weather index."
 *       Conference on Sierra Nevada Meteorology, American Meteorological Society.</li>
 *   <li>Schroeder, M. J. & Buck, C. C. (1970). "Fire weather." USDA Forest Service Agricultural Handbook 360.</li>
 *   <li>Thomas, B. et al. (2018). "Machine Learning for Wildfire Risk Prediction." arXiv:1812.11699.</li>
 *   <li>Koh, P. W. et al. (2021). "Wildfire Hazard Analysis using Meteorological Indices." arXiv:2110.09497.</li>
 * </ul>
 *
 * The model computes 1-hour equilibrium fuel moisture content m(H, T), moisture damping factor η,
 * wind scaling factor λ = √(1 + U²), base FFWI, and adjusts for antecedent drought (dry-spell days).
 * The resulting index is calibrated into a continuous [0, 100]% ignition probability and categorized into
 * standard fire danger tiers: Low (<25%), Moderate (25-49%), High (50-74%), and Extreme (≥75%).
 */
public class FosbergFireWeatherIndexModel implements WildfireRiskModel {

    public static final String MODEL_VERSION = "ffwi-v1.0-california";

    @Override
    public String getModelVersion() {
        return MODEL_VERSION;
    }

    @Override
    public RiskPrediction predict(WeatherFeatures weather) {
        if (weather == null) {
            throw new IllegalArgumentException("Weather features cannot be null");
        }

        double t = weather.getTempF();
        double h = weather.getHumidityPct();
        double u = weather.getWindMph();
        int dryDays = weather.getDrySpellDays();

        // 1. Equilibrium moisture content (Simard 1968 / Fosberg 1978)
        double m = calculateEquilibriumMoistureContent(h, t);

        // 2. Moisture damping factor η
        double eta = calculateMoistureDamping(m);

        // 3. Wind scaling factor λ
        double lambda = Math.sqrt(1.0 + (u * u));

        // 4. Base Fosberg Fire Weather Index
        double ffwiBase = (eta * lambda) / 0.3002;

        // 5. Antecedent drought / dry spell adjustment multiplier
        // Long dry spells deplete live and fine dead fuels, increasing ignition sensitivity
        double droughtFactor = 1.0 + 0.35 * Math.min(1.0, dryDays / 30.0);
        double adjustedIndex = Math.max(0.0, ffwiBase * droughtFactor);

        // 6. Calibrated continuous risk percentage
        double riskPctDouble = mapIndexToRiskPercentage(adjustedIndex);
        int riskPct = (int) Math.round(riskPctDouble);
        riskPct = Math.max(0, Math.min(100, riskPct));

        // 7. Risk level categorization
        String riskLevel = categorizeRiskLevel(riskPct);

        // 8. Automated descriptive summary
        String summary = generateSummary(riskLevel, m, weather);

        return new RiskPrediction(
                riskPct,
                riskLevel,
                adjustedIndex,
                m,
                MODEL_VERSION,
                Instant.now().toString(),
                summary
        );
    }

    /**
     * Computes the 1-hour equilibrium fuel moisture content m as a percentage.
     */
    public static double calculateEquilibriumMoistureContent(double h, double t) {
        double m;
        if (h < 10.0) {
            m = 0.03229 + (0.281073 * h) - (0.000578 * h * t);
        } else if (h <= 50.0) {
            m = 2.22749 + (0.160107 * h) - (0.014784 * t);
        } else {
            m = 21.0606 + (0.005565 * h * h) - (0.00035 * h * t) - (0.483199 * h);
        }
        return Math.max(1.0, m);
    }

    /**
     * Moisture damping factor η from equilibrium moisture content m.
     */
    public static double calculateMoistureDamping(double m) {
        if (m <= 30.0) {
            double x = m / 30.0;
            return 1.0 - (2.0 * x) + (1.5 * x * x) - (0.5 * x * x * x);
        }
        return 0.0;
    }

    /**
     * Piecewise calibrated mapping from raw continuous index to risk percentage [0, 100].
     */
    public static double mapIndexToRiskPercentage(double rawIndex) {
        if (rawIndex <= 0.0) return 0.0;
        if (rawIndex <= 20.0) {
            // [0, 20] maps monotonically to [0, 24.9] (Low tier)
            return (rawIndex / 20.0) * 24.9;
        } else if (rawIndex <= 40.0) {
            // (20, 40] maps monotonically to [25.0, 49.9] (Moderate tier)
            return 25.0 + ((rawIndex - 20.0) / 20.0) * 24.9;
        } else if (rawIndex <= 75.0) {
            // (40, 75] maps monotonically to [50.0, 74.9] (High tier)
            return 50.0 + ((rawIndex - 40.0) / 35.0) * 24.9;
        } else if (rawIndex <= 140.0) {
            // (75, 140] maps monotonically to [75.0, 95.0] (Extreme tier)
            return 75.0 + ((rawIndex - 75.0) / 65.0) * 20.0;
        } else {
            // Saturated upper asymptote for extreme Santa Ana gale force conditions
            return Math.min(99.0, 95.0 + Math.min(4.0, (rawIndex - 140.0) / 30.0));
        }
    }

    /**
     * Maps risk percentage to categorical classification according to project threshold criteria.
     */
    public static String categorizeRiskLevel(int riskPct) {
        if (riskPct >= 75) return "Extreme";
        if (riskPct >= 50) return "High";
        if (riskPct >= 25) return "Moderate";
        return "Low";
    }

    private static String generateSummary(String riskLevel, double fuelMoisture, WeatherFeatures weather) {
        StringBuilder sb = new StringBuilder();
        if ("Extreme".equals(riskLevel)) {
            sb.append("Critical fire danger: ");
        } else if ("High".equals(riskLevel)) {
            sb.append("Elevated fire weather: ");
        } else if ("Moderate".equals(riskLevel)) {
            sb.append("Moderate fire potential: ");
        } else {
            sb.append("Low fire activity: ");
        }

        sb.append(String.format("Fuel moisture %.1f%% with %d°F heat, %d%% humidity, and %d mph wind over %d dry days.",
                fuelMoisture,
                weather.getTempFRounded(),
                weather.getHumidityPctRounded(),
                weather.getWindMphRounded(),
                weather.getDrySpellDays()));
        return sb.toString();
    }
}
