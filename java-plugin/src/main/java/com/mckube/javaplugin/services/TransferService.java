package com.mckube.javaplugin.services;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Optional;

public class TransferService {

    private final ProxyServer server;
    private final Logger logger;

    public TransferService(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public boolean transferPlayer(String playerName, String serverName) {
        Optional<Player> playerOptional = server.getPlayer(playerName);
        Optional<RegisteredServer> targetServer = server.getServer(serverName);

        if (playerOptional.isEmpty() || targetServer.isEmpty()) {
            return false;
        }

        playerOptional.get().createConnectionRequest(targetServer.get()).fireAndForget();
        logger.info("Transferred {} to {}", playerName, serverName);
        return true;
    }

    public void registerCommand() {
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("transfer").build(),
                (SimpleCommand) invocation -> {
                    String[] args = invocation.arguments();
                    CommandSource source = invocation.source();

                    if (args.length != 2) {
                        source.sendMessage(Component.text("Usage: /transfer <player> <server>"));
                        return;
                    }

                    String playerName = args[0];
                    String serverName = args[1];

                    if (transferPlayer(playerName, serverName)) {
                        source.sendMessage(Component.text(
                                "Transferred " + playerName + " to " + serverName
                        ));
                    } else {
                        source.sendMessage(Component.text(
                                "Failed to transfer. Check player and server."
                        ));
                    }
                }
        );
    }
}
