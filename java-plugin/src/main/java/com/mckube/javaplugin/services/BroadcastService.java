package com.mckube.javaplugin.services;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class BroadcastService {

    private final ProxyServer server;
    private final Logger logger;
    private LogsService logsService;

    public BroadcastService(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public void setLogsService(LogsService logsService) {
        this.logsService = logsService;
    }

    public void broadcastMessage(String message) {
        Component component = Component.text("[Broadcast] ", NamedTextColor.GREEN)
                .append(Component.text(message, NamedTextColor.GOLD));

        Collection<Player> players = server.getAllPlayers();
        if (players.isEmpty()) {
            logger.info("No players online to broadcast to");

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("message", message);
                metadata.put("target", "all_players");
                metadata.put("players_online", 0);
                metadata.put("broadcast_time", java.time.Instant.now().toString());
                logsService.logBroadcastFailed("Broadcast attempted but no players online", null, metadata);
            }
            return;
        }

        players.forEach(player -> player.sendMessage(component));
        logger.info("Broadcasted message to {} players: {}", players.size(), message);

        if (logsService != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("message", message);
            metadata.put("target", "all_players");
            metadata.put("players_reached", players.size());
            metadata.put("broadcast_time", java.time.Instant.now().toString());
            metadata.put("message_length", message.length());
            logsService.logBroadcastAll("Broadcast message sent to all players", metadata);
        }
    }

    public void broadcastMessageToServer(String serverName, String message) {
        Component component = Component.text("[Broadcast] ", NamedTextColor.GREEN)
                .append(Component.text(message, NamedTextColor.GOLD));

        Optional<RegisteredServer> serverOptional = server.getServer(serverName);
        if (serverOptional.isEmpty()) {
            logger.warn("Server '{}' not found", serverName);

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("message", message);
                metadata.put("target_server", serverName);
                metadata.put("error", "server_not_found");
                metadata.put("available_servers", server.getAllServers().stream()
                        .map(s -> s.getServerInfo().getName()).toList());
                logsService.logBroadcastFailed("Broadcast failed - server not found", serverName, metadata);
            }
            return;
        }

        RegisteredServer targetServer = serverOptional.get();
        Collection<Player> playersOnServer = targetServer.getPlayersConnected();

        if (playersOnServer.isEmpty()) {
            logger.info("No players on server '{}' to broadcast to", serverName);

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("message", message);
                metadata.put("target_server", serverName);
                metadata.put("players_on_server", 0);
                metadata.put("broadcast_time", java.time.Instant.now().toString());
                logsService.logBroadcastFailed("Broadcast attempted but no players on target server", serverName, metadata);
            }
            return;
        }

        playersOnServer.forEach(player -> player.sendMessage(component));
        logger.info("Broadcasted message to {} players on server '{}': {}",
                playersOnServer.size(), serverName, message);

        if (logsService != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("message", message);
            metadata.put("target_server", serverName);
            metadata.put("players_reached", playersOnServer.size());
            metadata.put("broadcast_time", java.time.Instant.now().toString());
            metadata.put("message_length", message.length());
            metadata.put("player_list", playersOnServer.stream()
                    .map(Player::getUsername).toList());
            logsService.logBroadcastServer("Broadcast message sent to server players", serverName, metadata);
        }
    }
}