package com.mckube.javaplugin.services;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mckube.javaplugin.models.ManagedServer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

import net.kyori.adventure.text.Component;

import org.bson.Document;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerManagementService {
    private final ProxyServer proxyServer;
    private final MongoCollection<Document> serverCollection;
    private final Logger logger;
    private LogsService logsService;

    public ServerManagementService(ProxyServer proxyServer, MongoClient mongoClient, Logger logger) {
        this.proxyServer = proxyServer;
        this.serverCollection = mongoClient.getDatabase("mc_kube").getCollection("servers");
        this.logger = logger;
    }

    public void setLogsService(LogsService logsService) {
        this.logsService = logsService;
    }

    public void loadServersOnStartup() {
        try {
            int loadedCount = 0;
            List<String> loadedServers = new ArrayList<>();
            
            for (Document doc : serverCollection.find(Filters.eq("enabled", true))) {
                String name = doc.getString("name");
                String ip = doc.getString("ip");
                int port = doc.getInteger("port");

                ServerInfo info = new ServerInfo(name, new InetSocketAddress(ip, port));
                proxyServer.registerServer(info);
                logger.info("Registered server on startup: {} ({}:{})", name, ip, port);
                
                loadedServers.add(name + " (" + ip + ":" + port + ")");
                loadedCount++;
            }
            
            logger.info("Loaded {} enabled servers from MongoDB on startup", loadedCount);

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("servers_loaded", loadedCount);
                metadata.put("loaded_servers", loadedServers);
                metadata.put("startup_time", Instant.now().toString());
                metadata.put("database", "mc_kube");
                metadata.put("collection", "servers");
                logsService.logSystemEvent("Servers loaded on startup", metadata);
            }

        } catch (Exception e) {
            logger.error("Error loading servers from MongoDB on startup", e);
            
            if (logsService != null) {
                Map<String, Object> errorMetadata = new HashMap<>();
                errorMetadata.put("operation", "loadServersOnStartup");
                errorMetadata.put("database", "mc_kube");
                errorMetadata.put("collection", "servers");
                logsService.logError("Failed to load servers on startup", "ServerManagementService.loadServersOnStartup", e);
            }
        }
    }

    public boolean addServer(String name, String ip, int port, int maxPlayers) {
        long startTime = System.currentTimeMillis();
        
        try {
            
            Document existing = serverCollection.find(Filters.eq("name", name)).first();
            if (existing != null) {
                logger.warn("Attempted to add server that already exists: {}", name);
                
                if (logsService != null) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("server_name", name);
                    metadata.put("server_ip", ip);
                    metadata.put("server_port", port);
                    metadata.put("operation_time", Instant.now().toString());
                    metadata.put("failure_reason", "Server already exists");
                    logsService.logServerOperation("Failed to add server - already exists", null, null, name, metadata);
                }
                
                return false;
            }

            
            Document serverDoc = new Document()
                    .append("name", name)
                    .append("ip", ip)
                    .append("port", port)
                    .append("maxPlayers", maxPlayers)
                    .append("enabled", true)
                    .append("createdAt", Instant.now().toString())
                    .append("lastModified", Instant.now().toString());

            serverCollection.insertOne(serverDoc);

            
            ServerInfo info = new ServerInfo(name, new InetSocketAddress(ip, port));
            proxyServer.registerServer(info);

            long operationTime = System.currentTimeMillis() - startTime;
            logger.info("Added and registered server: {} ({}:{}) in {}ms", name, ip, port, operationTime);

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("server_name", name);
                metadata.put("server_ip", ip);
                metadata.put("server_port", port);
                metadata.put("max_players", maxPlayers);
                metadata.put("operation_time", Instant.now().toString());
                metadata.put("operation_duration_ms", operationTime);
                metadata.put("enabled", true);
                metadata.put("database_operation", "insertOne");
                metadata.put("velocity_operation", "registerServer");
                logsService.logServerOperation("Server added successfully", null, null, name, metadata);
            }

            return true;

        } catch (Exception e) {
            long operationTime = System.currentTimeMillis() - startTime;
            logger.error("Error adding server: {} in {}ms", name, operationTime, e);
            
            if (logsService != null) {
                Map<String, Object> errorMetadata = new HashMap<>();
                errorMetadata.put("server_name", name);
                errorMetadata.put("server_ip", ip);
                errorMetadata.put("server_port", port);
                errorMetadata.put("max_players", maxPlayers);
                errorMetadata.put("operation_duration_ms", operationTime);
                errorMetadata.put("operation", "addServer");
                logsService.logError("Failed to add server", "ServerManagementService.addServer", e);
            }
            
            return false;
        }
    }

    public boolean removeServer(String name) {
    long startTime = System.currentTimeMillis();
    
    try {
        
        Document existing = serverCollection.find(Filters.eq("name", name)).first();
        if (existing == null) {
            logger.warn("Attempted to remove server that doesn't exist: {}", name);
            
            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("server_name", name);
                metadata.put("operation_time", Instant.now().toString());
                metadata.put("failure_reason", "Server does not exist");
                logsService.logServerOperation("Failed to remove server - does not exist", null, null, name, metadata);
            }
            
            return false;
        }

        
        String serverIp = existing.getString("ip");
        Integer serverPort = existing.getInteger("port");
        String createdBy = existing.getString("createdBy");
        String createdAt = existing.getString("createdAt");

        
        int kickedPlayers = kickAllPlayersFromServer(name, "Server is being removed");

        
        try {
            var server = proxyServer.getServer(name);
            if (server.isPresent()) {
                proxyServer.unregisterServer(server.get().getServerInfo());
            }
        } catch (Exception velocityException) {
            logger.warn("Error unregistering server from Velocity: {}", name, velocityException);
        }

        
        serverCollection.deleteOne(Filters.eq("name", name));

        long operationTime = System.currentTimeMillis() - startTime;
        logger.info("Removed and unregistered server: {} in {}ms, kicked {} players", name, operationTime, kickedPlayers);

        if (logsService != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("server_name", name);
            metadata.put("server_ip", serverIp);
            metadata.put("server_port", serverPort);
            metadata.put("created_by", createdBy);
            metadata.put("created_at", createdAt);
            metadata.put("kicked_players", kickedPlayers);
            metadata.put("operation_time", Instant.now().toString());
            metadata.put("operation_duration_ms", operationTime);
            metadata.put("database_operation", "deleteOne");
            metadata.put("velocity_operation", "unregisterServer");
            logsService.logServerOperation("Server removed successfully", null, null, name, metadata);
        }

        return true;

    } catch (Exception e) {
        long operationTime = System.currentTimeMillis() - startTime;
        logger.error("Error removing server: {} in {}ms", name, operationTime, e);
        
        if (logsService != null) {
            Map<String, Object> errorMetadata = new HashMap<>();
            errorMetadata.put("server_name", name);
            errorMetadata.put("operation_duration_ms", operationTime);
            errorMetadata.put("operation", "removeServer");
            logsService.logError("Failed to remove server", "ServerManagementService.removeServer", e);
        }
        
        return false;
    }
}

    public boolean enableServer(String name) {
        long startTime = System.currentTimeMillis();
        
        try {
            Document existing = serverCollection.find(Filters.eq("name", name)).first();
            if (existing == null) {
                logger.warn("Attempted to enable server that doesn't exist: {}", name);
                
                if (logsService != null) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("server_name", name);
                    metadata.put("operation_time", Instant.now().toString());
                    metadata.put("failure_reason", "Server does not exist");
                    logsService.logServerOperation("Failed to enable server - does not exist", null, null, name, metadata);
                }
                
                return false;
            }

            
            Boolean currentlyEnabled = existing.getBoolean("enabled");
            if (Boolean.TRUE.equals(currentlyEnabled)) {
                logger.info("Server {} is already enabled", name);
                
                if (logsService != null) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("server_name", name);
                    metadata.put("operation_time", Instant.now().toString());
                    metadata.put("previous_state", "enabled");
                    logsService.logServerOperation("Server enable requested but already enabled", null, null, name, metadata);
                }
                
                return true;
            }

            
            serverCollection.updateOne(
                    Filters.eq("name", name),
                    Updates.combine(
                            Updates.set("enabled", true),
                            Updates.set("lastModified", Instant.now().toString()),
                            Updates.set("lastEnabledAt", Instant.now().toString())
                    )
            );

            
            String ip = existing.getString("ip");
            int port = existing.getInteger("port");
            ServerInfo info = new ServerInfo(name, new InetSocketAddress(ip, port));
            proxyServer.registerServer(info);

            long operationTime = System.currentTimeMillis() - startTime;
            logger.info("Enabled and registered server: {} ({}:{}) in {}ms", name, ip, port, operationTime);

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("server_name", name);
                metadata.put("server_ip", ip);
                metadata.put("server_port", port);
                metadata.put("operation_time", Instant.now().toString());
                metadata.put("operation_duration_ms", operationTime);
                metadata.put("previous_state", "disabled");
                metadata.put("new_state", "enabled");
                metadata.put("database_operation", "updateOne");
                metadata.put("velocity_operation", "registerServer");
                logsService.logServerOperation("Server enabled successfully", null, null, name, metadata);
            }

            return true;

        } catch (Exception e) {
            long operationTime = System.currentTimeMillis() - startTime;
            logger.error("Error enabling server: {} in {}ms", name, operationTime, e);
            
            if (logsService != null) {
                Map<String, Object> errorMetadata = new HashMap<>();
                errorMetadata.put("server_name", name);
                errorMetadata.put("operation_duration_ms", operationTime);
                errorMetadata.put("operation", "enableServer");
                logsService.logError("Failed to enable server", "ServerManagementService.enableServer", e);
            }
            
            return false;
        }
    }

    public boolean disableServer(String name) {
    long startTime = System.currentTimeMillis();
    
    try {
        Document existing = serverCollection.find(Filters.eq("name", name)).first();
        if (existing == null) {
            logger.warn("Attempted to disable server that doesn't exist: {}", name);
            
            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("server_name", name);
                metadata.put("operation_time", Instant.now().toString());
                metadata.put("failure_reason", "Server does not exist");
                logsService.logServerOperation("Failed to disable server - does not exist", null, null, name, metadata);
            }
            
            return false;
        }

        
        Boolean currentlyEnabled = existing.getBoolean("enabled");
        if (Boolean.FALSE.equals(currentlyEnabled)) {
            logger.info("Server {} is already disabled", name);
            
            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("server_name", name);
                metadata.put("operation_time", Instant.now().toString());
                metadata.put("previous_state", "disabled");
                logsService.logServerOperation("Server disable requested but already disabled", null, null, name, metadata);
            }
            
            return true;
        }

        String ip = existing.getString("ip");
        Integer port = existing.getInteger("port");

        
        int kickedPlayers = kickAllPlayersFromServer(name, "Server is being disabled");

        
        serverCollection.updateOne(
                Filters.eq("name", name),
                Updates.combine(
                        Updates.set("enabled", false),
                        Updates.set("lastModified", Instant.now().toString()),
                        Updates.set("lastDisabledAt", Instant.now().toString())
                )
        );

        
        try {
            var server = proxyServer.getServer(name);
            if (server.isPresent()) {
                proxyServer.unregisterServer(server.get().getServerInfo());
            }
        } catch (Exception velocityException) {
            logger.warn("Error unregistering server from Velocity: {}", name, velocityException);
        }

        long operationTime = System.currentTimeMillis() - startTime;
        logger.info("Disabled and unregistered server: {} in {}ms, kicked {} players", name, operationTime, kickedPlayers);

        if (logsService != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("server_name", name);
            metadata.put("server_ip", ip);
            metadata.put("server_port", port);
            metadata.put("kicked_players", kickedPlayers);
            metadata.put("operation_time", Instant.now().toString());
            metadata.put("operation_duration_ms", operationTime);
            metadata.put("previous_state", "enabled");
            metadata.put("new_state", "disabled");
            metadata.put("database_operation", "updateOne");
            metadata.put("velocity_operation", "unregisterServer");
            logsService.logServerOperation("Server disabled successfully", null, null, name, metadata);
        }

        return true;

    } catch (Exception e) {
        long operationTime = System.currentTimeMillis() - startTime;
        logger.error("Error disabling server: {} in {}ms", name, operationTime, e);
        
        if (logsService != null) {
            Map<String, Object> errorMetadata = new HashMap<>();
            errorMetadata.put("server_name", name);
            errorMetadata.put("operation_duration_ms", operationTime);
            errorMetadata.put("operation", "disableServer");
            logsService.logError("Failed to disable server", "ServerManagementService.disableServer", e);
        }
        
        return false;
    }
}

    public List<Map<String, Object>> getAllServers() {
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> servers = new ArrayList<>();
        
        try {
            for (Document doc : serverCollection.find()) {
                Map<String, Object> server = new HashMap<>();
                server.put("name", doc.getString("name"));
                server.put("ip", doc.getString("ip"));
                server.put("port", doc.getInteger("port"));
                server.put("maxPlayers", doc.getInteger("maxPlayers"));
                server.put("enabled", doc.getBoolean("enabled"));
                server.put("createdAt", doc.getString("createdAt"));
                server.put("lastModified", doc.getString("lastModified"));
                server.put("createdBy", doc.getString("createdBy"));
                server.put("lastEnabledBy", doc.getString("lastEnabledBy"));
                server.put("lastDisabledBy", doc.getString("lastDisabledBy"));
                servers.add(server);
            }
            
            long operationTime = System.currentTimeMillis() - startTime;
            logger.debug("Retrieved {} servers from MongoDB in {}ms", servers.size(), operationTime);
            
            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("servers_count", servers.size());
                metadata.put("operation_time", Instant.now().toString());
                metadata.put("operation_duration_ms", operationTime);
                metadata.put("database_operation", "find");
                logsService.logSystemEvent("Server list retrieved", metadata);
            }
            
        } catch (Exception e) {
            long operationTime = System.currentTimeMillis() - startTime;
            logger.error("Error retrieving servers from MongoDB in {}ms", operationTime, e);
            
            if (logsService != null) {
                Map<String, Object> errorMetadata = new HashMap<>();
                errorMetadata.put("operation_duration_ms", operationTime);
                errorMetadata.put("operation", "getAllServers");
                logsService.logError("Failed to retrieve servers from database", "ServerManagementService.getAllServers", e);
            }
        }
        
        return servers;
    }

