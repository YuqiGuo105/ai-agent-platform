package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Weather tool that queries weather data using Open-Meteo free API.
 * Supports current weather, forecast, and historical data.
 * 
 * Open-Meteo is a free weather API that doesn't require an API key.
 * Documentation: https://open-meteo.com/en/docs
 */
@Slf4j
@Component
public class WeatherTool implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;
    
    // Open-Meteo API endpoints (free, no API key required)
    private static final String GEOCODING_API = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String WEATHER_API = "https://api.open-meteo.com/v1/forecast";
    
    // Well-known city coordinates for common queries
    private static final Map<String, double[]> KNOWN_CITIES = Map.ofEntries(
        Map.entry("salt lake city", new double[]{40.7608, -111.8910}),
        Map.entry("盐湖城", new double[]{40.7608, -111.8910}),
        Map.entry("new york", new double[]{40.7128, -74.0060}),
        Map.entry("纽约", new double[]{40.7128, -74.0060}),
        Map.entry("los angeles", new double[]{34.0522, -118.2437}),
        Map.entry("洛杉矶", new double[]{34.0522, -118.2437}),
        Map.entry("chicago", new double[]{41.8781, -87.6298}),
        Map.entry("芝加哥", new double[]{41.8781, -87.6298}),
        Map.entry("san francisco", new double[]{37.7749, -122.4194}),
        Map.entry("旧金山", new double[]{37.7749, -122.4194}),
        Map.entry("seattle", new double[]{47.6062, -122.3321}),
        Map.entry("西雅图", new double[]{47.6062, -122.3321}),
        Map.entry("beijing", new double[]{39.9042, 116.4074}),
        Map.entry("北京", new double[]{39.9042, 116.4074}),
        Map.entry("shanghai", new double[]{31.2304, 121.4737}),
        Map.entry("上海", new double[]{31.2304, 121.4737}),
        Map.entry("tokyo", new double[]{35.6762, 139.6503}),
        Map.entry("东京", new double[]{35.6762, 139.6503}),
        Map.entry("london", new double[]{51.5074, -0.1278}),
        Map.entry("伦敦", new double[]{51.5074, -0.1278}),
        Map.entry("paris", new double[]{48.8566, 2.3522}),
        Map.entry("巴黎", new double[]{48.8566, 2.3522}),
        Map.entry("sydney", new double[]{-33.8688, 151.2093}),
        Map.entry("悉尼", new double[]{-33.8688, 151.2093}),
        Map.entry("dubai", new double[]{25.2048, 55.2708}),
        Map.entry("迪拜", new double[]{25.2048, 55.2708}),
        Map.entry("singapore", new double[]{1.3521, 103.8198}),
        Map.entry("新加坡", new double[]{1.3521, 103.8198}),
        Map.entry("hong kong", new double[]{22.3193, 114.1694}),
        Map.entry("香港", new double[]{22.3193, 114.1694})
    );

    private final HttpClient httpClient;

    public WeatherTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "weather.query";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        // location - required (city name or coordinates)
        ObjectNode locationProp = mapper.createObjectNode();
        locationProp.put("type", "string");
        locationProp.put("description", "City name (e.g., 'salt lake city', '盐湖城', 'beijing') or coordinates as 'lat,lon'");
        properties.set("location", locationProp);

        // type - optional (current, forecast, daily)
        ObjectNode typeProp = mapper.createObjectNode();
        typeProp.put("type", "string");
        typeProp.put("description", "Query type: 'current' (default), 'forecast' (7-day), 'hourly' (next 24h)");
        typeProp.set("enum", mapper.createArrayNode().add("current").add("forecast").add("hourly"));
        properties.set("type", typeProp);

        // units - optional
        ObjectNode unitsProp = mapper.createObjectNode();
        unitsProp.put("type", "string");
        unitsProp.put("description", "Temperature units: 'celsius' (default) or 'fahrenheit'");
        unitsProp.set("enum", mapper.createArrayNode().add("celsius").add("fahrenheit"));
        properties.set("units", unitsProp);

        inputSchema.set("properties", properties);
        inputSchema.set("required", mapper.createArrayNode().add("location"));

        ObjectNode outputSchema = mapper.createObjectNode();
        outputSchema.put("type", "object");
        ObjectNode outputProps = mapper.createObjectNode();

        ObjectNode locationOut = mapper.createObjectNode();
        locationOut.put("type", "string");
        locationOut.put("description", "Resolved location name");
        outputProps.set("location", locationOut);

        ObjectNode weatherOut = mapper.createObjectNode();
        weatherOut.put("type", "object");
        weatherOut.put("description", "Weather data including temperature, conditions, humidity, wind");
        outputProps.set("weather", weatherOut);

        outputSchema.set("properties", outputProps);

        return new ToolDefinition(
                name(),
                "Query current weather or forecast for any location worldwide. Supports city names in multiple languages.",
                "1.0.0",
                inputSchema,
                outputSchema,
                null,
                600L  // Cache for 10 minutes (weather data updates every 15 min)
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        String location = args.path("location").asText("").trim();
        String queryType = args.path("type").asText("current");
        String units = args.path("units").asText("celsius");
        
        if (location.isEmpty()) {
            return errorResponse("location is required");
        }

        log.info("Weather query: location={}, type={}, units={}", location, queryType, units);

        try {
            // 1. Resolve location to coordinates
            double[] coords = resolveLocation(location);
            if (coords == null) {
                return errorResponse("Unable to find location: " + location);
            }
            
            double lat = coords[0];
            double lon = coords[1];
            String resolvedLocation = location;
            
            log.debug("Resolved coordinates: lat={}, lon={}", lat, lon);
            
            // 2. Build Weather API URL
            String weatherUrl = buildWeatherUrl(lat, lon, queryType, units);
            
            // 3. Fetch weather data
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(weatherUrl))
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                return errorResponse("Weather API returned status " + response.statusCode());
            }
            
            // 4. Parse and format response
            JsonNode weatherData = mapper.readTree(response.body());
            ObjectNode result = formatWeatherResponse(weatherData, resolvedLocation, queryType, units);
            
            return new CallToolResponse(
                    true,
                    name(),
                    result,
                    false,
                    600L,
                    Instant.now(),
                    null
            );
            
        } catch (Exception e) {
            log.error("Weather query failed: {}", e.getMessage(), e);
            return errorResponse("Failed to fetch weather: " + e.getMessage());
        }
    }

    private double[] resolveLocation(String location) throws Exception {
        String normalizedLocation = location.toLowerCase().trim();
        
        // Check known cities first (faster and more reliable)
        if (KNOWN_CITIES.containsKey(normalizedLocation)) {
            return KNOWN_CITIES.get(normalizedLocation);
        }
        
        // Check if it's already coordinates
        if (normalizedLocation.matches("-?\\d+\\.?\\d*,-?\\d+\\.?\\d*")) {
            String[] parts = normalizedLocation.split(",");
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
        }
        
        // Use Open-Meteo geocoding API
        String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8);
        String geocodingUrl = GEOCODING_API + "?name=" + encodedLocation + "&count=1&language=en";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(geocodingUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            JsonNode data = mapper.readTree(response.body());
            JsonNode results = data.path("results");
            if (results.isArray() && !results.isEmpty()) {
                JsonNode firstResult = results.get(0);
                double lat = firstResult.path("latitude").asDouble();
                double lon = firstResult.path("longitude").asDouble();
                return new double[]{lat, lon};
            }
        }
        
        return null;
    }

    private String buildWeatherUrl(double lat, double lon, String queryType, String units) {
        StringBuilder url = new StringBuilder(WEATHER_API);
        url.append("?latitude=").append(lat);
        url.append("&longitude=").append(lon);
        
        // Temperature units
        if ("fahrenheit".equalsIgnoreCase(units)) {
            url.append("&temperature_unit=fahrenheit");
        }
        
        // Timezone
        url.append("&timezone=auto");
        
        // Parameters based on query type
        switch (queryType.toLowerCase()) {
            case "forecast":
            case "daily":
                url.append("&daily=weather_code,temperature_2m_max,temperature_2m_min,");
                url.append("apparent_temperature_max,apparent_temperature_min,");
                url.append("sunrise,sunset,precipitation_sum,precipitation_probability_max,");
                url.append("wind_speed_10m_max,uv_index_max");
                url.append("&forecast_days=7");
                break;
            case "hourly":
                url.append("&hourly=temperature_2m,relative_humidity_2m,");
                url.append("apparent_temperature,precipitation_probability,");
                url.append("weather_code,wind_speed_10m,wind_direction_10m");
                url.append("&forecast_hours=24");
                break;
            case "current":
            default:
                url.append("&current=temperature_2m,relative_humidity_2m,");
                url.append("apparent_temperature,is_day,precipitation,rain,");
                url.append("weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m");
                break;
        }
        
        return url.toString();
    }

    private ObjectNode formatWeatherResponse(JsonNode data, String location, String queryType, String units) {
        ObjectNode result = mapper.createObjectNode();
        result.put("location", location);
        result.put("queryType", queryType);
        result.put("units", units);
        result.put("timezone", data.path("timezone").asText("UTC"));
        
        switch (queryType.toLowerCase()) {
            case "forecast":
            case "daily":
                result.set("daily", formatDailyForecast(data.path("daily")));
                break;
            case "hourly":
                result.set("hourly", formatHourlyForecast(data.path("hourly")));
                break;
            case "current":
            default:
                result.set("current", formatCurrentWeather(data.path("current")));
                break;
        }
        
        return result;
    }

    private ObjectNode formatCurrentWeather(JsonNode current) {
        ObjectNode weather = mapper.createObjectNode();
        
        double temp = current.path("temperature_2m").asDouble();
        double feelsLike = current.path("apparent_temperature").asDouble();
        int humidity = current.path("relative_humidity_2m").asInt();
        double windSpeed = current.path("wind_speed_10m").asDouble();
        int windDir = current.path("wind_direction_10m").asInt();
        int weatherCode = current.path("weather_code").asInt();
        boolean isDay = current.path("is_day").asInt() == 1;
        double precipitation = current.path("precipitation").asDouble();
        
        weather.put("temperature", temp);
        weather.put("feelsLike", feelsLike);
        weather.put("humidity", humidity);
        weather.put("windSpeed", windSpeed);
        weather.put("windDirection", windDir);
        weather.put("windDirectionText", getWindDirectionText(windDir));
        weather.put("condition", getWeatherCondition(weatherCode));
        weather.put("conditionCode", weatherCode);
        weather.put("isDay", isDay);
        weather.put("precipitation", precipitation);
        weather.put("time", current.path("time").asText());
        
        // Add human-readable summary
        String summary = String.format(
            "Temperature: %.1f° (feels like %.1f°), %s. Humidity: %d%%. Wind: %.1f km/h %s",
            temp, feelsLike, getWeatherCondition(weatherCode), humidity, windSpeed, getWindDirectionText(windDir)
        );
        weather.put("summary", summary);
        
        return weather;
    }

    private ObjectNode formatDailyForecast(JsonNode daily) {
        ObjectNode forecast = mapper.createObjectNode();
        
        JsonNode times = daily.path("time");
        JsonNode maxTemps = daily.path("temperature_2m_max");
        JsonNode minTemps = daily.path("temperature_2m_min");
        JsonNode weatherCodes = daily.path("weather_code");
        JsonNode precipProbs = daily.path("precipitation_probability_max");
        JsonNode precipSums = daily.path("precipitation_sum");
        JsonNode uvIndex = daily.path("uv_index_max");
        
        var daysArray = mapper.createArrayNode();
        for (int i = 0; i < times.size() && i < 7; i++) {
            ObjectNode day = mapper.createObjectNode();
            day.put("date", times.get(i).asText());
            day.put("maxTemp", maxTemps.get(i).asDouble());
            day.put("minTemp", minTemps.get(i).asDouble());
            day.put("condition", getWeatherCondition(weatherCodes.get(i).asInt()));
            day.put("conditionCode", weatherCodes.get(i).asInt());
            day.put("precipitationProbability", precipProbs.isArray() ? precipProbs.get(i).asInt() : 0);
            day.put("precipitationSum", precipSums.isArray() ? precipSums.get(i).asDouble() : 0.0);
            day.put("uvIndex", uvIndex.isArray() ? uvIndex.get(i).asDouble() : 0.0);
            daysArray.add(day);
        }
        
        forecast.set("days", daysArray);
        forecast.put("forecastDays", daysArray.size());
        
        return forecast;
    }

    private ObjectNode formatHourlyForecast(JsonNode hourly) {
        ObjectNode forecast = mapper.createObjectNode();
        
        JsonNode times = hourly.path("time");
        JsonNode temps = hourly.path("temperature_2m");
        JsonNode humidity = hourly.path("relative_humidity_2m");
        JsonNode weatherCodes = hourly.path("weather_code");
        JsonNode precipProbs = hourly.path("precipitation_probability");
        
        var hoursArray = mapper.createArrayNode();
        for (int i = 0; i < times.size() && i < 24; i++) {
            ObjectNode hour = mapper.createObjectNode();
            hour.put("time", times.get(i).asText());
            hour.put("temperature", temps.get(i).asDouble());
            hour.put("humidity", humidity.get(i).asInt());
            hour.put("condition", getWeatherCondition(weatherCodes.get(i).asInt()));
            hour.put("precipitationProbability", precipProbs.isArray() ? precipProbs.get(i).asInt() : 0);
            hoursArray.add(hour);
        }
        
        forecast.set("hours", hoursArray);
        forecast.put("forecastHours", hoursArray.size());
        
        return forecast;
    }

    private String getWeatherCondition(int code) {
        // WMO Weather interpretation codes
        return switch (code) {
            case 0 -> "Clear sky";
            case 1, 2, 3 -> "Partly cloudy";
            case 45, 48 -> "Foggy";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snow";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown";
        };
    }

    private String getWindDirectionText(int degrees) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                               "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(degrees / 22.5) % 16;
        return directions[index];
    }

    private CallToolResponse errorResponse(String message) {
        return new CallToolResponse(
                false,
                name(),
                null,
                false,
                null,
                Instant.now(),
                new ToolError("WEATHER_ERROR", message, true)
        );
    }
}
