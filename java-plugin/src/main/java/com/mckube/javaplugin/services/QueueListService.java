package com.mckube.javaplugin.services;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class QueueListService {

    private final ProxyServer server;
    private final Logger logger;

    private final Deque<UUID> queueOrder = new ConcurrentLinkedDeque<>();
    private final Set<UUID> queueSet = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();
    private final ReentrantLock queueLock = new ReentrantLock();

    private static final String QUEUE_SERVER_NAME = "queue";

    public QueueListService(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onPlayerConnect(LoginEvent event) {
        logger.info("=== LoginEvent triggered for: {} ===", event.getPlayer().getUsername());
    }

    @Subscribe(priority = (short) 0)
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        logger.info("=== PostLoginEvent triggered for: {} ===", player.getUsername());

        boolean added = addToQueueIfAbsent(playerId);
        if (added) {
            logger.info("Player {} added to queue", player.getUsername());
        } else {
            logger.info("Player {} already in queue (re-login?)", player.getUsername());
        }

        int position = getPlayerQueuePosition(playerId);

        player.sendMessage(Component.text("You have been added to the queue! Your position is " + position)
                .color(NamedTextColor.GOLD));

        BossBar bossBar = BossBar.bossBar(
                Component.text("Queue Position: " + position).color(NamedTextColor.GOLD),
                1f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
        playerBossBars.put(playerId, bossBar);
        player.showBossBar(bossBar);

        updateAllBossBars();
    }

    @Subscribe(priority = (short) 0)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        String targetServer = event.getResult().getServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("unknown");

        logger.info("=== ServerPreConnectEvent: {} trying to connect to {} ===",
                player.getUsername(), targetServer);

        if (!targetServer.equals(QUEUE_SERVER_NAME) && contains(playerId)) {
            removePlayerFromQueue(playerId, "successfully connecting to " + targetServer);
            player.sendMessage(Component.text("Connecting you to server: " + targetServer)
                    .color(NamedTextColor.GREEN));
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (contains(playerId)) {
            removePlayerFromQueue(playerId, "disconnect from proxy");
        }
    }

    private boolean addToQueueIfAbsent(UUID uuid) {
        queueLock.lock();
        try {
            if (queueSet.add(uuid)) {
                queueOrder.addLast(uuid);
                return true;
            }
            return false;
        } finally {
            queueLock.unlock();
        }
    }

    private boolean contains(UUID uuid) {
        return queueSet.contains(uuid);
    }

    private void removePlayerFromQueue(UUID playerId, String reason) {
        boolean removed;
        queueLock.lock();
        try {
            removed = queueSet.remove(playerId);
            if (removed) {
                queueOrder.remove(playerId);
            }
        } finally {
            queueLock.unlock();
        }

        if (!removed) {
            return;
        }

        BossBar bossBar = playerBossBars.remove(playerId);
        if (bossBar != null) {
            server.getPlayer(playerId).ifPresent(p -> p.hideBossBar(bossBar));
        }

        String nameOrId = server.getPlayer(playerId)
                .map(Player::getUsername)
                .orElse(playerId.toString());

        logger.info("Player {} removed from queue: {}", nameOrId, reason);

        updateAllBossBars();
    }

    public boolean removePlayerFromQueueName(String playerName) {
        Optional<Player> optPlayer = server.getPlayer(playerName);
        if (optPlayer.isEmpty()) {
            return false;
        }
        Player player = optPlayer.get();
        UUID uuid = player.getUniqueId();

        boolean wasInQueue = contains(uuid);
        if (wasInQueue) {
            removePlayerFromQueue(uuid, "removed by administrator");
        }

        player.disconnect(Component.text("Removed from queue by administrator")
                .color(NamedTextColor.RED));

        logger.info("Player {} {} in queue and was kicked", playerName, wasInQueue ? "was" : "was not");
        return wasInQueue;
    }

    public boolean removePlayerFromQueueUUID(UUID playerId) {
        boolean wasInQueue = contains(playerId);
        if (wasInQueue) {
            removePlayerFromQueue(playerId, "removed by administrator");
            server.getPlayer(playerId).ifPresent(player -> {
                player.disconnect(Component.text("Removed from queue by administrator")
                        .color(NamedTextColor.RED));
                logger.info("Player {} kicked after queue removal", player.getUsername());
            });
        }
        return wasInQueue;
    }

    public int getPlayerQueuePosition(UUID playerId) {
        queueLock.lock();
        try {
            if (!queueSet.contains(playerId)) {
                return -1;
            }
            int index = 0;
            for (UUID u : queueOrder) {
                if (u.equals(playerId)) {
                    return index + 1;
                }
                index++;
            }
            return -1;
        } finally {
            queueLock.unlock();
        }
    }

    public int getQueueCount() {
        cleanupDisconnectedPlayers();
        return queueSet.size();
    }

    public List<String> getQueuedPlayerNames() {
        cleanupDisconnectedPlayers();
        queueLock.lock();
        try {
            return queueOrder.stream()
                    .map(server::getPlayer)
                    .filter(Optional::isPresent)
                    .map(opt -> opt.get().getUsername())
                    .collect(Collectors.toList());
        } finally {
            queueLock.unlock();
        }
    }

    public List<String> getQueuedPlayerUUIDs() {
        cleanupDisconnectedPlayers();
        queueLock.lock();
        try {
            return queueOrder.stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());
        } finally {
            queueLock.unlock();
        }
    }


    private void updateAllBossBars() {
        List<UUID> snapshot;
        queueLock.lock();
        try {
            snapshot = new ArrayList<>(queueOrder);
        } finally {
            queueLock.unlock();
        }

        int size = snapshot.size();

        for (int i = 0; i < size; i++) {
            UUID playerId = snapshot.get(i);
            int position = i + 1;

            server.getPlayer(playerId).ifPresent(player -> {
                BossBar bossBar = playerBossBars.get(playerId);
                if (bossBar != null) {
                    bossBar.name(Component.text("Queue Position: " + position)
                            .color(NamedTextColor.GOLD));

                    float progress = Math.max(0.05f, 1.0f - ((float) (position - 1) / Math.max(1, size)));
                    bossBar.progress(progress);
                }
            });
        }
    }

    private void cleanupDisconnectedPlayers() {
        List<UUID> toRemove = new ArrayList<>();
        queueLock.lock();
        try {
            for (UUID uuid : queueOrder) {
                if (server.getPlayer(uuid).isEmpty()) {
                    toRemove.add(uuid);
                }
            }
            if (!toRemove.isEmpty()) {
                for (UUID u : toRemove) {
                    queueSet.remove(u);
                    queueOrder.remove(u);
                    BossBar bb = playerBossBars.remove(u);
                    if (bb != null) {
                    }
                    logger.info("Removed offline player {} during cleanup", u);
                }
            }
        } finally {
            queueLock.unlock();
        }

        if (!toRemove.isEmpty()) {
            updateAllBossBars();
        }
    }
}