package com.mrpot.agent.tools.tool.deep;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Input validator for Deep Tools.
 * Provides basic validation for required fields and types.
 */
public final class DeepToolInputValidator {
    
    private DeepToolInputValidator() {}
    
    /**
     * Validation result.
     */
    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }
        
        public static ValidationResult fail(String error) {
            return new ValidationResult(false, List.of(error));
        }
        
        public static ValidationResult fail(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }
    
    /**
     * Validate planning.decompose input.
     */
    public static ValidationResult validatePlanningDecompose(JsonNode args) {
        List<String> errors = new ArrayList<>();
        
        if (args == null) {
            return ValidationResult.fail("Args cannot be null");
        }
        
        if (!args.has("objective") || args.get("objective").isNull()) {
            errors.add("Required field 'objective' is missing");
        } else if (!args.get("objective").isTextual()) {
            errors.add("Field 'objective' must be a string");
        } else if (args.get("objective").asText().isBlank()) {
            errors.add("Field 'objective' cannot be empty");
        }
        
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }
    
    /**
     * Validate planning.next_step input.
     */
    public static ValidationResult validatePlanningNextStep(JsonNode args) {
        List<String> errors = new ArrayList<>();
        
        if (args == null) {
            return ValidationResult.fail("Args cannot be null");
        }
        
        if (!args.has("currentState") || args.get("currentState").isNull()) {
            errors.add("Required field 'currentState' is missing");
        } else if (!args.get("currentState").isTextual()) {
            errors.add("Field 'currentState' must be a string");
        }
        
        if (args.has("availableTools") && !args.get("availableTools").isArray()) {
            errors.add("Field 'availableTools' must be an array");
        }
        
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }
    
    /**
     * Validate reasoning.compare input.
     */
    public static ValidationResult validateReasoningCompare(JsonNode args) {
        List<String> errors = new ArrayList<>();
        
        if (args == null) {
            return ValidationResult.fail("Args cannot be null");
        }
        
        if (!args.has("items") || args.get("items").isNull()) {
            errors.add("Required field 'items' is missing");
        } else if (!args.get("items").isArray()) {
            errors.add("Field 'items' must be an array");
        } else if (args.get("items").isEmpty()) {
            errors.add("Field 'items' cannot be empty");
        }
        
        if (!args.has("criteria") || args.get("criteria").isNull()) {
            errors.add("Required field 'criteria' is missing");
        } else if (!args.get("criteria").isTextual()) {
            errors.add("Field 'criteria' must be a string");
        }
        
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }
    
    /**
     * Validate reasoning.analyze input.
     */
    public static ValidationResult validateReasoningAnalyze(JsonNode args) {
        List<String> errors = new ArrayList<>();
        
        if (args == null) {
            return ValidationResult.fail("Args cannot be null");
        }
        
        if (!args.has("data") || args.get("data").isNull()) {
            errors.add("Required field 'data' is missing");
        } else if (!args.get("data").isTextual()) {
            errors.add("Field 'data' must be a string");
        }
        
        if (!args.has("question") || args.get("question").isNull()) {
            errors.add("Required field 'question' is missing");
        } else if (!args.get("question").isTextual()) {
            errors.add("Field 'question' must be a string");
        }
        
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }
    
    /**
     * Validate memory.store input.
     */
    public static ValidationResult validateMemoryStore(JsonNode args) {
        List<String> errors = new ArrayList<>();
        
        if (args == null) {
            return ValidationResult.fail("Args cannot be null");
        }
        
        if (!args.has("lane") || args.get("lane").isNull()) {
            errors.add("Required field 'lane' is missing");
        } else if (!args.get("lane").isTextual()) {
            errors.add("Field 'lane' must be a string");
        } else {
            String lane = args.get("lane").asText();
            if (!lane.equals("facts") && !lane.equals("plans")) {
                errors.add("Field 'lane' must be 'facts' or 'plans'");
            }
        }
        
        if (!args.has("key") || args.get("key").isNull()) {
            errors.add("Required field 'key' is missing");
        } else if (!args.get("key").isTextual()) {
            errors.add("Field 'key' must be a string");
        } else if (args.get("key").asText().isBlank()) {
            errors.add("Field 'key' cannot be empty");
        }
        
        if (!args.has("value") || args.get("value").isNull()) {
            errors.add("Required field 'value' is missing");
        } else if (!args.get("value").isTextual()) {
            errors.add("Field 'value' must be a string");
        }
        
        if (args.has("ttl") && !args.get("ttl").isNumber()) {
            errors.add("Field 'ttl' must be a number");
        }
        
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }
    
    /**
     * Validate memory.recall input.
     */
    public static ValidationResult validateMemoryRecall(JsonNode args) {
        List<String> errors = new ArrayList<>();
        
        if (args == null) {
            return ValidationResult.fail("Args cannot be null");
        }
        
        if (!args.has("lane") || args.get("lane").isNull()) {
            errors.add("Required field 'lane' is missing");
        } else if (!args.get("lane").isTextual()) {
            errors.add("Field 'lane' must be a string");
        } else {
            String lane = args.get("lane").asText();
            if (!lane.equals("facts") && !lane.equals("plans")) {
                errors.add("Field 'lane' must be 'facts' or 'plans'");
            }
        }
        
        if (!args.has("key") || args.get("key").isNull()) {
            errors.add("Required field 'key' is missing");
        } else if (!args.get("key").isTextual()) {
            errors.add("Field 'key' must be a string");
        }
        
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }
}
