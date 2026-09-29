package com.wildfire.tracker.model;

/**
 * Data source status indicators to distinguish live observations from cached/stale
 * feeds and offline development fallback data.
 */
public final class DataSourceStatus {

    public static final String LIVE = "live";
    public static final String STALE = "stale";
    public static final String FALLBACK = "fallback";

    private DataSourceStatus() {
        // utility class
    }

    public static boolean isValid(String status) {
        return LIVE.equalsIgnoreCase(status) ||
               STALE.equalsIgnoreCase(status) ||
               FALLBACK.equalsIgnoreCase(status);
    }
}
