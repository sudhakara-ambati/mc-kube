package com.mckube.javaplugin.controllers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mckube.javaplugin.services.QueueListService;
import io.javalin.Javalin;
import org.slf4j.Logger;
import java.util.*;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class QueueController {

    private final QueueListService queueListService;
    private final Logger logger;

    public QueueController(QueueListService queueListService, Logger logger) {
        this.queueListService = queueListService;
        this.logger = logger;
    }

    public void registerRoutes(Javalin app) {
        app.get("/queue/playerlist", this::getPlayerList);
        app.get("/queue/uuidlist", this::getUuidList);
        app.get("/queue/count", this::getQueueCount);
        app.post("/queue/removename", this::removePlayerName);
        app.post("/queue/removeuuid", this::removePlayerUUID);
    }

    private void getPlayerList(io.javalin.http.Context ctx) {
        try {
            List<String> playerNames = queueListService.getQueuedPlayerNames();
            ctx.status(200).json(playerNames);
        } catch (Exception e) {
            logger.error("Error getting player list", e);
            ctx.status(500).json(createErrorResponse("Error retrieving player list"));
        }
    }

    private void getUuidList(io.javalin.http.Context ctx) {
        try {
            List<String> playerNames = queueListService.getQueuedPlayerUUIDs();
            ctx.status(200).json(playerNames);
        } catch (Exception e) {
            logger.error("Error getting player list", e);
            ctx.status(500).json(createErrorResponse("Error retrieving player list"));
        }
    }

    private void getQueueCount(io.javalin.http.Context ctx) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", queueListService.getQueueCount());
            ctx.status(200).json(response);
        } catch (Exception e) {
            logger.error("Error getting queue count", e);
            ctx.status(500).json(createErrorResponse("Error retrieving queue count"));
        }
    }

    private void removePlayerName(io.javalin.http.Context ctx) {
        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            String playerName = body.get("player").getAsString();

            boolean success = queueListService.removePlayerFromQueueName(playerName);
            Map<String, Object> response = createRemoveResponse(success, playerName);
            ctx.status(success ? 200 : 400).json(response);

        } catch (Exception e) {
            logger.error("Error removing player from queue", e);
            ctx.status(500).json(createErrorResponse("Internal error"));
        }
    }

    private void removePlayerUUID(io.javalin.http.Context ctx) {
        try {
            JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            String playerUUID = body.get("uuid").getAsString();

            UUID uuid = UUID.fromString(playerUUID);

            boolean success = queueListService.removePlayerFromQueueUUID(uuid);
            Map<String, Object> response = createRemoveResponse(success, playerUUID);
            ctx.status(success ? 200 : 400).json(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: {}", e.getMessage());
            ctx.status(400).json(createErrorResponse("Invalid UUID format"));
        } catch (Exception e) {
            logger.error("Error removing player from queue by UUID", e);
            ctx.status(500).json(createErrorResponse("Internal error"));
        }
    }

    private Map<String, Object> createRemoveResponse(boolean success, String playerName) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);

        if (success) {
            response.put("message", "Player " + playerName + " removed from queue");
        } else {
            response.put("message", "Player " + playerName + " not found in queue");
        }

        return response;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }
}