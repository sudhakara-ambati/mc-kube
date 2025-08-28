package com.mckube.javaplugin.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ControllerUtils {

    /**
     * Creates a standardized error response map
     */
    public static Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    /**
     * Creates a standardized success response map with basic fields
     */
    public static Map<String, Object> createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    /**
     * Validates that the request body is not null or empty
     */
    public static boolean validateRequestBody(Context ctx, String requestBody) {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            ctx.status(400).json(createErrorResponse("Request body is required"));
            return false;
        }
        return true;
    }

    /**
     * Parses JSON from request body with error handling
     */
    public static JsonObject parseJson(Context ctx, String requestBody) {
        try {
            return JsonParser.parseString(requestBody).getAsJsonObject();
        } catch (Exception e) {
            ctx.status(400).json(createErrorResponse("Invalid JSON format"));
            return null;
        }
    }

    /**
     * Validates that a required field exists in JSON and is not empty
     */
    public static String validateJsonField(Context ctx, JsonObject json, String fieldName, String displayName) {
        if (!json.has(fieldName)) {
            ctx.status(400).json(createErrorResponse(displayName + " field is required"));
            return null;
        }

        String value = json.get(fieldName).getAsString();
        if (value == null || value.trim().isEmpty()) {
            ctx.status(400).json(createErrorResponse(displayName + " cannot be empty"));
            return null;
        }

        return value;
    }

    /**
     * Validates that a path parameter is not null or empty
     */
    public static boolean validatePathParam(Context ctx, String paramValue, String paramName) {
        if (paramValue == null || paramValue.trim().isEmpty()) {
            ctx.status(400).json(createErrorResponse(paramName + " is required"));
            return false;
        }
        return true;
    }

    /**
     * Extracts and validates the message field from JSON
     */
    public static String extractMessage(Context ctx, JsonObject json) {
        return validateJsonField(ctx, json, "message", "Message");
    }

    /**
     * Parses and validates a complete request body returning the JsonObject
     */
    public static JsonObject parseAndValidateRequestBody(Context ctx) {
        String requestBody = ctx.body();
        if (!validateRequestBody(ctx, requestBody)) {
            return null;
        }
        return parseJson(ctx, requestBody);
    }
}