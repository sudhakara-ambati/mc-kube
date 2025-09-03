package com.mckube.javaplugin.controllers;

import com.mckube.javaplugin.services.LogsService;
import com.mckube.javaplugin.utils.ControllerUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

public class LogsController {

    private final LogsService logsService;
    private final Logger logger;

    public LogsController(LogsService logsService, Logger logger) {
        this.logsService = logsService;
        this.logger = logger;
    }

    public void registerRoutes(Javalin app) {
        app.get("/cluster/logs", this::getClusterLogs);
        app.get("/cluster/logs/stats", this::getLogStatistics);
        app.delete("/cluster/logs", this::clearLogs);
    }

    private void getClusterLogs(Context ctx) {
        try {
            
            int limit = parseIntParam(ctx, "limit", 50, 1, 500);
            String typeParam = ctx.queryParam("type");
            String playerParam = ctx.queryParam("player");
            String sinceParam = ctx.queryParam("since");

            LogsService.LogType filterType = null;
            if (typeParam != null && !typeParam.trim().isEmpty()) {
                try {
                    filterType = LogsService.LogType.valueOf(typeParam.toUpperCase());
                } catch (IllegalArgumentException e) {
                    ctx.status(400).json(ControllerUtils.createErrorResponse(
                            "Invalid type parameter. Valid types: " + String.join(", ", getValidLogTypes())));
                    return;
                }
            }
            
            Instant since = null;
            if (sinceParam != null && !sinceParam.trim().isEmpty()) {
                try {
                    since = Instant.parse(sinceParam);
                } catch (DateTimeParseException e) {
                    ctx.status(400).json(ControllerUtils.createErrorResponse(
                            "Invalid since parameter. Use ISO-8601 format (e.g., 2023-12-01T10:00:00Z)"));
                    return;
                }
            }
            
            List<Map<String, Object>> events = logsService.getRecentEvents(limit, filterType, playerParam, since);
            
            Map<String, Object> response = ControllerUtils.createSuccessResponse("Cluster logs retrieved successfully");
            response.put("events", events);
            response.put("count", events.size());
            response.put("limit", limit);
            
            if (filterType != null) {
                response.put("filter_type", filterType.name().toLowerCase());
            }
            if (playerParam != null) {
                response.put("filter_player", playerParam);
            }
            if (since != null) {
                response.put("filter_since", since.toString());
            }

            ctx.status(200).json(response);

        } catch (NumberFormatException e) {
            logger.error("Invalid number parameter in logs request", e);
            ctx.status(400).json(ControllerUtils.createErrorResponse("Invalid number parameter"));
        } catch (Exception e) {
            logger.error("Error retrieving cluster logs", e);
            logsService.logError("Error retrieving cluster logs via API", "LogsController.getClusterLogs", e);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Error retrieving cluster logs"));
        }
    }

    private void getLogStatistics(Context ctx) {
        try {
            Map<String, Object> stats = logsService.getLogStatistics();
            Map<String, Object> response = ControllerUtils.createSuccessResponse("Log statistics retrieved successfully");
            response.put("statistics", stats);

            ctx.status(200).json(response);

        } catch (Exception e) {
            logger.error("Error retrieving log statistics", e);
            logsService.logError("Error retrieving log statistics via API", "LogsController.getLogStatistics", e);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Error retrieving log statistics"));
        }
    }

    private void clearLogs(Context ctx) {
        try {
            logsService.clearEvents();

            Map<String, Object> response = ControllerUtils.createSuccessResponse("All log events cleared successfully");
            ctx.status(200).json(response);

        } catch (Exception e) {
            logger.error("Error clearing log events", e);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Error clearing log events"));
        }
    }

    private int parseIntParam(Context ctx, String paramName, int defaultValue, int min, int max) {
        String paramValue = ctx.queryParam(paramName);
        if (paramValue == null || paramValue.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            int value = Integer.parseInt(paramValue);
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid " + paramName + " parameter: " + paramValue);
        }
    }

    private String[] getValidLogTypes() {
        LogsService.LogType[] types = LogsService.LogType.values();
        String[] typeNames = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            typeNames[i] = types[i].name().toLowerCase();
        }
        return typeNames;
    }
}