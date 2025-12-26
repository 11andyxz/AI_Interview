package com.aiinterview.validator;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/**
 * Base validator with common validation utilities
 */
public abstract class ValidationResult {
    
    private boolean valid = true;
    private List<String> errors = new ArrayList<>();
    
    public void addError(String error) {
        this.valid = false;
        this.errors.add(error);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public String getErrorMessage() {
        return String.join("; ", errors);
    }
    
    // Common validation helpers
    protected void requireField(JsonNode root, String field) {
        if (!root.has(field) || root.get(field).isNull()) {
            addError("Missing required field: " + field);
        }
    }
    
    protected void validateString(JsonNode root, String field, int minLen, int maxLen) {
        if (root.has(field) && !root.get(field).isNull()) {
            String value = root.get(field).asText();
            if (value.length() < minLen || value.length() > maxLen) {
                addError(field + " must be " + minLen + "-" + maxLen + " characters");
            }
        }
    }
    
    protected void validateNumber(JsonNode root, String field, int min, int max) {
        if (root.has(field)) {
            if (!root.get(field).isNumber()) {
                addError(field + " must be a number");
            } else {
                int value = root.get(field).asInt();
                if (value < min || value > max) {
                    addError(field + " must be " + min + "-" + max);
                }
            }
        }
    }
    
    protected void validateArray(JsonNode root, String field, int minSize, int maxSize, int itemMinLen, int itemMaxLen) {
        if (root.has(field)) {
            if (!root.get(field).isArray()) {
                addError(field + " must be an array");
                return;
            }
            
            JsonNode array = root.get(field);
            if (array.size() < minSize || array.size() > maxSize) {
                addError(field + " must have " + minSize + "-" + maxSize + " items");
            }
            
            Set<String> unique = new HashSet<>();
            for (JsonNode item : array) {
                String text = item.asText();
                if (itemMinLen > 0 && (text.length() < itemMinLen || text.length() > itemMaxLen)) {
                    addError(field + " items must be " + itemMinLen + "-" + itemMaxLen + " characters");
                    break;
                }
                if (!unique.add(text)) {
                    addError(field + " must not contain duplicates");
                    break;
                }
            }
        }
    }
    
    protected void validateEnum(JsonNode root, String field, String... validValues) {
        if (root.has(field)) {
            String value = root.get(field).asText();
            if (!Arrays.asList(validValues).contains(value)) {
                addError(field + " must be one of: " + String.join(", ", validValues));
            }
        }
    }
}