@param serverName
@param reason
@return

private int kickAllPlayersFromServer(String serverName, String reason) {
    try {
        var serverOptional = proxyServer.getServer(serverName);
        if (serverOptional.isEmpty()) {
            logger.debug("Server '{}' not found in Velocity, no players to kick", serverName);
            return 0;
        }

        var server = serverOptional.get();
        var playersOnServer = server.getPlayersConnected();
        
        if (playersOnServer.isEmpty()) {
            logger.debug("No players on server '{}' to kick", serverName);
            return 0;
        }
        
        Component kickMessage = Component.text(reason, net.kyori.adventure.text.format.NamedTextColor.RED);
        
        int kickedCount = 0;
        for (var player : playersOnServer) {
            try {
                boolean movedToLobby = movePlayerToLobby(player, kickMessage);
                
                if (!movedToLobby) {
                    
                    player.disconnect(kickMessage);
                }
                
                kickedCount++;
                logger.debug("Kicked player '{}' from server '{}'", player.getUsername(), serverName);
                
                if (logsService != null) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("reason", reason);
                    metadata.put("moved_to_lobby", movedToLobby);
                    metadata.put("kick_time", Instant.now().toString());
                    logsService.logServerConnect("Player kicked from server", 
                            player.getUsername(), 
                            player.getUniqueId().toString(), 
                            serverName, 
                            metadata);
                } 
            } catch (Exception playerException) {
                logger.warn("Failed to kick player '{}' from server '{}': {}", 
                        player.getUsername(), serverName, playerException.getMessage());
            }
        }
        
        logger.info("Kicked {} players from server '{}'", kickedCount, serverName);
        return kickedCount;
        
    } catch (Exception e) {
        logger.error("Error kicking players from server '{}': {}", serverName, e.getMessage());
        return 0;
    }
}

@param player
@param kickMessage
@return

private boolean movePlayerToLobby(com.velocitypowered.api.proxy.Player player, Component kickMessage) {
    try {
        
        String[] lobbyNames = {"queue"};
        
        for (String lobbyName : lobbyNames) {
            var lobbyServer = proxyServer.getServer(lobbyName);
            if (lobbyServer.isPresent()) {
                
                player.createConnectionRequest(lobbyServer.get()).fireAndForget();
                logger.debug("Moved player '{}' to lobby server '{}'", player.getUsername(), lobbyName);
                return true;
            }
        }
        
        var availableServers = proxyServer.getAllServers();
        for (var server : availableServers) {
            
            if (!server.getServerInfo().getName().equals(player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(""))) {
                player.createConnectionRequest(server).fireAndForget();
                logger.debug("Moved player '{}' to available server '{}'", player.getUsername(), server.getServerInfo().getName());
                return true;
            }
        }
        
        return false;
        
    } catch (Exception e) {
        logger.debug("Failed to move player '{}' to lobby: {}", player.getUsername(), e.getMessage());
        return false;
    }
}
}