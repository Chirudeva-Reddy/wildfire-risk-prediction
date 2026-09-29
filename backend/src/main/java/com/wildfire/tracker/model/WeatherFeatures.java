package com.wildfire.tracker.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Encapsulates the atmospheric and environmental features required for
 * wildfire ignition-risk inference.
 */
public class WeatherFeatures {

    private final double tempF;
    private final double humidityPct;
    private final double windMph;
    private final int drySpellDays;
    private final String observationTime;

    public WeatherFeatures(double tempF, double humidityPct, double windMph, int drySpellDays, String observationTime) {
        validate(tempF, humidityPct, windMph, drySpellDays);
        this.tempF = tempF;
        this.humidityPct = humidityPct;
        this.windMph = windMph;
        this.drySpellDays = drySpellDays;
        this.observationTime = observationTime != null ? observationTime : Instant.now().toString();
    }

    public static WeatherFeatures of(double tempF, double humidityPct, double windMph, int drySpellDays) {
        return new WeatherFeatures(tempF, humidityPct, windMph, drySpellDays, Instant.now().toString());
    }

    public static WeatherFeatures of(double tempF, double humidityPct, double windMph, int drySpellDays, String observationTime) {
        return new WeatherFeatures(tempF, humidityPct, windMph, drySpellDays, observationTime);
    }

    /**
     * Factory method to convert SI metric weather observations (°C, km/h) into model-native units (°F, mph).
     */
    public static WeatherFeatures fromMetric(double tempC, double humidityPct, double windKmh, int drySpellDays) {
        return fromMetric(tempC, humidityPct, windKmh, drySpellDays, Instant.now().toString());
    }

    public static WeatherFeatures fromMetric(double tempC, double humidityPct, double windKmh, int drySpellDays, String observationTime) {
        double tempF = (tempC * 9.0 / 5.0) + 32.0;
        double windMph = windKmh * 0.621371192;
        return new WeatherFeatures(tempF, humidityPct, windMph, drySpellDays, observationTime);
    }

    private static void validate(double tempF, double humidityPct, double windMph, int drySpellDays) {
        if (Double.isNaN(tempF) || Double.isInfinite(tempF) || tempF < -40.0 || tempF > 140.0) {
            throw new IllegalArgumentException(String.format(
                    "Temperature must be between -40°F and 140°F, got: %.2f", tempF));
        }
        if (Double.isNaN(humidityPct) || Double.isInfinite(humidityPct) || humidityPct < 0.0 || humidityPct > 100.0) {
            throw new IllegalArgumentException(String.format(
                    "Relative humidity must be between 0%% and 100%%, got: %.2f", humidityPct));
        }
        if (Double.isNaN(windMph) || Double.isInfinite(windMph) || windMph < 0.0 || windMph > 150.0) {
            throw new IllegalArgumentException(String.format(
                    "Wind speed must be between 0 and 150 mph, got: %.2f", windMph));
        }
        if (drySpellDays < 0) {
            throw new IllegalArgumentException(String.format(
                    "Dry spell days cannot be negative, got: %d", drySpellDays));
        }
    }

    public double getTempF() { return tempF; }
    public int getTempFRounded() { return (int) Math.round(tempF); }
    public double getHumidityPct() { return humidityPct; }
    public int getHumidityPctRounded() { return (int) Math.round(humidityPct); }
    public double getWindMph() { return windMph; }
    public int getWindMphRounded() { return (int) Math.round(windMph); }
    public int getDrySpellDays() { return drySpellDays; }
    public String getObservationTime() { return observationTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeatherFeatures that = (WeatherFeatures) o;
        return Double.compare(that.tempF, tempF) == 0 &&
               Double.compare(that.humidityPct, humidityPct) == 0 &&
               Double.compare(that.windMph, windMph) == 0 &&
               drySpellDays == that.drySpellDays &&
               Objects.equals(observationTime, that.observationTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tempF, humidityPct, windMph, drySpellDays, observationTime);
    }

    @Override
    public String toString() {
        return "WeatherFeatures{" +
                "tempF=" + tempF +
                ", humidityPct=" + humidityPct +
                ", windMph=" + windMph +
                ", drySpellDays=" + drySpellDays +
                ", observationTime='" + observationTime + '\'' +
                '}';
    }
}
