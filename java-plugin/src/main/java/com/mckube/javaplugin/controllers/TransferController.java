package com.mckube.javaplugin.controllers;

import com.google.gson.JsonObject;
import com.mckube.javaplugin.services.TransferService;
import com.mckube.javaplugin.utils.ControllerUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TransferController {

    private final TransferService transferService;
    private final Logger logger;

    public TransferController(TransferService transferService, Logger logger) {
        this.transferService = transferService;
        this.logger = logger;
    }

    public void registerRoutes(Javalin app) {
        app.post("/transfer", this::handleTransfer);
    }

    private void handleTransfer(Context ctx) {
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                TransferRequest request = parseAndValidateRequest(ctx);
                if (request == null) return null; // Error already set in context

                boolean success = transferService.transferPlayer(request.playerName, request.serverName);
                Map<String, Object> response = createTransferResponse(success, request.playerName, request.serverName);
                ctx.status(success ? 200 : 404).json(response);
                return null;

            } catch (Exception e) {
                logger.error("Error handling transfer request", e);
                ctx.status(500).json(ControllerUtils.createErrorResponse("Internal server error while transferring player"));
                return null;
            }
        }).exceptionally(throwable -> {
            logger.error("Unexpected error in transfer handler", throwable);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to transfer player"));
            return null;
        });

        ctx.future(() -> future);
    }

    private TransferRequest parseAndValidateRequest(Context ctx) {
        JsonObject jsonObject = ControllerUtils.parseAndValidateRequestBody(ctx);
        if (jsonObject == null) return null; // Error already set in context

        // Extract and validate fields using the utils
        String playerName = ControllerUtils.validateJsonField(ctx, jsonObject, "player", "Player name");
        if (playerName == null) return null; // Error already set in context

        String serverName = ControllerUtils.validateJsonField(ctx, jsonObject, "server", "Server name");
        if (serverName == null) return null; // Error already set in context

        return new TransferRequest(playerName, serverName);
    }

    private Map<String, Object> createTransferResponse(boolean success, String playerName, String serverName) {
        String message = success
                ? "Successfully transferred " + playerName + " to " + serverName
                : "Failed to transfer player - player or server not found";

        Map<String, Object> response = success
                ? ControllerUtils.createSuccessResponse(message)
                : ControllerUtils.createErrorResponse(message);

        response.put("playerName", playerName);
        response.put("targetServer", serverName);

        return response;
    }

    private static class TransferRequest {
        final String playerName;
        final String serverName;

        TransferRequest(String playerName, String serverName) {
            this.playerName = playerName;
            this.serverName = serverName;
        }
    }
}