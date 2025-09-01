package com.mckube.javaplugin.controllers;

import com.google.gson.JsonObject;
import com.mckube.javaplugin.services.BroadcastService;
import com.mckube.javaplugin.utils.ControllerUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BroadcastController {

    private final BroadcastService broadcastService;
    private final Logger logger;

    public BroadcastController(BroadcastService broadcastService, Logger logger) {
        this.broadcastService = broadcastService;
        this.logger = logger;
    }

    public void registerRoutes(Javalin app) {
        app.post("/broadcast", this::handleBroadcast);
        app.post("/broadcast/{server}", this::handleBroadcastToServer);
    }

    private void handleBroadcast(Context ctx) {
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject jsonObject = ControllerUtils.parseAndValidateRequestBody(ctx);
                if (jsonObject == null) return null; // Error already set in context

                String message = ControllerUtils.extractMessage(ctx, jsonObject);
                if (message == null) return null; // Error already set in context

                // Broadcast the message to all players
                broadcastService.broadcastMessage(message);

                // Create success response
                Map<String, Object> response = ControllerUtils.createSuccessResponse("Message broadcasted successfully to all players");
                response.put("broadcastMessage", message);

                ctx.status(200).json(response);
                return null;

            } catch (Exception e) {
                logger.error("Error handling broadcast request", e);
                ctx.status(500).json(ControllerUtils.createErrorResponse("Internal server error while broadcasting message"));
                return null;
            }
        }).exceptionally(throwable -> {
            logger.error("Unexpected error in broadcast handler", throwable);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to broadcast message"));
            return null;
        });

        ctx.future(() -> future);
    }

    private void handleBroadcastToServer(Context ctx) {
        String serverName = ctx.pathParam("server");

        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                // Validate server name
                if (!ControllerUtils.validatePathParam(ctx, serverName, "Server name")) {
                    return null; // Error already set in context
                }

                JsonObject jsonObject = ControllerUtils.parseAndValidateRequestBody(ctx);
                if (jsonObject == null) return null; // Error already set in context

                String message = ControllerUtils.extractMessage(ctx, jsonObject);
                if (message == null) return null; // Error already set in context

                // Broadcast the message to specific server
                broadcastService.broadcastMessageToServer(serverName, message);

                // Create success response
                Map<String, Object> response = ControllerUtils.createSuccessResponse("Message broadcasted successfully to server: " + serverName);
                response.put("targetServer", serverName);
                response.put("broadcastMessage", message);

                ctx.status(200).json(response);
                return null;

            } catch (Exception e) {
                logger.error("Error handling broadcast to server request for server: " + serverName, e);
                ctx.status(500).json(ControllerUtils.createErrorResponse("Internal server error while broadcasting message to server"));
                return null;
            }
        }).exceptionally(throwable -> {
            logger.error("Unexpected error in broadcast to server handler for server: " + serverName, throwable);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to broadcast message to server"));
            return null;
        });

        ctx.future(() -> future);
    }
}