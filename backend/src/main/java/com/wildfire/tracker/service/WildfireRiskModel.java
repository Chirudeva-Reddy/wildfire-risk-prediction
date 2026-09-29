package com.wildfire.tracker.service;

import com.wildfire.tracker.model.RiskPrediction;
import com.wildfire.tracker.model.WeatherFeatures;

/**
 * Contract for wildfire ignition-risk prediction models.
 */
public interface WildfireRiskModel {

    /**
     * Executes inference on the supplied weather features.
     *
     * @param weather validated weather observations
     * @return risk prediction including continuous index, risk percentage, and category
     */
    RiskPrediction predict(WeatherFeatures weather);

    /**
     * Unique identifier and version tag of the underlying model.
     */
    String getModelVersion();
}
