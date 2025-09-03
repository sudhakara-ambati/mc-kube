package com.mckube.javaplugin.services;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ServerListService {

    private final ProxyServer server;
    private final Logger logger;
    private LogsService logsService;
    private ServerManagementService serverManagementService; 

    public ServerListService(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public void setLogsService(LogsService logsService) {
        this.logsService = logsService;
    }

    public void setServerManagementService(ServerManagementService serverManagementService) {
        this.serverManagementService = serverManagementService;
    }

    public CompletableFuture<List<ServerStatus>> getAllServersWithStatus() {
        if (serverManagementService == null) {
            logger.error("ServerManagementService not set, falling back to registered servers only");
            return getAllRegisteredServersWithStatus();
        }

        try {
            
            List<Map<String, Object>> allServers = serverManagementService.getAllServers();
            List<CompletableFuture<ServerStatus>> futures = new ArrayList<>();
            Set<String> mongoServerNames = new java.util.HashSet<>();

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("total_servers", allServers.size());
                metadata.put("check_time", java.time.Instant.now().toString());
                metadata.put("server_names", allServers.stream()
                        .map(s -> s.get("name").toString()).toList());
                logsService.logServerStatusCheck("Server status check initiated for all servers", metadata);
            }

            
            for (Map<String, Object> serverData : allServers) {
                String serverName = (String) serverData.get("name");
                mongoServerNames.add(serverName);
                Boolean enabled = (Boolean) serverData.get("enabled");
                
                if (enabled != null && enabled) {
                    Optional<RegisteredServer> registeredServer = server.getServer(serverName);
                    if (registeredServer.isPresent()) {
                        futures.add(getServerStatus(registeredServer.get(), true));
                    } else {
                        futures.add(CompletableFuture.completedFuture(createOfflineServerStatus(serverData, true)));
                    }
                } else {
                    futures.add(CompletableFuture.completedFuture(createOfflineServerStatus(serverData, false)));
                }
            }

            for (RegisteredServer registeredServer : server.getAllServers()) {
                String serverName = registeredServer.getServerInfo().getName();
                if (!mongoServerNames.contains(serverName)) {
                    logger.info("Found Velocity-registered server '{}' not in MongoDB, including it as enabled", serverName);
                    futures.add(getServerStatus(registeredServer, true));
                }
            }

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        List<ServerStatus> results = futures.stream()
                                .map(CompletableFuture::join)
                                .toList();

                        if (logsService != null) {
                            long onlineServers = results.stream().filter(s -> "online".equals(s.getStatus())).count();
                            long enabledServers = results.stream().filter(ServerStatus::isEnabled).count();

                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("total_servers", results.size());
                            metadata.put("enabled_servers", enabledServers);
                            metadata.put("disabled_servers", results.size() - enabledServers);
                            metadata.put("online_servers", onlineServers);
                            metadata.put("offline_servers", results.size() - onlineServers);
                            metadata.put("check_completed_time", java.time.Instant.now().toString());
                            logsService.logServerStatusCheck("Server status check completed", metadata);
                        }

                        return results;
                    });

        } catch (Exception e) {
            logger.error("Error getting servers from database, falling back to registered servers", e);
            return getAllRegisteredServersWithStatus();
        }
    }

    
    private CompletableFuture<List<ServerStatus>> getAllRegisteredServersWithStatus() {
        List<CompletableFuture<ServerStatus>> futures = new ArrayList<>();
        
        for (RegisteredServer registeredServer : server.getAllServers()) {
            CompletableFuture<ServerStatus> serverStatusFuture =
                    getServerStatus(registeredServer, true);
            futures.add(serverStatusFuture);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    private ServerStatus createOfflineServerStatus(Map<String, Object> serverData, boolean enabled) {
        String name = (String) serverData.get("name");
        String ip = (String) serverData.get("ip");
        Integer port = (Integer) serverData.get("port");
        
        return new ServerStatus(
                name,
                ip != null ? ip : "unknown",
                port != null ? port : 0,
                enabled ? "offline" : "disabled",
                0,
                -1,
                -1,
                "Unknown",
                enabled ? "Server offline" : "Server disabled",
                enabled
        );
    }

    public CompletableFuture<List<BasicServerInfo>> getBasicServerInfo() {
        List<BasicServerInfo> basicInfo = new ArrayList<>();

        for (RegisteredServer registeredServer : server.getAllServers()) {
            ServerInfo serverInfo = registeredServer.getServerInfo();
            int currentPlayers = registeredServer.getPlayersConnected().size();

            BasicServerInfo info = new BasicServerInfo(
                    serverInfo.getName(),
                    serverInfo.getAddress().getHostString(),
                    serverInfo.getAddress().getPort(),
                    currentPlayers
            );
            basicInfo.add(info);
        }

        return CompletableFuture.completedFuture(basicInfo);
    }

    public CompletableFuture<Optional<ServerStatus>> getServerByName(String serverName) {
        Optional<RegisteredServer> registeredServer = server.getServer(serverName);

        if (registeredServer.isEmpty()) {
            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("requested_server", serverName);
                metadata.put("available_servers", server.getAllServers().stream()
                        .map(s -> s.getServerInfo().getName()).toList());
                logsService.logServerStatusCheck("Server status check failed - server not found", metadata);
            }
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return getServerStatus(registeredServer.get(), true)
                .thenApply(Optional::of);
    }

    public CompletableFuture<List<ServerStatus>> getOnlineServersOnly() {
        return getAllServersWithStatus()
                .thenApply(servers -> servers.stream()
                        .filter(s -> "online".equals(s.getStatus()))
                        .toList());
    }

    private CompletableFuture<ServerStatus> getServerStatus(RegisteredServer registeredServer, boolean enabled) {
        ServerInfo serverInfo = registeredServer.getServerInfo();
        long startTime = System.currentTimeMillis();

        int currentPlayers = registeredServer.getPlayersConnected().size();

        return registeredServer.ping()
                .thenApply(ping -> {
                    long latency = System.currentTimeMillis() - startTime;

                    String version = ping.getVersion() != null ?
                            ping.getVersion().getName() : "Unknown";

                    String motd = ping.getDescriptionComponent() != null ?
                            ping.getDescriptionComponent().toString() : "No MOTD";

                    int maxPlayers = ping.getPlayers()
                            .map(ServerPing.Players::getMax)
                            .orElse(-1);

                    ServerStatus status = new ServerStatus(
                            serverInfo.getName(),
                            serverInfo.getAddress().getHostString(),
                            serverInfo.getAddress().getPort(),
                            "online",
                            currentPlayers,
                            maxPlayers,
                            latency,
                            version,
                            motd,
                            enabled
                    );

                    
                    if (logsService != null && latency > 1000) {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("latency_ms", latency);
                        metadata.put("threshold_ms", 1000);
                        metadata.put("server_address", serverInfo.getAddress().toString());
                        metadata.put("current_players", currentPlayers);
                        logsService.logServerHighLatency("High latency detected for server",
                                serverInfo.getName(),
                                metadata);
                    }

                    
                    if (logsService != null) {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("latency_ms", latency);
                        metadata.put("current_players", currentPlayers);
                        metadata.put("max_players", maxPlayers);
                        metadata.put("version", version);
                        logsService.logServerOnline("Server is online and responding",
                                serverInfo.getName(),
                                metadata);
                    }

                    return status;
                })
                .exceptionally(throwable -> {
                    logger.debug("Server {} is offline or unreachable: {}",
                            serverInfo.getName(), throwable.getMessage());

                    
                    if (logsService != null) {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("error_message", throwable.getMessage());
                        metadata.put("server_address", serverInfo.getAddress().toString());
                        metadata.put("check_time", java.time.Instant.now().toString());
                        logsService.logServerOffline("Server went offline or became unreachable",
                                serverInfo.getName(),
                                metadata);
                    }

                    return new ServerStatus(
                            serverInfo.getName(),
                            serverInfo.getAddress().getHostString(),
                            serverInfo.getAddress().getPort(),
                            "offline",
                            0,
                            -1,
                            -1,
                            "Unknown",
                            "Server offline",
                            enabled
                    );
                });
    }
    
    public static class BasicServerInfo {
        private final String name;
        private final String host;
        private final int port;
        private final int currentPlayers;
        private final String timestamp;

        public BasicServerInfo(String name, String host, int port, int currentPlayers) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.currentPlayers = currentPlayers;
            this.timestamp = java.time.Instant.now().toString();
        }

        public String getName() { return name; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public int getCurrentPlayers() { return currentPlayers; }
        public String getTimestamp() { return timestamp; }
    }

    public static class ServerStatus {
        private final String name;
        private final String host;
        private final int port;
        private final String status;
        private final int currentPlayers;
        private final int maxPlayers;
        private final long latency;
        private final String version;
        private final String motd;
        private final String timestamp;
        private final boolean enabled; 

        public ServerStatus(String name, String host, int port, String status,
                            int currentPlayers, int maxPlayers, long latency,
                            String version, String motd, boolean enabled) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.status = status;
            this.currentPlayers = currentPlayers;
            this.maxPlayers = maxPlayers;
            this.latency = latency;
            this.version = version;
            this.motd = motd;
            this.timestamp = java.time.Instant.now().toString();
            this.enabled = enabled;
        }
        
        public String getName() { return name; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getStatus() { return status; }
        public int getCurrentPlayers() { return currentPlayers; }
        public int getMaxPlayers() { return maxPlayers; }
        public long getLatency() { return latency; }
        public String getVersion() { return version; }
        public String getMotd() { return motd; }
        public String getTimestamp() { return timestamp; }
        public boolean isEnabled() { return enabled; }

        public float getTickRate() {
            if (latency <= 0) return 0.0f;
            return Math.max(0.0f, 20.0f - (latency / 50.0f));
        }

        public double getLoadPercentage() {
            if (maxPlayers <= 0 || status.equals("offline")) return 0.0;
            return (double) currentPlayers / maxPlayers * 100.0;
        }

        public String getLoadStatus() {
            if (status.equals("offline")) return "offline";
            double load = getLoadPercentage();
            if (load < 30) return "low";
            if (load < 70) return "medium";
            return "high";
        }

        public boolean isHealthy() {
            return enabled && status.equals("online") && latency > 0 && latency < 1000;
        }
    }
}