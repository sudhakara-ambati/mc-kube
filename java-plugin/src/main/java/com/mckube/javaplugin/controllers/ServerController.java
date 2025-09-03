package com.mckube.javaplugin.controllers;

import com.mckube.javaplugin.services.ServerListService;
import com.mckube.javaplugin.utils.ControllerUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ServerController {

    private final ServerListService serverListService;
    private final Logger logger;

    public ServerController(ServerListService serverListService, Logger logger) {
        this.serverListService = serverListService;
        this.logger = logger;
    }

    public void registerRoutes(Javalin app) {
        app.get("/server/list", this::handleServerList);
        app.get("/server/summary", this::handleServerSummary);
        app.get("/server/status", this::handleServerStatus);
        app.get("/server/players", this::handlePlayerCounts);
        app.get("/server/{name}", this::handleServerDetails);
    }

    private void handleServerList(Context ctx) {
        CompletableFuture<Void> future = serverListService.getAllServersWithStatus()
                .thenAccept(servers -> {
                    try {
                        List<Map<String, Object>> basicServerInfo = servers.stream()
                                .map(server -> {
                                    Map<String, Object> info = new HashMap<>();
                                    info.put("name", server.getName());
                                    info.put("host", server.getHost());
                                    info.put("port", server.getPort());
                                    info.put("status", server.getStatus());
                                    info.put("enabled", server.isEnabled()); 
                                    info.put("currentPlayers", server.getCurrentPlayers());
                                    info.put("maxPlayers", server.getMaxPlayers());
                                    info.put("loadStatus", server.getLoadStatus());
                                    return info;
                                })
                                .toList();

                        Map<String, Object> response = ControllerUtils.createSuccessResponse("Server list retrieved successfully");
                        response.put("servers", basicServerInfo);
                        response.put("totalServers", servers.size());

                        ctx.status(200).json(response);
                    } catch (Exception e) {
                        logger.error("Error building server list response", e);
                        ctx.status(500).json(ControllerUtils.createErrorResponse("Error building response"));
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error getting server list", throwable);
                    ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to retrieve server list"));
                    return null;
                });

        ctx.future(() -> future);
    }

    private void handleServerSummary(Context ctx) {
        CompletableFuture<Void> future = serverListService.getAllServersWithStatus()
                .thenAccept(servers -> {
                    try {
                        Map<String, Object> summary = buildSummary(servers);
                        Map<String, Object> response = ControllerUtils.createSuccessResponse("Server summary retrieved successfully");
                        response.putAll(summary);

                        ctx.status(200).json(response);
                    } catch (Exception e) {
                        logger.error("Error building summary response", e);
                        ctx.status(500).json(ControllerUtils.createErrorResponse("Error building summary"));
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error getting server summary", throwable);
                    ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to retrieve server summary"));
                    return null;
                });

        ctx.future(() -> future);
    }

    private void handleServerDetails(Context ctx) {
        String serverName = ctx.pathParam("name");

        CompletableFuture<Void> future = serverListService.getServerByName(serverName)
                .thenAccept(serverOpt -> {
                    try {
                        if (serverOpt.isEmpty()) {
                            ctx.status(404).json(ControllerUtils.createErrorResponse("Server not found: " + serverName));
                            return;
                        }

                        ServerListService.ServerStatus server = serverOpt.get();
                        Map<String, Object> response = ControllerUtils.createSuccessResponse("Server details retrieved successfully");
                        
                        response.put("name", server.getName());
                        response.put("host", server.getHost());
                        response.put("port", server.getPort());
                        response.put("status", server.getStatus());
                        response.put("currentPlayers", server.getCurrentPlayers());
                        response.put("maxPlayers", server.getMaxPlayers());
                        response.put("latency", server.getLatency());
                        response.put("version", server.getVersion());
                        response.put("motd", server.getMotd());
                        response.put("loadPercentage", server.getLoadPercentage());
                        response.put("loadStatus", server.getLoadStatus());
                        response.put("isHealthy", server.isHealthy());
                        response.put("serverTimestamp", server.getTimestamp());

                        ctx.status(200).json(response);
                    } catch (Exception e) {
                        logger.error("Error building server details response", e);
                        ctx.status(500).json(ControllerUtils.createErrorResponse("Error building server details"));
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error getting server details for: " + serverName, throwable);
                    ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to retrieve server details"));
                    return null;
                });

        ctx.future(() -> future);
    }

    private void handleServerStatus(Context ctx) {
        CompletableFuture<Void> future = serverListService.getBasicServerInfo()
                .thenAccept(servers -> {
                    try {
                        Map<String, Object> statusInfo = new HashMap<>();
                        for (ServerListService.BasicServerInfo server : servers) {
                            Map<String, Object> info = new HashMap<>();
                            info.put("host", server.getHost());
                            info.put("port", server.getPort());
                            info.put("currentPlayers", server.getCurrentPlayers());
                            statusInfo.put(server.getName(), info);
                        }

                        Map<String, Object> response = ControllerUtils.createSuccessResponse("Server status retrieved successfully");
                        response.put("servers", statusInfo);

                        ctx.status(200).json(response);
                    } catch (Exception e) {
                        logger.error("Error building status response", e);
                        ctx.status(500).json(ControllerUtils.createErrorResponse("Error building status"));
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error getting server status", throwable);
                    ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to retrieve server status"));
                    return null;
                });

        ctx.future(() -> future);
    }

    private void handlePlayerCounts(Context ctx) {
        CompletableFuture<Void> future = serverListService.getOnlineServersOnly()
                .thenAccept(servers -> {
                    try {
                        List<Map<String, Object>> playerInfo = servers.stream()
                                .map(server -> {
                                    Map<String, Object> info = new HashMap<>();
                                    info.put("name", server.getName());
                                    info.put("currentPlayers", server.getCurrentPlayers());
                                    info.put("maxPlayers", server.getMaxPlayers());
                                    info.put("loadPercentage", server.getLoadPercentage());
                                    return info;
                                })
                                .toList();

                        int totalPlayers = servers.stream()
                                .mapToInt(ServerListService.ServerStatus::getCurrentPlayers)
                                .sum();

                        int totalMaxPlayers = servers.stream()
                                .mapToInt(ServerListService.ServerStatus::getMaxPlayers)
                                .sum();

                        Map<String, Object> response = ControllerUtils.createSuccessResponse("Player counts retrieved successfully");
                        response.put("servers", playerInfo);
                        response.put("totalPlayers", totalPlayers);
                        response.put("totalMaxPlayers", totalMaxPlayers);
                        response.put("networkLoadPercentage", totalMaxPlayers > 0 ? (double) totalPlayers / totalMaxPlayers * 100.0 : 0.0);

                        ctx.status(200).json(response);
                    } catch (Exception e) {
                        logger.error("Error building player counts response", e);
                        ctx.status(500).json(ControllerUtils.createErrorResponse("Error building player counts"));
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error getting player counts", throwable);
                    ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to retrieve player counts"));
                    return null;
                });

        ctx.future(() -> future);
    }

    private Map<String, Object> buildSummary(List<ServerListService.ServerStatus> servers) {
        long enabledServers = servers.stream()
                .filter(ServerListService.ServerStatus::isEnabled)
                .count();
        
        long onlineServers = servers.stream()
                .filter(s -> "online".equals(s.getStatus()))
                .count();

        int totalPlayers = servers.stream()
                .filter(ServerListService.ServerStatus::isEnabled) 
                .mapToInt(ServerListService.ServerStatus::getCurrentPlayers)
                .sum();

        long healthyServers = servers.stream()
                .filter(ServerListService.ServerStatus::isHealthy)
                .count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalServers", servers.size());
        summary.put("enabledServers", enabledServers);
        summary.put("disabledServers", servers.size() - enabledServers);
        summary.put("onlineServers", onlineServers);
        summary.put("offlineServers", enabledServers - onlineServers); 
        summary.put("healthyServers", healthyServers);
        summary.put("totalPlayers", totalPlayers);
        return summary;
    }
}