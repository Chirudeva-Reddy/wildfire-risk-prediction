package com.wildfire.tracker.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.wildfire.tracker.model.RiskPrediction;
import com.wildfire.tracker.model.RiskZone;
import com.wildfire.tracker.model.WeatherFeatures;
import com.wildfire.tracker.service.RiskZoneService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal REST API for the Wildfire Risk Detector test site.
 *
 * Deliberately dependency-free: uses the JDK's built-in
 * com.sun.net.httpserver.HttpServer instead of Spring Boot, so it compiles
 * and runs with nothing but a JDK (javac/java) - no Maven/Gradle required.
 *
 * Endpoints:
 *   GET  /api/risk-zones -> JSON array of California wildfire ignition-risk assessments
 *   GET  /api/predict    -> On-demand risk inference for specified weather query params
 *   POST /api/predict    -> On-demand risk inference for JSON weather payload
 *   GET  /api/health     -> simple liveness check
 */
public class ApiServer {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws IOException {
        int port = getPort();
        RiskZoneService riskZoneService = new RiskZoneService();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/risk-zones", withCors(exchange -> handleRiskZones(exchange, riskZoneService)));
        server.createContext("/api/predict", withCors(exchange -> handlePredict(exchange, riskZoneService)));
        server.createContext("/api/health", withCors(ApiServer::handleHealth));
        server.setExecutor(null);
        server.start();

        System.out.println("Wildfire Risk Detector API listening on http://localhost:" + port);
        System.out.println("Try: curl http://localhost:" + port + "/api/risk-zones");
        System.out.println("Try: curl \"http://localhost:" + port + "/api/predict?tempF=95&humidityPct=12&windMph=25&drySpellDays=30\"");
    }

    private static int getPort() {
        String prop = System.getProperty("server.port");
        if (prop != null && !prop.trim().isEmpty()) {
            try { return Integer.parseInt(prop.trim()); } catch (NumberFormatException ignored) {}
        }
        String env = System.getenv("PORT");
        if (env != null && !env.trim().isEmpty()) {
            try { return Integer.parseInt(env.trim()); } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_PORT;
    }

    private static void handleRiskZones(HttpExchange exchange, RiskZoneService service) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        List<RiskZone> riskZones = service.getAllRiskZones();
        sendResponse(exchange, 200, toJsonArray(riskZones));
    }

