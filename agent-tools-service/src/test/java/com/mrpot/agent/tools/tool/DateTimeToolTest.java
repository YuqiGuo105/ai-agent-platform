package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.tools.service.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeToolTest {

    private DateTimeTool tool;
    private ObjectMapper mapper;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        tool = new DateTimeTool();
        mapper = new ObjectMapper();
        ctx = new ToolContext("trace-1", "session-1");
    }

    @Test
    void name_returns_datetime_now() {
        assertEquals("datetime.now", tool.name());
    }

    @Test
    void now_returns_current_time() {
        ObjectNode args = mapper.createObjectNode();

        CallToolResponse response = tool.handle(args, ctx);

        assertTrue(response.ok());
        assertNotNull(response.result());
        assertTrue(response.result().has("datetime"));
        assertTrue(response.result().has("year"));
        assertTrue(response.result().has("month"));
        assertTrue(response.result().has("day"));
    }

    @Test
    void now_with_timezone_returns_correct_timezone() {
        ObjectNode args = mapper.createObjectNode();
        args.put("timezone", "America/New_York");

        CallToolResponse response = tool.handle(args, ctx);

        assertTrue(response.ok());
        assertEquals("America/New_York", response.result().get("timezone").asText());
    }

    @Test
    void add_days_returns_future_date() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "add");
        args.put("amount", 7);
        args.put("unit", "days");

        CallToolResponse response = tool.handle(args, ctx);

        assertTrue(response.ok());
        // Just verify it returns a valid response with expected fields
        assertTrue(response.result().has("datetime"));
        assertTrue(response.result().has("dayOfWeek"));
    }

    @Test
    void diff_calculates_days_between_dates() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "diff");
        args.put("date1", "2024-01-01T00:00:00Z");
        args.put("date2", "2024-01-08T00:00:00Z");

        CallToolResponse response = tool.handle(args, ctx);

        assertTrue(response.ok());
        assertEquals(7, response.result().get("days").asInt());
    }

    @Test
    void invalid_timezone_returns_error() {
        ObjectNode args = mapper.createObjectNode();
        args.put("timezone", "Invalid/Timezone");

        CallToolResponse response = tool.handle(args, ctx);

        assertFalse(response.ok());
        assertNotNull(response.error());
    }

    @Test
    void diff_with_missing_date_returns_error() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "diff");
        args.put("date1", "2024-01-01T00:00:00Z");
        // date2 is missing

        CallToolResponse response = tool.handle(args, ctx);

        assertFalse(response.ok());
        assertNotNull(response.error());
    }
}
