package com.wildfire.tracker.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Encapsulates the output of a wildfire risk model inference run.
 */
public class RiskPrediction {

    private final int riskPct;              // 0-100 likelihood of wildfire ignition/spread
    private final String riskLevel;         // "Low", "Moderate", "High", "Extreme"
    private final double rawIndex;          // Continuous fire weather index
    private final double fuelMoisturePct;   // Estimated 1-hour equilibrium fuel moisture %
    private final String modelVersion;      // Model identifier, e.g. "ffwi-v1.0-california"
    private final String predictionTimestamp; // ISO-8601 UTC timestamp
    private final String summary;

    public RiskPrediction(int riskPct, String riskLevel, double rawIndex,
                          double fuelMoisturePct, String modelVersion,
                          String predictionTimestamp, String summary) {
        this.riskPct = Math.max(0, Math.min(100, riskPct));
        this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        this.rawIndex = rawIndex;
        this.fuelMoisturePct = fuelMoisturePct;
        this.modelVersion = Objects.requireNonNull(modelVersion, "modelVersion must not be null");
        this.predictionTimestamp = predictionTimestamp != null ? predictionTimestamp : Instant.now().toString();
        this.summary = summary != null ? summary : "";
    }

    public int getRiskPct() { return riskPct; }
    public String getRiskLevel() { return riskLevel; }
    public double getRawIndex() { return rawIndex; }
    public double getFuelMoisturePct() { return fuelMoisturePct; }
    public String getModelVersion() { return modelVersion; }
    public String getPredictionTimestamp() { return predictionTimestamp; }
    public String getSummary() { return summary; }

    @Override
    public String toString() {
        return "RiskPrediction{" +
                "riskPct=" + riskPct +
                ", riskLevel='" + riskLevel + '\'' +
                ", rawIndex=" + String.format("%.2f", rawIndex) +
                ", fuelMoisturePct=" + String.format("%.2f", fuelMoisturePct) +
                ", modelVersion='" + modelVersion + '\'' +
                ", predictionTimestamp='" + predictionTimestamp + '\'' +
                '}';
    }
}
