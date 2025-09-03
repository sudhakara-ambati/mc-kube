package com.mckube.javaplugin.controllers;

import com.google.gson.JsonObject;
import com.mckube.javaplugin.services.QueueListService;
import com.mckube.javaplugin.utils.ControllerUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.time.Duration;

public class QueueController {

    private final QueueListService queueListService;
    private final Logger logger;
    
    
    private final Map<String, CachedQueueData> queueCache = new ConcurrentHashMap<>();
    private static final Duration CACHE_TTL = Duration.ofSeconds(2); 
    
    private static class CachedQueueData {
        final List<String> data;
        final Instant timestamp;
        final String type;
        
        CachedQueueData(List<String> data, String type) {
            this.data = new ArrayList<>(data); 
            this.type = type;
            this.timestamp = Instant.now();
        }
        
        boolean isExpired() {
            return Duration.between(timestamp, Instant.now()).compareTo(CACHE_TTL) > 0;
        }
    }

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
        CompletableFuture.supplyAsync(() -> {
            try {
                String type = ctx.queryParam("type");
                
                if (type == null || type.trim().isEmpty()) {
                    type = "username";
                }
                
                
                CachedQueueData cached = queueCache.get(type);
                if (cached != null && !cached.isExpired()) {
                    logger.debug("Serving queue list from cache for type: {}", type);
                    return createQueueResponse(cached.data, cached.type);
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
                
                
                queueCache.put(listType, new CachedQueueData(queueData, listType));
                
                return createQueueResponse(queueData, listType);

            } catch (Exception e) {
                logger.error("Error getting queue list", e);
                return ControllerUtils.createErrorResponse("Failed to retrieve queue list");
            }
        }).thenAccept(response -> {
            ctx.status(200).json(response);
        }).exceptionally(throwable -> {
            logger.error("Unexpected error in queue list handler", throwable);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Internal server error"));
            return null;
        });
    }
    
    private Map<String, Object> createQueueResponse(List<String> queueData, String listType) {
        Map<String, Object> response = ControllerUtils.createSuccessResponse("Queue list retrieved successfully");
        response.put("queueList", queueData);
        response.put("queueCount", queueData.size());
        response.put("listType", listType);
        return response;
    }

    private void getQueueCount(Context ctx) {
        CompletableFuture.supplyAsync(() -> {
            try {
                
                CachedQueueData usernameCache = queueCache.get("username");
                if (usernameCache != null && !usernameCache.isExpired()) {
                    logger.debug("Serving queue count from cache");
                    return usernameCache.data.size();
                }
                
                return queueListService.getQueueCount();
            } catch (Exception e) {
                logger.error("Error getting queue count", e);
                throw new RuntimeException("Failed to get queue count", e);
            }
        }).thenAccept(queueCount -> {
            Map<String, Object> response = ControllerUtils.createSuccessResponse("Queue count retrieved successfully");
            response.put("count", queueCount);
            ctx.status(200).json(response);
        }).exceptionally(throwable -> {
            logger.error("Unexpected error in queue count handler", throwable);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Error retrieving queue count"));
            return null;
        });
    }

    private void removePlayer(Context ctx) {
        try {
            JsonObject jsonObject = ControllerUtils.parseAndValidateRequestBody(ctx);
            if (jsonObject == null) return; 

            
            String type = ControllerUtils.validateJsonField(ctx, jsonObject, "type", "Type");
            if (type == null) return; 

            boolean success;
            String identifier;

            switch (type.toLowerCase()) {
                case "username":
                    identifier = ControllerUtils.validateJsonField(ctx, jsonObject, "player", "Player name");
                    if (identifier == null) return; 
                    success = queueListService.removePlayerFromQueueName(identifier);
                    break;

                case "uuid":
                    identifier = ControllerUtils.validateJsonField(ctx, jsonObject, "uuid", "UUID");
                    if (identifier == null) return; 

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
    
    
    public void invalidateCache() {
        queueCache.clear();
    }
    
    
    public void cleanupCache() {
        queueCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}