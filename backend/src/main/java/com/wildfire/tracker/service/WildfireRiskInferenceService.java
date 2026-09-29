package com.wildfire.tracker.service;

import com.wildfire.tracker.model.DataSourceStatus;
import com.wildfire.tracker.model.RiskPrediction;
import com.wildfire.tracker.model.RiskZone;
import com.wildfire.tracker.model.WeatherFeatures;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service for executing wildfire ignition-risk inference from weather inputs.
 *
 * Provides parameter validation, metric/imperial feature transformations,
 * execution of the underlying {@link WildfireRiskModel}, and zone assessment construction.
 */
public class WildfireRiskInferenceService {

    private final WildfireRiskModel model;

    public WildfireRiskInferenceService() {
        this(new FosbergFireWeatherIndexModel());
    }

    public WildfireRiskInferenceService(WildfireRiskModel model) {
        this.model = Objects.requireNonNull(model, "WildfireRiskModel must not be null");
    }

    public WildfireRiskModel getModel() {
        return model;
    }

    /**
     * Executes inference on the provided weather features.
     */
    public RiskPrediction predict(WeatherFeatures weather) {
        if (weather == null) {
            throw new IllegalArgumentException("WeatherFeatures cannot be null");
        }
        return model.predict(weather);
    }

    /**
     * Convenience method to execute inference directly from primitive weather readings.
     */
    public RiskPrediction predict(int tempF, int humidityPct, int windMph, int drySpellDays) {
        WeatherFeatures weather = WeatherFeatures.of(tempF, humidityPct, windMph, drySpellDays);
        return predict(weather);
    }

    /**
     * Executes inference from metric observations (°C, km/h).
     */
    public RiskPrediction predictFromMetric(double tempC, double humidityPct, double windKmh, int drySpellDays) {
        WeatherFeatures weather = WeatherFeatures.fromMetric(tempC, humidityPct, windKmh, drySpellDays);
        return predict(weather);
    }

    /**
     * Evaluates a risk zone by running model inference on its weather conditions and constructing
     * a fully enriched {@link RiskZone} entity.
     */
    public RiskZone evaluateZone(String id, String name, String county, double lat, double lng,
                                 WeatherFeatures weather, String summaryOverride, String dataSourceStatus) {
        RiskPrediction prediction = predict(weather);

        String summary = (summaryOverride != null && !summaryOverride.trim().isEmpty())
                ? summaryOverride
                : prediction.getSummary();

        String status = (dataSourceStatus != null && DataSourceStatus.isValid(dataSourceStatus))
                ? dataSourceStatus
                : DataSourceStatus.FALLBACK;

        return new RiskZone(
                id,
                name,
                county,
                lat,
                lng,
                prediction.getRiskPct(),
                prediction.getRiskLevel(),
                weather.getTempFRounded(),
                weather.getHumidityPctRounded(),
                weather.getWindMphRounded(),
                weather.getDrySpellDays(),
                weather.getObservationTime(),
                summary,
                prediction.getModelVersion(),
                prediction.getPredictionTimestamp(),
                status
        );
    }
}
