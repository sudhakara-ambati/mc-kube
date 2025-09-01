package com.mckube.javaplugin.controllers;

import com.google.gson.JsonObject;
import com.mckube.javaplugin.services.QueueListService;
import com.mckube.javaplugin.utils.ControllerUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;

import java.util.*;

public class QueueController {

    private final QueueListService queueListService;
    private final Logger logger;

    public QueueController(QueueListService queueListService, Logger logger) {
        this.queueListService = queueListService;
        this.logger = logger;
    }

    public void registerRoutes(Javalin app) {
        app.get("/queue/list", this::getQueueList);
        app.get("/queue/count", this::getQueueCount);
        app.post("/queue/remove", this::removePlayer);
    }

    private void getQueueList(Context ctx) {
        try {
            String type = ctx.queryParam("type");

            // Default to usernames if no type specified
            if (type == null || type.trim().isEmpty()) {
                type = "username";
            }

            List<String> queueData;
            String listType;

            switch (type.toLowerCase()) {
                case "uuid":
                    queueData = queueListService.getQueuedPlayerUUIDs();
                    listType = "uuid";
                    break;
                case "username":
                default:
                    queueData = queueListService.getQueuedPlayerNames();
                    listType = "username";
                    break;
            }

            Map<String, Object> response = ControllerUtils.createSuccessResponse("Queue list retrieved successfully");
            response.put("type", listType);
            response.put("data", queueData);
            response.put("count", queueData.size());

            ctx.status(200).json(response);
        } catch (Exception e) {
            logger.error("Error getting queue list with type: " + ctx.queryParam("type"), e);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Error retrieving queue list"));
        }
    }

    private void getQueueCount(Context ctx) {
        try {
            int queueCount = queueListService.getQueueCount();

            Map<String, Object> response = ControllerUtils.createSuccessResponse("Queue count retrieved successfully");
            response.put("count", queueCount);

            ctx.status(200).json(response);
        } catch (Exception e) {
            logger.error("Error getting queue count", e);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Error retrieving queue count"));
        }
    }

    private void removePlayer(Context ctx) {
        try {
            JsonObject jsonObject = ControllerUtils.parseAndValidateRequestBody(ctx);
            if (jsonObject == null) return; // Error already set in context

            // Validate type field
            String type = ControllerUtils.validateJsonField(ctx, jsonObject, "type", "Type");
            if (type == null) return; // Error already set in context

            boolean success;
            String identifier;

            switch (type.toLowerCase()) {
                case "username":
                    identifier = ControllerUtils.validateJsonField(ctx, jsonObject, "player", "Player name");
                    if (identifier == null) return; // Error already set in context
                    success = queueListService.removePlayerFromQueueName(identifier);
                    break;

                case "uuid":
                    identifier = ControllerUtils.validateJsonField(ctx, jsonObject, "uuid", "UUID");
                    if (identifier == null) return; // Error already set in context

                    try {
                        UUID uuid = UUID.fromString(identifier);
                        success = queueListService.removePlayerFromQueueUUID(uuid);
                    } catch (IllegalArgumentException e) {
                        logger.error("Invalid UUID format: {}", identifier);
                        ctx.status(400).json(ControllerUtils.createErrorResponse("Invalid UUID format"));
                        return;
                    }
                    break;

                default:
                    ctx.status(400).json(ControllerUtils.createErrorResponse("Invalid type. Must be 'usernames' or 'uuids'"));
                    return;
            }

            Map<String, Object> response = createRemoveResponse(success, identifier, type);
            ctx.status(success ? 200 : 404).json(response);

        } catch (Exception e) {
            logger.error("Error removing player from queue", e);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Internal error removing player from queue"));
        }
    }

    private Map<String, Object> createRemoveResponse(boolean success, String identifier, String type) {
        String identifierType = type.equals("usernames") ? "Player" : "UUID";
        String message;

        if (success) {
            message = identifierType + " " + identifier + " removed from queue successfully";
        } else {
            message = identifierType + " " + identifier + " not found in queue";
        }

        Map<String, Object> response = success
                ? ControllerUtils.createSuccessResponse(message)
                : ControllerUtils.createErrorResponse(message);

        response.put("type", type);
        response.put(type.equals("usernames") ? "player" : "uuid", identifier);

        return response;
    }
}