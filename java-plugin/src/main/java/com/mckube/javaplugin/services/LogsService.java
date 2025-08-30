package com.mckube.javaplugin.services;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class LogsService {

    private final ProxyServer server;
    private final Logger logger;

    // Thread-safe deque to store recent events
    private final Deque<LogEntry> recentEvents = new ConcurrentLinkedDeque<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Configuration
    private static final int MAX_EVENTS = 1000; // Maximum number of events to keep
    private static final int DEFAULT_LIMIT = 50; // Default number of events to return

    public enum LogType {
        // Player Events
        PLAYER_JOIN,
        PLAYER_LEAVE,
        PLAYER_RECONNECT,

        // Server Events
        SERVER_CONNECT,
        SERVER_DISCONNECT,
        SERVER_OFFLINE,
        SERVER_ONLINE,
        SERVER_HIGH_LATENCY,
        SERVER_STATUS_CHECK,

        // Queue Events
        QUEUE_JOIN,
        QUEUE_LEAVE,
        QUEUE_REMOVE_ADMIN,
        QUEUE_CLEANUP,
        QUEUE_POSITION_UPDATE,

        // Transfer Events
        TRANSFER_INITIATED,
        TRANSFER_COMPLETED,
        TRANSFER_FAILED,

        // Broadcast Events
        BROADCAST_ALL,
        BROADCAST_SERVER,
        BROADCAST_FAILED,

        // Metrics Events
        METRICS_COLLECTED,
        METRICS_FAILED,

        // System Events
        SYSTEM_STARTUP,
        SYSTEM_SHUTDOWN,
        SYSTEM_CONFIG_CHANGE,
        SYSTEM_EVENT,

        // API Events
        API_REQUEST,
        API_ERROR,

        // Error Events
        ERROR
    }

    public static class LogEntry {
        private final String id;
        private final Instant timestamp;
        private final LogType type;
        private final String message;
        private final String playerName;
        private final String playerUuid;
        private final String serverName;
        private final Map<String, Object> metadata;

        public LogEntry(LogType type, String message, String playerName, String playerUuid, String serverName, Map<String, Object> metadata) {
            this.id = UUID.randomUUID().toString();
            this.timestamp = Instant.now();
            this.type = type;
            this.message = message;
            this.playerName = playerName;
            this.playerUuid = playerUuid;
            this.serverName = serverName;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        }

        public LogEntry(LogType type, String message) {
            this(type, message, null, null, null, null);
        }

        // Getters
        public String getId() { return id; }
        public Instant getTimestamp() { return timestamp; }
        public LogType getType() { return type; }
        public String getMessage() { return message; }
        public String getPlayerName() { return playerName; }
        public String getPlayerUuid() { return playerUuid; }
        public String getServerName() { return serverName; }
        public Map<String, Object> getMetadata() { return metadata; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("timestamp", timestamp.toString());
            map.put("type", type.name().toLowerCase());
            map.put("message", message);
            if (playerName != null) map.put("player_name", playerName);
            if (playerUuid != null) map.put("player_uuid", playerUuid);
            if (serverName != null) map.put("server_name", serverName);
            if (!metadata.isEmpty()) map.put("metadata", metadata);
            return map;
        }
    }

    public LogsService(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;

        // Add initial system startup event
        logSystemStartup("LogsService initialized and ready");
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ip_address", player.getRemoteAddress().getAddress().getHostAddress());

        logPlayerJoin("Player joined the proxy",
                player.getUsername(),
                player.getUniqueId().toString(),
                metadata);
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> metadata = new HashMap<>();
        if (event.getLoginStatus() != null) {
            metadata.put("login_status", event.getLoginStatus().name());
        }

        logPlayerLeave("Player disconnected from proxy",
                player.getUsername(),
                player.getUniqueId().toString(),
                metadata);
    }

    @Subscribe
    public void onServerConnect(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();
        String previousServer = event.getPreviousServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("none");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("previous_server", previousServer);

        logServerConnect("Player connected to server",
                player.getUsername(),
                player.getUniqueId().toString(),
                serverName,
                metadata);
    }

    // Specific logging methods for each event type

    // Player Events
    public void logPlayerJoin(String message, String playerName, String playerUuid, Map<String, Object> metadata) {
        addEvent(LogType.PLAYER_JOIN, message, playerName, playerUuid, null, metadata);
    }

    public void logPlayerLeave(String message, String playerName, String playerUuid, Map<String, Object> metadata) {
        addEvent(LogType.PLAYER_LEAVE, message, playerName, playerUuid, null, metadata);
    }

    public void logPlayerReconnect(String message, String playerName, String playerUuid, Map<String, Object> metadata) {
        addEvent(LogType.PLAYER_RECONNECT, message, playerName, playerUuid, null, metadata);
    }

    // Server Events
    public void logServerConnect(String message, String playerName, String playerUuid, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.SERVER_CONNECT, message, playerName, playerUuid, serverName, metadata);
    }

    public void logServerOffline(String message, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.SERVER_OFFLINE, message, null, null, serverName, metadata);
    }

    public void logServerOnline(String message, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.SERVER_ONLINE, message, null, null, serverName, metadata);
    }

    public void logServerHighLatency(String message, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.SERVER_HIGH_LATENCY, message, null, null, serverName, metadata);
    }

    public void logServerStatusCheck(String message, Map<String, Object> metadata) {
        addEvent(LogType.SERVER_STATUS_CHECK, message, null, null, null, metadata);
    }

    // Queue Events
    public void logQueueJoin(String message, String playerName, String playerUuid, Map<String, Object> metadata) {
        addEvent(LogType.QUEUE_JOIN, message, playerName, playerUuid, "queue", metadata);
    }

    public void logQueueLeave(String message, String playerName, String playerUuid, Map<String, Object> metadata) {
        addEvent(LogType.QUEUE_LEAVE, message, playerName, playerUuid, "queue", metadata);
    }

    public void logQueueRemoveAdmin(String message, String playerName, String playerUuid, Map<String, Object> metadata) {
        addEvent(LogType.QUEUE_REMOVE_ADMIN, message, playerName, playerUuid, "queue", metadata);
    }

    public void logQueueCleanup(String message, Map<String, Object> metadata) {
        addEvent(LogType.QUEUE_CLEANUP, message, null, null, "queue", metadata);
    }

    // Transfer Events
    public void logTransferInitiated(String message, String playerName, String playerUuid, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.TRANSFER_INITIATED, message, playerName, playerUuid, serverName, metadata);
    }

    public void logTransferCompleted(String message, String playerName, String playerUuid, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.TRANSFER_COMPLETED, message, playerName, playerUuid, serverName, metadata);
    }

    public void logTransferFailed(String message, String playerName, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.TRANSFER_FAILED, message, playerName, null, serverName, metadata);
    }

    // Broadcast Events
    public void logBroadcastAll(String message, Map<String, Object> metadata) {
        addEvent(LogType.BROADCAST_ALL, message, null, null, null, metadata);
    }

    public void logBroadcastServer(String message, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.BROADCAST_SERVER, message, null, null, serverName, metadata);
    }

    public void logBroadcastFailed(String message, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.BROADCAST_FAILED, message, null, null, serverName, metadata);
    }

    // Metrics Events
    public void logMetricsCollected(String message, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.METRICS_COLLECTED, message, null, null, serverName, metadata);
    }

    public void logMetricsFailed(String message, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.METRICS_FAILED, message, null, null, serverName, metadata);
    }

    // System Events
    public void logSystemStartup(String message) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", Instant.now().toString());
        addEvent(LogType.SYSTEM_STARTUP, message, null, null, null, metadata);
    }

    public void logSystemShutdown(String message) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", Instant.now().toString());
        addEvent(LogType.SYSTEM_SHUTDOWN, message, null, null, null, metadata);
    }

    public void logSystemEvent(String message, String playerName, String playerUuid, String serverName, Map<String, Object> metadata) {
        addEvent(LogType.SYSTEM_EVENT, message, playerName, playerUuid, serverName, metadata);
    }

    public void logSystemEvent(String message, Map<String, Object> metadata) {
        addEvent(LogType.SYSTEM_EVENT, message, null, null, null, metadata);
    }

    // API Events
    public void logApiRequest(String message, Map<String, Object> metadata) {
        addEvent(LogType.API_REQUEST, message, null, null, null, metadata);
    }

    public void logApiError(String message, Map<String, Object> metadata) {
        addEvent(LogType.API_ERROR, message, null, null, null, metadata);
    }

    // Error Events
    public void logError(String message, String context, Exception exception) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("context", context);
        if (exception != null) {
            metadata.put("exception_class", exception.getClass().getSimpleName());
            metadata.put("exception_message", exception.getMessage());
        }
        addEvent(LogType.ERROR, message, null, null, null, metadata);
    }

    public void logServerOperation(String message, String playerName, String playerUuid, String serverName, Map<String, Object> metadata) {
    // Determine the appropriate log type based on the message content
    LogType logType = LogType.SYSTEM_EVENT; // Default
    
    if (message.contains("added")) {
        logType = LogType.SERVER_ONLINE;
    } else if (message.contains("removed")) {
        logType = LogType.SERVER_OFFLINE;
    } else if (message.contains("enabled")) {
        logType = LogType.SERVER_ONLINE;
    } else if (message.contains("disabled")) {
        logType = LogType.SERVER_OFFLINE;
    }
    
    addEvent(logType, message, playerName, playerUuid, serverName, metadata);
}

    private void addEvent(LogType type, String message, String playerName, String playerUuid, String serverName, Map<String, Object> metadata) {
        lock.writeLock().lock();
        try {
            LogEntry entry = new LogEntry(type, message, playerName, playerUuid, serverName, metadata);
            recentEvents.addFirst(entry); // Add to front for newest first

            // Remove old events if we exceed the maximum
            while (recentEvents.size() > MAX_EVENTS) {
                recentEvents.removeLast();
            }

            logger.debug("Added log event: {} - {}", type, message);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get recent events with optional filtering
     */
    public List<Map<String, Object>> getRecentEvents(int limit, LogType filterType, String filterPlayer, Instant since) {
        lock.readLock().lock();
        try {
            return recentEvents.stream()
                    .filter(event -> filterType == null || event.getType() == filterType)
                    .filter(event -> filterPlayer == null ||
                            (event.getPlayerName() != null && event.getPlayerName().equalsIgnoreCase(filterPlayer)) ||
                            (event.getPlayerUuid() != null && event.getPlayerUuid().equalsIgnoreCase(filterPlayer)))
                    .filter(event -> since == null || event.getTimestamp().isAfter(since))
                    .limit(Math.min(limit, MAX_EVENTS))
                    .map(LogEntry::toMap)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get recent events with default settings
     */
    public List<Map<String, Object>> getRecentEvents() {
        return getRecentEvents(DEFAULT_LIMIT, null, null, null);
    }

    /**
     * Get statistics about the logs
     */
    public Map<String, Object> getLogStatistics() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("total_events", recentEvents.size());
            stats.put("max_capacity", MAX_EVENTS);

            // Count by type
            Map<String, Long> typeCounts = recentEvents.stream()
                    .collect(Collectors.groupingBy(
                            event -> event.getType().name().toLowerCase(),
                            Collectors.counting()
                    ));
            stats.put("events_by_type", typeCounts);

            // Get oldest and newest timestamps
            if (!recentEvents.isEmpty()) {
                stats.put("oldest_event", recentEvents.peekLast().getTimestamp().toString());
                stats.put("newest_event", recentEvents.peekFirst().getTimestamp().toString());
            }

            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear all events (admin function)
     */
    public void clearEvents() {
        lock.writeLock().lock();
        try {
            int clearedCount = recentEvents.size();
            recentEvents.clear();
            logger.info("Cleared {} log events", clearedCount);
            logSystemEvent("Log events cleared by administrator", null);
        } finally {
            lock.writeLock().unlock();
        }
    }
}