package com.mckube.javaplugin.controllers;

import com.mckube.javaplugin.services.MetricsService;
import com.mckube.javaplugin.services.MetricsData;
import com.mckube.javaplugin.utils.ControllerUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MetricsController {

    private final MetricsService metricsService;
    private final Logger logger;

    public MetricsController(MetricsService metricsService, Logger logger) {
        this.metricsService = metricsService;
        this.logger = logger;
    }

    public void registerRoutes(Javalin app) {
        app.get("/metrics/{serverIp}", this::getMetrics);
        app.post("/metrics/{serverIp}", this::postMetrics);
    }

    private void getMetrics(Context ctx) {
        String serverIp = ctx.pathParam("serverIp");

        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                if (!ControllerUtils.validatePathParam(ctx, serverIp, "Server IP")) {
                    return null;
                }

                var data = metricsService.getMetrics(serverIp);
                if (data == null) {
                    ctx.status(404).json(ControllerUtils.createErrorResponse("Metrics not found for server IP: " + serverIp));
                    return null;
                }

                Map<String, Object> response = Map.ofEntries(
                        new AbstractMap.SimpleEntry<>("success", true),
                        new AbstractMap.SimpleEntry<>("server_ip", data.serverIp()),
                        new AbstractMap.SimpleEntry<>("timestamp", (data.timestamp())),
                        new AbstractMap.SimpleEntry<>("system_cpu_percent", round(data.systemCpuPercent())),
                        new AbstractMap.SimpleEntry<>("process_cpu_percent", round(data.processCpuPercent())),
                        new AbstractMap.SimpleEntry<>("memory_used_gb", round(data.memoryUsedGB())),
                        new AbstractMap.SimpleEntry<>("memory_max_gb", round(data.memoryMaxGB())),
                        new AbstractMap.SimpleEntry<>("memory_percent", round(data.memoryPercent())),
                        new AbstractMap.SimpleEntry<>("system_memory_used_gb", round(data.systemMemoryUsedGB())),
                        new AbstractMap.SimpleEntry<>("system_memory_total_gb", round(data.systemMemoryTotalGB())),
                        new AbstractMap.SimpleEntry<>("system_memory_percent", round(data.systemMemoryPercent())),
                        new AbstractMap.SimpleEntry<>("tps", round(data.tps(), 2)),
                        new AbstractMap.SimpleEntry<>("tps_percent", round(data.tpsPercent()))
                );

                ctx.status(200).json(response);
                return null;

            } catch (Exception e) {
                logger.error("Error retrieving metrics for server IP: " + serverIp, e);
                ctx.status(500).json(ControllerUtils.createErrorResponse("Internal server error while retrieving metrics"));
                return null;
            }
        }).exceptionally(throwable -> {
            logger.error("Unexpected error in metrics retrieval handler for server IP: " + serverIp, throwable);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to retrieve metrics"));
            return null;
        });

        ctx.future(() -> future);
    }

    private void postMetrics(Context ctx) {
        String serverIp = ctx.pathParam("serverIp");

        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                if (!ControllerUtils.validatePathParam(ctx, serverIp, "Server IP")) {
                    return null;
                }

                var body = ctx.bodyAsClass(MetricsData.class);
                if (body == null) {
                    ctx.status(400).json(ControllerUtils.createErrorResponse("Invalid or missing request body"));
                    return null;
                }

                metricsService.putMetrics(serverIp, body);

                Map<String, Object> response = ControllerUtils.createSuccessResponse("Metrics updated successfully for server: " + serverIp);
                response.put("server_ip", serverIp);

                ctx.status(200).json(response);
                return null;

            } catch (Exception e) {
                logger.error("Error updating metrics for server IP: " + serverIp, e);
                ctx.status(500).json(ControllerUtils.createErrorResponse("Internal server error while updating metrics"));
                return null;
            }
        }).exceptionally(throwable -> {
            logger.error("Unexpected error in metrics update handler for server IP: " + serverIp, throwable);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to update metrics"));
            return null;
        });

        ctx.future(() -> future);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round(double v, int d) {
        double factor = Math.pow(10, d);
        return Math.round(v * factor) / factor;
    }
}