package com.mrpot.agent.tools.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class WeatherToolHandler implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    // WMO weather interpretation codes returned by Open-Meteo
    private static final Map<Integer, String> WEATHER_CODE_MAP = Map.ofEntries(
        Map.entry(0,  "Clear sky"),
        Map.entry(1,  "Mainly clear"),
        Map.entry(2,  "Partly cloudy"),
        Map.entry(3,  "Overcast"),
        Map.entry(45, "Fog"),
        Map.entry(48, "Depositing rime fog"),
        Map.entry(51, "Light drizzle"),
        Map.entry(53, "Moderate drizzle"),
        Map.entry(55, "Dense drizzle"),
        Map.entry(61, "Slight rain"),
        Map.entry(63, "Moderate rain"),
        Map.entry(65, "Heavy rain"),
        Map.entry(80, "Rain showers"),
        Map.entry(81, "Heavy rain showers"),
        Map.entry(82, "Violent rain showers"),
        Map.entry(95, "Thunderstorm"),
        Map.entry(96, "Thunderstorm with hail"),
        Map.entry(99, "Severe thunderstorm")
    );

    private final WebClient webClient;

    public WeatherToolHandler(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://api.open-meteo.com")
            .build();
    }

    @Override
    public String name() { return "weather.getCurrentWeather"; }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        ObjectNode latProp = mapper.createObjectNode();
        latProp.put("type", "number");
        latProp.put("description", "Latitude of the location (e.g. 40.7128 for New York)");
        properties.set("latitude", latProp);

        ObjectNode lonProp = mapper.createObjectNode();
        lonProp.put("type", "number");
        lonProp.put("description", "Longitude of the location (e.g. -74.006 for New York)");
        properties.set("longitude", lonProp);

        ObjectNode tzProp = mapper.createObjectNode();
        tzProp.put("type", "string");
        tzProp.put("description", "Timezone name (e.g. 'America/New_York'). Defaults to 'auto'.");
        properties.set("timezone", tzProp);

        inputSchema.set("properties", properties);
        inputSchema.set("required", mapper.createArrayNode().add("latitude").add("longitude"));

        return new ToolDefinition(
            "weather.getCurrentWeather",
            "Get current weather conditions (temperature °C, description, sunrise/sunset) for a given latitude/longitude. Uses Open-Meteo — free, no API key required.",
            "1.0.0",
            inputSchema,
            mapper.createObjectNode(),
            null,
            600L   // cache hint: 10 minutes
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        JsonNode latNode = args.path("latitude");
        JsonNode lonNode = args.path("longitude");

        if (latNode.isMissingNode() || lonNode.isMissingNode()) {
            return new CallToolResponse(false, name(), null, false, null, Instant.now(),
                new ToolError(ToolError.BAD_ARGS, "latitude and longitude are required", false));
        }

        double latitude  = latNode.asDouble();
        double longitude = lonNode.asDouble();
        String timezone  = args.path("timezone").isMissingNode()
            ? "auto"
            : args.path("timezone").asText("auto");

        try {
            String url = String.format(
                "/v1/forecast?latitude=%s&longitude=%s" +
                "&current=temperature_2m,weather_code" +
                "&daily=sunrise,sunset" +
                "&temperature_unit=celsius" +
                "&timezone=%s",
                latitude, longitude, timezone
            );

            String responseJson = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();

            JsonNode root    = mapper.readTree(responseJson);
            JsonNode current = root.path("current");
            JsonNode daily   = root.path("daily");

            double temperature = current.path("temperature_2m").asDouble();
            int    weatherCode = current.path("weather_code").asInt(-1);
            String description = WEATHER_CODE_MAP.getOrDefault(weatherCode, "Unknown");
            String sunrise     = daily.path("sunrise").path(0).asText(null);
            String sunset      = daily.path("sunset").path(0).asText(null);

            ObjectNode result = mapper.createObjectNode();
            result.put("latitude",    latitude);
            result.put("longitude",   longitude);
            result.put("timezone",    timezone);
            result.put("temperature", temperature);
            result.put("unit",        "celsius");
            result.put("weatherCode", weatherCode);
            result.put("description", description);
            if (sunrise != null) result.put("sunrise", sunrise);
            if (sunset  != null) result.put("sunset",  sunset);
            result.put("provider",    "open-meteo.com (free, no key)");
            result.put("fetchedAt",   Instant.now().toString());

            return new CallToolResponse(true, name(), result, false, 600L, Instant.now(), null);

        } catch (Exception e) {
            return new CallToolResponse(false, name(), null, false, null, Instant.now(),
                new ToolError(ToolError.INTERNAL,
                    "Failed to fetch weather from Open-Meteo: " + e.getMessage(), true));
        }
    }
}
