package com.wildfire.tracker.service;

import com.wildfire.tracker.model.DataSourceStatus;
import com.wildfire.tracker.model.RiskZone;
import com.wildfire.tracker.model.WeatherFeatures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Service providing wildfire ignition-risk assessments across California.
 *
 * Risk percentages and risk levels are dynamically derived via
 * {@link WildfireRiskInferenceService} from weather conditions (heat, humidity,
 * wind velocity, and dry-spell duration).
 */
public class RiskZoneService {

    private final WildfireRiskInferenceService inferenceService;
    private final List<RiskZone> riskZones;

    public RiskZoneService() {
        this(new WildfireRiskInferenceService());
    }

    public RiskZoneService(WildfireRiskInferenceService inferenceService) {
        this.inferenceService = Objects.requireNonNull(inferenceService, "inferenceService must not be null");
        this.riskZones = buildDefaultRiskZones();
    }

    public List<RiskZone> getAllRiskZones() {
        return Collections.unmodifiableList(riskZones);
    }

    public WildfireRiskInferenceService getInferenceService() {
        return inferenceService;
    }

    private List<RiskZone> buildDefaultRiskZones() {
        List<RiskZone> list = new ArrayList<>();

        list.add(inferenceService.evaluateZone(
                "ca-001", "Angeles Foothills", "Los Angeles County",
                34.2367, -118.4319,
                WeatherFeatures.of(101, 9, 32, 41, "2026-07-24T08:15:00Z"),
                "Extreme heat, single-digit humidity, and sustained Santa Ana winds over parched chaparral.",
                DataSourceStatus.FALLBACK
        ));
        list.add(inferenceService.evaluateZone(
                "ca-002", "Feather River Canyon", "Butte County",
                39.7285, -121.6169,
                WeatherFeatures.of(88, 24, 12, 18, "2026-07-24T06:40:00Z"),
                "Warm and breezy with moderate fuel moisture; conditions easing after recent light rain.",
                DataSourceStatus.FALLBACK
        ));
        list.add(inferenceService.evaluateZone(
                "ca-003", "Cleveland National Forest", "San Diego County",
                33.0587, -116.7739,
                WeatherFeatures.of(96, 14, 22, 29, "2026-07-24T09:05:00Z"),
                "Red flag warning in effect: low humidity and gusty offshore winds over dry backcountry brush.",
                DataSourceStatus.FALLBACK
        ));
        list.add(inferenceService.evaluateZone(
                "ca-004", "Sequoia Foothills", "Tulare County",
                36.4864, -118.5658,
                WeatherFeatures.of(82, 33, 8, 10, "2026-07-23T22:10:00Z"),
                "Mild conditions and decent humidity keep ignition risk low for now.",
                DataSourceStatus.FALLBACK
        ));
        list.add(inferenceService.evaluateZone(
                "ca-005", "Sonoma Wine Country", "Sonoma County",
                38.5780, -122.9888,
                WeatherFeatures.of(94, 16, 27, 25, "2026-07-24T07:55:00Z"),
                "Afternoon winds picking up over cured grass and vineyard-adjacent wildland.",
                DataSourceStatus.FALLBACK
        ));
        list.add(inferenceService.evaluateZone(
                "ca-006", "Shasta Canyon", "Shasta County",
                40.7909, -122.3928,
                WeatherFeatures.of(90, 21, 10, 15, "2026-07-24T05:30:00Z"),
                "Warm and dry but calm winds keep spread potential moderate rather than severe.",
                DataSourceStatus.FALLBACK
        ));
        list.add(inferenceService.evaluateZone(
                "ca-007", "Santa Ana Foothills", "Riverside County",
                33.7175, -117.0242,
                WeatherFeatures.of(104, 7, 35, 45, "2026-07-24T09:20:00Z"),
                "Critical fire weather: record heat, near-zero humidity, and strong sustained winds on tinder-dry slopes.",
                DataSourceStatus.FALLBACK
        ));

        return list;
    }
}
