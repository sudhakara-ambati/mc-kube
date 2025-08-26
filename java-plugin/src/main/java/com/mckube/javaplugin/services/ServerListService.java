package com.mckube.javaplugin.services;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ServerListService {

    private final ProxyServer server;
    private final Logger logger;

    public ServerListService(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public CompletableFuture<List<ServerStatus>> getAllServersWithStatus() {
        List<CompletableFuture<ServerStatus>> futures = new ArrayList<>();

        for (RegisteredServer registeredServer : server.getAllServers()) {
            CompletableFuture<ServerStatus> serverStatusFuture =
                    getServerStatus(registeredServer);
            futures.add(serverStatusFuture);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    // Get basic server info (no ping) - faster for status checks
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

    // Get specific server by name
    public CompletableFuture<Optional<ServerStatus>> getServerByName(String serverName) {
        Optional<RegisteredServer> registeredServer = server.getServer(serverName);

        if (registeredServer.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return getServerStatus(registeredServer.get())
                .thenApply(Optional::of);
    }

    // Get only online servers - more efficient for player counts
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

                    return new ServerStatus(
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
                })
                .exceptionally(throwable -> {
                    logger.debug("Server {} is offline or unreachable: {}",
                            serverInfo.getName(), throwable.getMessage());

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

    // Basic server info class for lightweight responses
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

        // Getters
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
            return status.equals("online") && latency > 0 && latency < 1000; // Under 1 second ping
        }
    }
}