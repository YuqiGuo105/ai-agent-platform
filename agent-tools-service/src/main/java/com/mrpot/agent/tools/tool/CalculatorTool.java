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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Mathematical calculator tool.
 * Performs basic arithmetic and mathematical operations with precision.
 */
@Component
public class CalculatorTool implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final MathContext PRECISION = new MathContext(15, RoundingMode.HALF_UP);

    @Override
    public String name() {
        return "math.calculate";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        // operation
        ObjectNode operationProp = mapper.createObjectNode();
        operationProp.put("type", "string");
        operationProp.put("description", "Operation: 'add', 'subtract', 'multiply', 'divide', 'power', 'sqrt', 'percentage', 'abs', 'round'");
        properties.set("operation", operationProp);

        // a - first operand
        ObjectNode aProp = mapper.createObjectNode();
        aProp.put("type", "number");
        aProp.put("description", "First operand");
        properties.set("a", aProp);

        // b - second operand (optional for some operations)
        ObjectNode bProp = mapper.createObjectNode();
        bProp.put("type", "number");
        bProp.put("description", "Second operand (required for add, subtract, multiply, divide, power, percentage)");
        properties.set("b", bProp);

        // precision - decimal places
        ObjectNode precisionProp = mapper.createObjectNode();
        precisionProp.put("type", "integer");
        precisionProp.put("description", "Decimal places for result (default: 10)");
        properties.set("precision", precisionProp);

        inputSchema.set("properties", properties);
        inputSchema.set("required", mapper.createArrayNode().add("operation").add("a"));

        ObjectNode outputSchema = mapper.createObjectNode();
        outputSchema.put("type", "object");
        ObjectNode outputProps = mapper.createObjectNode();

        ObjectNode resultProp = mapper.createObjectNode();
        resultProp.put("type", "number");
        resultProp.put("description", "Calculation result");
        outputProps.set("result", resultProp);

        ObjectNode expressionProp = mapper.createObjectNode();
        expressionProp.put("type", "string");
        expressionProp.put("description", "Human-readable expression");
        outputProps.set("expression", expressionProp);

        outputSchema.set("properties", outputProps);

        return new ToolDefinition(
                name(),
                "Perform mathematical calculations with high precision",
                "1.0.0",
                inputSchema,
                outputSchema,
                null,
                null  // Results are deterministic but don't cache
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        try {
            String operation = args.path("operation").asText("");
            if (operation.isEmpty()) {
                return errorResponse("operation is required");
            }

            // Get operand a (required)
            if (!args.has("a") || args.get("a").isNull()) {
                return errorResponse("operand 'a' is required");
            }
            BigDecimal a = new BigDecimal(args.get("a").asText());

            // Get operand b (optional for some operations)
            BigDecimal b = null;
            if (args.has("b") && !args.get("b").isNull()) {
                b = new BigDecimal(args.get("b").asText());
            }

            int precision = args.path("precision").asInt(10);

            BigDecimal result;
            String expression;

            switch (operation.toLowerCase()) {
                case "add" -> {
                    if (b == null) return errorResponse("operand 'b' is required for add");
                    result = a.add(b, PRECISION);
                    expression = a + " + " + b + " = " + result;
                }
                case "subtract" -> {
                    if (b == null) return errorResponse("operand 'b' is required for subtract");
                    result = a.subtract(b, PRECISION);
                    expression = a + " - " + b + " = " + result;
                }
                case "multiply" -> {
                    if (b == null) return errorResponse("operand 'b' is required for multiply");
                    result = a.multiply(b, PRECISION);
                    expression = a + " × " + b + " = " + result;
                }
                case "divide" -> {
                    if (b == null) return errorResponse("operand 'b' is required for divide");
                    if (b.compareTo(BigDecimal.ZERO) == 0) {
                        return errorResponse("Division by zero");
                    }
                    result = a.divide(b, PRECISION);
                    expression = a + " ÷ " + b + " = " + result;
                }
                case "power" -> {
                    if (b == null) return errorResponse("operand 'b' is required for power");
                    result = BigDecimal.valueOf(Math.pow(a.doubleValue(), b.doubleValue()));
                    expression = a + " ^ " + b + " = " + result;
                }
                case "sqrt" -> {
                    if (a.compareTo(BigDecimal.ZERO) < 0) {
                        return errorResponse("Cannot calculate square root of negative number");
                    }
                    result = BigDecimal.valueOf(Math.sqrt(a.doubleValue()));
                    expression = "√" + a + " = " + result;
                }
                case "percentage" -> {
                    if (b == null) return errorResponse("operand 'b' is required for percentage");
                    // Calculate what percentage a is of b
                    if (b.compareTo(BigDecimal.ZERO) == 0) {
                        return errorResponse("Cannot calculate percentage with zero denominator");
                    }
                    result = a.divide(b, PRECISION).multiply(BigDecimal.valueOf(100));
                    expression = a + " is " + result + "% of " + b;
                }
                case "abs" -> {
                    result = a.abs();
                    expression = "|" + a + "| = " + result;
                }
                case "round" -> {
                    result = a.setScale(precision, RoundingMode.HALF_UP);
                    expression = "round(" + a + ", " + precision + ") = " + result;
                }
                case "mod" -> {
                    if (b == null) return errorResponse("operand 'b' is required for mod");
                    if (b.compareTo(BigDecimal.ZERO) == 0) {
                        return errorResponse("Cannot calculate modulo with zero");
                    }
                    result = a.remainder(b, PRECISION);
                    expression = a + " mod " + b + " = " + result;
                }
                default -> {
                    return errorResponse("Unknown operation: " + operation);
                }
            }

            // Round result to specified precision
            result = result.setScale(precision, RoundingMode.HALF_UP);

            ObjectNode resultNode = mapper.createObjectNode();
            resultNode.put("result", result.doubleValue());
            resultNode.put("resultString", result.toPlainString());
            resultNode.put("expression", expression);
            resultNode.put("operation", operation);

            return new CallToolResponse(true, name(), resultNode, true, null, Instant.now(), null);

        } catch (NumberFormatException e) {
            return errorResponse("Invalid number format: " + e.getMessage());
        } catch (ArithmeticException e) {
            return errorResponse("Arithmetic error: " + e.getMessage());
        } catch (Exception e) {
            return errorResponse("Calculation error: " + e.getMessage());
        }
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
