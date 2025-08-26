package com.mckube.javaplugin.controllers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mckube.javaplugin.services.TransferService;
import io.javalin.Javalin;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class TransferController {

    private final TransferService transferService;
    private final Logger logger;

    public TransferController(TransferService transferService, Logger logger) {
        this.transferService = transferService;
        this.logger = logger;
    }

    public void registerRoutes(Javalin app) {
        app.post("/transfer", ctx -> {
            try {
                JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
                String playerName = body.get("player").getAsString();
                String serverName = body.get("server").getAsString();

                boolean success = transferService.transferPlayer(playerName, serverName);

                Map<String, Object> response = createResponse(success, playerName, serverName);
                ctx.status(success ? 200 : 400).json(response);

            } catch (Exception e) {
                logger.error("Error handling transfer request", e);
                ctx.status(500).json(createErrorResponse("Internal error"));
            }
        });
    }

    private Map<String, Object> createResponse(boolean success, String playerName, String serverName) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);

        if (success) {
            response.put("message", "Transferred " + playerName + " to " + serverName);
        } else {
            response.put("message", "Player or server not found");
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