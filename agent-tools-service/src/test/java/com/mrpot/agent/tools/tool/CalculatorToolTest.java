package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.tools.service.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorToolTest {

    private CalculatorTool tool;
    private ObjectMapper mapper;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        tool = new CalculatorTool();
        mapper = new ObjectMapper();
        ctx = new ToolContext("trace-1", "session-1");
    }

    @Test
    void name_returns_math_calculate() {
        assertEquals("math.calculate", tool.name());
    }

    @Test
    void add_returns_correct_sum() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "add");
        args.put("a", 10);
        args.put("b", 5);

        CallToolResponse response = tool.handle(args, ctx);

        assertTrue(response.ok());
        assertEquals(15.0, response.result().get("result").asDouble());
        assertEquals("10 + 5 = 15", response.result().get("expression").asText());
    }

    @Test
    void subtract_returns_correct_difference() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "subtract");
        args.put("a", 10);
        args.put("b", 3);

        CallToolResponse response = tool.handle(args, ctx);

        assertTrue(response.ok());
        assertEquals(7.0, response.result().get("result").asDouble());
    }

    @Test
    void multiply_returns_correct_product() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "multiply");
        args.put("a", 6);
        args.put("b", 7);

        CallToolResponse response = tool.handle(args, ctx);

        assertTrue(response.ok());
        assertEquals(42.0, response.result().get("result").asDouble());
    }

    @Test
    void divide_returns_correct_quotient() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "divide");
        args.put("a", 20);
        args.put("b", 4);

        CallToolResponse response = tool.handle(args, ctx);

        assertTrue(response.ok());
        assertEquals(5.0, response.result().get("result").asDouble());
    }

    @Test
    void divide_by_zero_returns_error() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "divide");
        args.put("a", 10);
        args.put("b", 0);

        CallToolResponse response = tool.handle(args, ctx);

        assertFalse(response.ok());
        assertNotNull(response.error());
        assertTrue(response.error().message().contains("zero"));
    }

    @Test
    void sqrt_returns_correct_value() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "sqrt");
        args.put("a", 16);

        CallToolResponse response = tool.handle(args, ctx);

        assertTrue(response.ok());
        assertEquals(4.0, response.result().get("result").asDouble());
    }

    @Test
    void sqrt_negative_returns_error() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "sqrt");
        args.put("a", -16);

        CallToolResponse response = tool.handle(args, ctx);

        assertFalse(response.ok());
        assertNotNull(response.error());
    }

    @Test
    void power_returns_correct_value() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "power");
        args.put("a", 2);
        args.put("b", 10);

        CallToolResponse response = tool.handle(args, ctx);

        assertTrue(response.ok());
        assertEquals(1024.0, response.result().get("result").asDouble());
    }

    @Test
    void missing_operation_returns_error() {
        ObjectNode args = mapper.createObjectNode();
        args.put("a", 10);

        CallToolResponse response = tool.handle(args, ctx);

        assertFalse(response.ok());
        assertNotNull(response.error());
    }

    @Test
    void missing_operand_a_returns_error() {
        ObjectNode args = mapper.createObjectNode();
        args.put("operation", "add");
        args.put("b", 5);

        CallToolResponse response = tool.handle(args, ctx);

        assertFalse(response.ok());
        assertNotNull(response.error());
    }
}
