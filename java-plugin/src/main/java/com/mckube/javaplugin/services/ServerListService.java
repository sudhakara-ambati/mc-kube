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
import java.util.concurrent.CompletableFuture;

public class ServerListService {

    private final ProxyServer server;
    private final Logger logger;
    private LogsService logsService;

    public ServerListService(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public void setLogsService(LogsService logsService) {
        this.logsService = logsService;
    }

    public CompletableFuture<List<ServerStatus>> getAllServersWithStatus() {
        List<CompletableFuture<ServerStatus>> futures = new ArrayList<>();
        
        if (logsService != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("total_servers", server.getAllServers().size());
            metadata.put("check_time", java.time.Instant.now().toString());
            metadata.put("server_names", server.getAllServers().stream()
                    .map(s -> s.getServerInfo().getName()).toList());
            logsService.logServerStatusCheck("Server status check initiated for all servers", metadata);
        }

        for (RegisteredServer registeredServer : server.getAllServers()) {
            CompletableFuture<ServerStatus> serverStatusFuture =
                    getServerStatus(registeredServer);
            futures.add(serverStatusFuture);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<ServerStatus> results = futures.stream()
                            .map(CompletableFuture::join)
                            .toList();

                    
                    if (logsService != null) {
                        long onlineServers = results.stream().filter(s -> "online".equals(s.getStatus())).count();
                        long offlineServers = results.size() - onlineServers;

                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("total_servers", results.size());
                        metadata.put("online_servers", onlineServers);
                        metadata.put("offline_servers", offlineServers);
                        metadata.put("check_completed_time", java.time.Instant.now().toString());
                        metadata.put("online_server_names", results.stream()
                                .filter(s -> "online".equals(s.getStatus()))
                                .map(ServerStatus::getName).toList());
                        metadata.put("offline_server_names", results.stream()
                                .filter(s -> "offline".equals(s.getStatus()))
                                .map(ServerStatus::getName).toList());
                        logsService.logServerStatusCheck("Server status check completed", metadata);
                    }

                    return results;
                });
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

        return getServerStatus(registeredServer.get())
                .thenApply(Optional::of);
    }

    public CompletableFuture<List<ServerStatus>> getOnlineServersOnly() {
        return getAllServersWithStatus()
                .thenApply(servers -> servers.stream()
                        .filter(s -> "online".equals(s.getStatus()))
                        .toList());
    }

    private CompletableFuture<ServerStatus> getServerStatus(RegisteredServer registeredServer) {
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
                            motd
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
                            "Server offline"
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

        public ServerStatus(String name, String host, int port, String status,
                            int currentPlayers, int maxPlayers, long latency,
                            String version, String motd) {
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
            return status.equals("online") && latency > 0 && latency < 1000;
        }
    }
}