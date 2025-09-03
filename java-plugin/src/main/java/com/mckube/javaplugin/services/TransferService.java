package com.mckube.javaplugin.services;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TransferService {

    private final ProxyServer server;
    private final Logger logger;
    private LogsService logsService;

    public TransferService(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public void setLogsService(LogsService logsService) {
        this.logsService = logsService;
    }

    public boolean transferPlayer(String playerName, String serverName) {
        Optional<Player> playerOptional = server.getPlayer(playerName);
        Optional<RegisteredServer> targetServer = server.getServer(serverName);

        if (playerOptional.isEmpty()) {
            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("target_server", serverName);
                metadata.put("error", "player_not_found");
                metadata.put("requested_player", playerName);
                metadata.put("online_players", server.getAllPlayers().stream()
                        .map(Player::getUsername).toList());
                logsService.logTransferFailed("Player transfer failed - player not found",
                        playerName,
                        serverName,
                        metadata);
            }
            return false;
        }

        if (targetServer.isEmpty()) {
            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("target_server", serverName);
                metadata.put("error", "server_not_found");
                metadata.put("requested_player", playerName);
                metadata.put("available_servers", server.getAllServers().stream()
                        .map(s -> s.getServerInfo().getName()).toList());
                logsService.logTransferFailed("Player transfer failed - server not found",
                        playerName,
                        serverName,
                        metadata);
            }
            return false;
        }

        Player player = playerOptional.get();
        String currentServer = player.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("none");

        if (logsService != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("from_server", currentServer);
            metadata.put("requested_by", "admin");
            metadata.put("transfer_time", java.time.Instant.now().toString());
            metadata.put("player_ip", player.getRemoteAddress().getAddress().getHostAddress());
            logsService.logTransferInitiated("Player transfer initiated",
                    player.getUsername(),
                    player.getUniqueId().toString(),
                    serverName,
                    metadata);
        }

        player.createConnectionRequest(targetServer.get()).fireAndForget();
        logger.info("Transferred {} to {}", playerName, serverName);

        if (logsService != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("from_server", currentServer);
            metadata.put("transfer_time", java.time.Instant.now().toString());
            metadata.put("requested_by", "admin");
            logsService.logTransferCompleted("Player transfer request sent successfully",
                    player.getUsername(),
                    player.getUniqueId().toString(),
                    serverName,
                    metadata);
        }

        return true;
    }
}