package com.mckube.javaplugin;

import com.google.inject.Inject;
import com.mckube.javaplugin.services.QueueListService;
import com.mckube.javaplugin.services.ServerListService;
import com.mckube.javaplugin.services.TransferService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

@Plugin(
        id = "mc-kube",
        name = "mc-kube",
        version = BuildConstants.VERSION
)
public class Java_plugin {

    private final ProxyServer server;
    private final Logger logger;
    private RestServer restServer;
    private TransferService transferService;
    private QueueListService queueListService;
    private ServerListService serverListService;

    @Inject
    public Java_plugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Plugin initialized successfully.");

        transferService = new TransferService(server, logger);
        queueListService = new QueueListService(server, logger);
        serverListService = new ServerListService(server, logger);

        server.getEventManager().register(this, queueListService);

        logger.info("=== QueueListService event listeners registered ===");

        restServer = new RestServer(
                transferService,
                queueListService,
                serverListService,
                logger
        );

        restServer.start(8080);

        transferService.registerCommand();

        logger.info("MC-Kube plugin fully initialized with REST API on port 8080");
        logger.info("REST API endpoints registered:");
        logger.info("  GET  /health - Health check endpoint");
        logger.info("  GET  /server/list - Basic server list with minimal info");
        logger.info("  GET  /server/summary - Quick summary/overview");
        logger.info("  GET  /server/{name} - Detailed info for specific server");
        logger.info("  GET  /server/status - Server names and basic status (fastest)");
        logger.info("  GET  /server/players - Player counts across all servers");
        logger.info("  POST /transfer - Transfer players between servers");
        logger.info("  GET  /queue/playerlist - Get queued player names");
        logger.info("  GET  /queue/uuidlist - Get queued player UUIDs");
        logger.info("  GET  /queue/count - Get queue count");
        logger.info("  POST /queue/removename - Remove player from queue");
        logger.info("  POST /queue/removeuuid - Remove player from queue");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Plugin shutting down...");

        if (restServer != null) {
            logger.info("Stopping REST API server...");
            restServer.stop();
        }

        if (transferService != null) {
            logger.debug("Transfer service cleanup completed");
        }

        if (queueListService != null) {
            logger.debug("Queue service cleanup completed");
        }

        if (serverListService != null) {
            logger.debug("Server list service cleanup completed");
        }

        logger.info("Plugin shut down successfully.");
    }

    public TransferService getTransferService() {
        return transferService;
    }

    public QueueListService getQueueListService() {
        return queueListService;
    }

    public ServerListService getServerListService() {
        return serverListService;
    }

    public RestServer getRestServer() {
        return restServer;
    }
}