package com.mckube.javaplugin.controllers;

import com.google.gson.JsonObject;
import com.mckube.javaplugin.services.ServerManagementService;
import com.mckube.javaplugin.utils.ControllerUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ServerManagementController {

    private final ServerManagementService serverService;
    private final Logger logger;

    public ServerManagementController(ServerManagementService serverService, Logger logger) {
        this.serverService = serverService;
        this.logger = logger;
    }

    public void registerRoutes(Javalin app) {
        app.post("/server/add", this::handleAddServer);
        app.post("/server/remove", this::handleRemoveServer);
        app.delete("/server/remove", this::handleRemoveServer);
        app.post("/server/enable", this::handleEnableServer);
        app.post("/server/disable", this::handleDisableServer);
    }

    private void handleAddServer(Context ctx) {
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject jsonObject = ControllerUtils.parseAndValidateRequestBody(ctx);
                if (jsonObject == null) return null;

                String name = ControllerUtils.validateJsonField(ctx, jsonObject, "name", "Server name");
                if (name == null) return null;

                String ip = ControllerUtils.validateJsonField(ctx, jsonObject, "ip", "Server IP");
                if (ip == null) return null;

                if (!jsonObject.has("port")) {
                    ctx.status(400).json(ControllerUtils.createErrorResponse("Port field is required"));
                    return null;
                }

                int port;
                int maxPlayers = 100;

                try {
                    port = jsonObject.get("port").getAsInt();
                    if (port <= 0 || port > 65535) {
                        ctx.status(400).json(ControllerUtils.createErrorResponse("Port must be between 1 and 65535"));
                        return null;
                    }
                } catch (Exception e) {
                    ctx.status(400).json(ControllerUtils.createErrorResponse("Port must be a valid integer"));
                    return null;
                }

                if (jsonObject.has("maxPlayers")) {
                    try {
                        maxPlayers = jsonObject.get("maxPlayers").getAsInt();
                        if (maxPlayers <= 0) {
                            ctx.status(400).json(ControllerUtils.createErrorResponse("Max players must be greater than 0"));
                            return null;
                        }
                    } catch (Exception e) {
                        ctx.status(400).json(ControllerUtils.createErrorResponse("Max players must be a valid integer"));
                        return null;
                    }
                }

                boolean success = serverService.addServer(name, ip, port, maxPlayers);
                if (!success) {
                    ctx.status(409).json(ControllerUtils.createErrorResponse("Server with name '" + name + "' already exists"));
                    return null;
                }

                Map<String, Object> response = ControllerUtils.createSuccessResponse("Server added successfully");
                response.put("serverName", name);
                response.put("serverIp", ip);
                response.put("serverPort", port);
                response.put("maxPlayers", maxPlayers);

                ctx.status(201).json(response);
                return null;

            } catch (Exception e) {
                logger.error("Error handling add server request", e);
                ctx.status(500).json(ControllerUtils.createErrorResponse("Internal server error while adding server"));
                return null;
            }
        }).exceptionally(throwable -> {
            logger.error("Unexpected error in add server handler", throwable);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to add server"));
            return null;
        });

        ctx.future(() -> future);
    }

    private void handleRemoveServer(Context ctx) {
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject jsonObject = ControllerUtils.parseAndValidateRequestBody(ctx);
                if (jsonObject == null) return null;

                String name = ControllerUtils.validateJsonField(ctx, jsonObject, "name", "Server name");
                if (name == null) return null;

                boolean success = serverService.removeServer(name);
                if (!success) {
                    ctx.status(404).json(ControllerUtils.createErrorResponse("Server with name '" + name + "' not found"));
                    return null;
                }

                Map<String, Object> response = ControllerUtils.createSuccessResponse("Server removed successfully");
                response.put("removedServer", name);

                ctx.status(200).json(response);
                return null;

            } catch (Exception e) {
                logger.error("Error handling remove server request", e);
                ctx.status(500).json(ControllerUtils.createErrorResponse("Internal server error while removing server"));
                return null;
            }
        }).exceptionally(throwable -> {
            logger.error("Unexpected error in remove server handler", throwable);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to remove server"));
            return null;
        });

        ctx.future(() -> future);
    }

    private void handleEnableServer(Context ctx) {
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject jsonObject = ControllerUtils.parseAndValidateRequestBody(ctx);
                if (jsonObject == null) return null;

                String name = ControllerUtils.validateJsonField(ctx, jsonObject, "name", "Server name");
                if (name == null) return null;

                boolean success = serverService.enableServer(name);
                if (!success) {
                    ctx.status(404).json(ControllerUtils.createErrorResponse("Server with name '" + name + "' not found"));
                    return null;
                }

                Map<String, Object> response = ControllerUtils.createSuccessResponse("Server enabled successfully");
                response.put("enabledServer", name);

                ctx.status(200).json(response);
                return null;

            } catch (Exception e) {
                logger.error("Error handling enable server request", e);
                ctx.status(500).json(ControllerUtils.createErrorResponse("Internal server error while enabling server"));
                return null;
            }
        }).exceptionally(throwable -> {
            logger.error("Unexpected error in enable server handler", throwable);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to enable server"));
            return null;
        });

        ctx.future(() -> future);
    }

    private void handleDisableServer(Context ctx) {
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject jsonObject = ControllerUtils.parseAndValidateRequestBody(ctx);
                if (jsonObject == null) return null;

                String name = ControllerUtils.validateJsonField(ctx, jsonObject, "name", "Server name");
                if (name == null) return null;

                boolean success = serverService.disableServer(name);
                if (!success) {
                    ctx.status(404).json(ControllerUtils.createErrorResponse("Server with name '" + name + "' not found"));
                    return null;
                }

                Map<String, Object> response = ControllerUtils.createSuccessResponse("Server disabled successfully");
                response.put("disabledServer", name);

                ctx.status(200).json(response);
                return null;

            } catch (Exception e) {
                logger.error("Error handling disable server request", e);
                ctx.status(500).json(ControllerUtils.createErrorResponse("Internal server error while disabling server"));
                return null;
            }
        }).exceptionally(throwable -> {
            logger.error("Unexpected error in disable server handler", throwable);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to disable server"));
            return null;
        });

        ctx.future(() -> future);
    }
}