    private static void handlePredict(HttpExchange exchange, RiskZoneService service) throws IOException {
        String method = exchange.getRequestMethod();
        try {
            WeatherFeatures features;
            if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
                features = parseWeatherFeaturesFromParams(params);
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange.getRequestBody());
                features = parseWeatherFeaturesFromJson(body);
            } else {
                sendResponse(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            RiskPrediction prediction = service.getInferenceService().predict(features);
            sendResponse(exchange, 200, toJson(prediction));
        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\":\"internal server error: " + escape(e.getMessage()) + "\"}");
        }
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 200, "{\"status\":\"ok\"}");
    }

    /** Wraps a handler so every response (including OPTIONS preflight) carries CORS headers. */
    private static HttpHandler withCors(HttpHandler delegate) {
        return exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            delegate.handle(exchange);
        };
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(InputStream is) throws IOException {
        byte[] buffer = new byte[4096];
        StringBuilder sb = new StringBuilder();
        int read;
        while ((read = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public static Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return map;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(key, val);
            }
        }
        return map;
    }

    private static double parseDoubleParam(Map<String, String> params, String key) {
        String val = params.get(key);
        if (val == null || val.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing value for parameter: " + key);
        }
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value for parameter '" + key + "': " + val);
        }
    }

    private static int parseDrySpellDays(Map<String, String> params) {
        if (!params.containsKey("drySpellDays")) return 0;
        String val = params.get("drySpellDays");
        if (val == null || val.trim().isEmpty()) return 0;
        try {
            return (int) Math.round(Double.parseDouble(val.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value for drySpellDays: " + val);
        }
    }

    public static WeatherFeatures parseWeatherFeaturesFromParams(Map<String, String> params) {
        if (!params.containsKey("tempF") && !params.containsKey("tempC")) {
            throw new IllegalArgumentException("Missing required temperature parameter (tempF or tempC)");
        }
        if (!params.containsKey("humidityPct")) {
            throw new IllegalArgumentException("Missing required humidityPct parameter");
        }
        if (!params.containsKey("windMph") && !params.containsKey("windKmh")) {
            throw new IllegalArgumentException("Missing required wind parameter (windMph or windKmh)");
        }

        double humidityPct = parseDoubleParam(params, "humidityPct");
        int drySpellDays = parseDrySpellDays(params);
        String observationTime = params.get("observationTime");

        if (params.containsKey("tempC") || params.containsKey("windKmh")) {
            double tempC = params.containsKey("tempC")
                    ? parseDoubleParam(params, "tempC")
                    : ((parseDoubleParam(params, "tempF") - 32.0) * 5.0 / 9.0);
            double windKmh = params.containsKey("windKmh")
                    ? parseDoubleParam(params, "windKmh")
                    : (parseDoubleParam(params, "windMph") / 0.621371192);
            return WeatherFeatures.fromMetric(tempC, humidityPct, windKmh, drySpellDays, observationTime);
        } else {
            double tempF = parseDoubleParam(params, "tempF");
            double windMph = parseDoubleParam(params, "windMph");
            return WeatherFeatures.of(tempF, humidityPct, windMph, drySpellDays, observationTime);
        }
    }

    public static WeatherFeatures parseWeatherFeaturesFromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Request body must not be empty");
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON object: body must be enclosed in { }");
        }
        Map<String, String> map = new HashMap<>();
        String inner = trimmed.substring(1, trimmed.length() - 1);
        for (String pair : inner.split(",")) {
            int idx = pair.indexOf(':');
            if (idx > 0) {
                String key = pair.substring(0, idx).replaceAll("\"", "").trim();
                String val = pair.substring(idx + 1).replaceAll("\"", "").trim();
                if (!key.isEmpty()) {
                    map.put(key, val);
                }
            }
        }
        return parseWeatherFeaturesFromParams(map);
    }

    // ---- tiny hand-rolled JSON serialization (no external libraries) ----

    public static String toJsonArray(List<RiskZone> riskZones) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < riskZones.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(riskZones.get(i)));
        }
        return sb.append("]").toString();
    }

    public static String toJson(RiskZone zone) {
        return "{"
                + field("id", zone.getId()) + ","
                + field("name", zone.getName()) + ","
                + field("county", zone.getCounty()) + ","
                + "\"lat\":" + zone.getLat() + ","
                + "\"lng\":" + zone.getLng() + ","
                + "\"riskPct\":" + zone.getRiskPct() + ","
                + field("riskLevel", zone.getRiskLevel()) + ","
                + "\"tempF\":" + zone.getTempF() + ","
                + "\"humidityPct\":" + zone.getHumidityPct() + ","
                + "\"windMph\":" + zone.getWindMph() + ","
                + "\"drySpellDays\":" + zone.getDrySpellDays() + ","
                + field("lastUpdate", zone.getLastUpdate()) + ","
                + field("summary", zone.getSummary()) + ","
                + field("modelVersion", zone.getModelVersion()) + ","
                + field("predictionTimestamp", zone.getPredictionTimestamp()) + ","
                + field("dataSourceStatus", zone.getDataSourceStatus())
                + "}";
    }

    public static String toJson(RiskPrediction pred) {
        return "{"
                + "\"riskPct\":" + pred.getRiskPct() + ","
                + field("riskLevel", pred.getRiskLevel()) + ","
                + "\"rawIndex\":" + String.format(Locale.US, "%.2f", pred.getRawIndex()) + ","
                + "\"fuelMoisturePct\":" + String.format(Locale.US, "%.2f", pred.getFuelMoisturePct()) + ","
                + field("modelVersion", pred.getModelVersion()) + ","
                + field("predictionTimestamp", pred.getPredictionTimestamp()) + ","
                + field("summary", pred.getSummary())
                + "}";
    }

    private static String field(String key, String value) {
        return "\"" + key + "\":\"" + escape(value) + "\"";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
