package com.mckube.javaplugin.controllers;

import com.mckube.javaplugin.services.ServerListService;
import com.mckube.javaplugin.utils.ControllerUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;

import java.util.ArrayList;
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
        app.get("/server/overview", this::handleServerOverview);
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
                                    info.put("loadPercentage", server.getLoadPercentage());
                                    info.put("loadStatus", server.getLoadStatus());
                                    info.put("isHealthy", server.isHealthy());
                                    info.put("latency", server.getLatency());
                                    info.put("version", server.getVersion());
                                    info.put("motd", server.getMotd());
                                    info.put("serverTimestamp", server.getTimestamp());
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

    private void handleServerOverview(Context ctx) {
        CompletableFuture<Void> future = serverListService.getAllServersWithStatus()
                .thenAccept(servers -> {
                    try {
                        
                        List<ServerListService.ServerStatus> onlineServers = new ArrayList<>();
                        List<ServerListService.ServerStatus> enabledServers = new ArrayList<>();
                        int totalPlayers = 0;
                        int totalMaxPlayers = 0;
                        long healthyCount = 0;

                        
                        for (ServerListService.ServerStatus server : servers) {
                            if (server.isEnabled()) {
                                enabledServers.add(server);
                                totalPlayers += server.getCurrentPlayers();
                            }
                            
                            if ("online".equals(server.getStatus())) {
                                onlineServers.add(server);
                                totalMaxPlayers += server.getMaxPlayers();
                            }
                            
                            if (server.isHealthy()) {
                                healthyCount++;
                            }
                        }

                        
                        Map<String, Object> summary = buildSummaryOptimized(servers, enabledServers, onlineServers, healthyCount, totalPlayers);
                        
                        
                        List<Map<String, Object>> playerInfo = servers.stream()
                                .map(server -> {
                                    Map<String, Object> info = new HashMap<>();
                                    info.put("name", server.getName());
                                    info.put("currentPlayers", server.getCurrentPlayers());
                                    info.put("maxPlayers", server.getMaxPlayers());
                                    info.put("loadPercentage", server.getLoadPercentage());
                                    info.put("loadStatus", server.getLoadStatus());
                                    info.put("status", server.getStatus()); 
                                    info.put("enabled", server.isEnabled()); 
                                    info.put("isHealthy", server.isHealthy()); 
                                    return info;
                                })
                                .toList();

                        double networkLoadPercentage = totalMaxPlayers > 0 ? (double) totalPlayers / totalMaxPlayers * 100.0 : 0.0;

                        Map<String, Object> response = ControllerUtils.createSuccessResponse("Server overview retrieved successfully");
                        response.putAll(summary);
                        response.put("playerDetails", playerInfo);
                        response.put("totalActivePlayers", totalPlayers);
                        response.put("totalMaxPlayers", totalMaxPlayers);
                        response.put("networkLoadPercentage", networkLoadPercentage);

                        ctx.status(200).json(response);
                    } catch (Exception e) {
                        logger.error("Error building server overview response", e);
                        ctx.status(500).json(ControllerUtils.createErrorResponse("Error building server overview"));
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Error getting server overview", throwable);
                    ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to retrieve server overview"));
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
                        response.put("enabled", server.isEnabled());
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
    
    
    private Map<String, Object> buildSummaryOptimized(List<ServerListService.ServerStatus> servers, 
                                                     List<ServerListService.ServerStatus> enabledServers,
                                                     List<ServerListService.ServerStatus> onlineServers,
                                                     long healthyCount, int totalPlayers) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalServers", servers.size());
        summary.put("enabledServers", enabledServers.size());
        summary.put("disabledServers", servers.size() - enabledServers.size());
        summary.put("onlineServers", onlineServers.size());
        summary.put("offlineServers", enabledServers.size() - onlineServers.size()); 
        summary.put("healthyServers", healthyCount);
        summary.put("totalPlayers", totalPlayers);
        return summary;
    }
}