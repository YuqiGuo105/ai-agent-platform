package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Date and time utility tool.
 * Provides current time, date calculations, and timezone conversions.
 */
@Component
public class DateTimeTool implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() {
        return "datetime.now";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        // timezone - optional
        ObjectNode timezoneProp = mapper.createObjectNode();
        timezoneProp.put("type", "string");
        timezoneProp.put("description", "Timezone ID (e.g., 'America/New_York', 'Asia/Shanghai', 'UTC'). Defaults to UTC.");
        properties.set("timezone", timezoneProp);

        // format - optional
        ObjectNode formatProp = mapper.createObjectNode();
        formatProp.put("type", "string");
        formatProp.put("description", "Output format pattern (e.g., 'yyyy-MM-dd HH:mm:ss', 'ISO'). Defaults to ISO format.");
        properties.set("format", formatProp);

        // operation - optional
        ObjectNode operationProp = mapper.createObjectNode();
        operationProp.put("type", "string");
        operationProp.put("description", "Operation: 'now' (default), 'add', 'diff'");
        properties.set("operation", operationProp);

        // amount - for add operation
        ObjectNode amountProp = mapper.createObjectNode();
        amountProp.put("type", "integer");
        amountProp.put("description", "Amount to add (for 'add' operation)");
        properties.set("amount", amountProp);

        // unit - for add operation
        ObjectNode unitProp = mapper.createObjectNode();
        unitProp.put("type", "string");
        unitProp.put("description", "Unit for add: 'days', 'hours', 'minutes', 'seconds', 'weeks', 'months', 'years'");
        properties.set("unit", unitProp);

        // date1, date2 - for diff operation
        ObjectNode date1Prop = mapper.createObjectNode();
        date1Prop.put("type", "string");
        date1Prop.put("description", "First date for diff operation (ISO format)");
        properties.set("date1", date1Prop);

        ObjectNode date2Prop = mapper.createObjectNode();
        date2Prop.put("type", "string");
        date2Prop.put("description", "Second date for diff operation (ISO format)");
        properties.set("date2", date2Prop);

        inputSchema.set("properties", properties);
        inputSchema.set("required", mapper.createArrayNode());

        ObjectNode outputSchema = mapper.createObjectNode();
        outputSchema.put("type", "object");

        return new ToolDefinition(
                name(),
                "Get current date/time, perform date arithmetic, or calculate date differences",
                "1.0.0",
                inputSchema,
                outputSchema,
                null,
                0L  // Don't cache time results
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        try {
            String operation = args.path("operation").asText("now");
            String timezoneStr = args.path("timezone").asText("UTC");
            String format = args.path("format").asText("ISO");

            ZoneId zoneId;
            try {
                zoneId = ZoneId.of(timezoneStr);
            } catch (Exception e) {
                return errorResponse("Invalid timezone: " + timezoneStr);
            }

            return switch (operation.toLowerCase()) {
                case "now" -> handleNow(zoneId, format);
                case "add" -> handleAdd(args, zoneId, format);
                case "diff" -> handleDiff(args);
                default -> errorResponse("Unknown operation: " + operation);
            };
        } catch (Exception e) {
            return errorResponse("Error: " + e.getMessage());
        }
    }

    private CallToolResponse handleNow(ZoneId zoneId, String format) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return buildTimeResponse(now, format);
    }

    private CallToolResponse handleAdd(JsonNode args, ZoneId zoneId, String format) {
        int amount = args.path("amount").asInt(0);
        String unit = args.path("unit").asText("days");

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime result = switch (unit.toLowerCase()) {
            case "seconds" -> now.plusSeconds(amount);
            case "minutes" -> now.plusMinutes(amount);
            case "hours" -> now.plusHours(amount);
            case "days" -> now.plusDays(amount);
            case "weeks" -> now.plusWeeks(amount);
            case "months" -> now.plusMonths(amount);
            case "years" -> now.plusYears(amount);
            default -> now.plusDays(amount);
        };

        return buildTimeResponse(result, format);
    }

    private CallToolResponse handleDiff(JsonNode args) {
        String date1Str = args.path("date1").asText("");
        String date2Str = args.path("date2").asText("");

        if (date1Str.isEmpty() || date2Str.isEmpty()) {
            return errorResponse("Both date1 and date2 are required for diff operation");
        }

        try {
            Instant instant1 = Instant.parse(date1Str);
            Instant instant2 = Instant.parse(date2Str);

            long days = ChronoUnit.DAYS.between(instant1, instant2);
            long hours = ChronoUnit.HOURS.between(instant1, instant2);
            long minutes = ChronoUnit.MINUTES.between(instant1, instant2);
            long seconds = ChronoUnit.SECONDS.between(instant1, instant2);

            ObjectNode result = mapper.createObjectNode();
            result.put("days", days);
            result.put("hours", hours);
            result.put("minutes", minutes);
            result.put("seconds", seconds);
            result.put("date1", date1Str);
            result.put("date2", date2Str);

            return new CallToolResponse(true, name(), result, false, null, Instant.now(), null);
        } catch (DateTimeParseException e) {
            return errorResponse("Invalid date format. Use ISO format (e.g., 2024-01-15T10:30:00Z)");
        }
    }

    private CallToolResponse buildTimeResponse(ZonedDateTime dateTime, String format) {
        ObjectNode result = mapper.createObjectNode();
        
        if ("ISO".equalsIgnoreCase(format)) {
            result.put("datetime", dateTime.toInstant().toString());
        } else {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                result.put("datetime", dateTime.format(formatter));
            } catch (Exception e) {
                result.put("datetime", dateTime.toInstant().toString());
            }
        }
        
        result.put("timezone", dateTime.getZone().getId());
        result.put("offset", dateTime.getOffset().toString());
        result.put("year", dateTime.getYear());
        result.put("month", dateTime.getMonthValue());
        result.put("day", dateTime.getDayOfMonth());
        result.put("dayOfWeek", dateTime.getDayOfWeek().toString());
        result.put("hour", dateTime.getHour());
        result.put("minute", dateTime.getMinute());
        result.put("second", dateTime.getSecond());
        result.put("epochSeconds", dateTime.toEpochSecond());

        return new CallToolResponse(true, name(), result, false, null, Instant.now(), null);
    }

    private CallToolResponse errorResponse(String message) {
        return new CallToolResponse(
                false,
                name(),
                null,
                false,
                null,
                Instant.now(),
                new ToolError(ToolError.BAD_ARGS, message, false)
        );
    }
}
