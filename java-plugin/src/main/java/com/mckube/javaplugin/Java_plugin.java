package com.mckube.javaplugin;

import com.google.inject.Inject;
import java.nio.file.Path;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mckube.javaplugin.services.*;
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

    private MongoClient mongoClient;

    private RestServer restServer;
    private TransferService transferService;
    private QueueListService queueListService;
    private ServerListService serverListService;
    private BroadcastService broadcastService;
    private MetricsService metricsService;
    private ServerManagementService serverManagementService;
    private LogsService logsService; // Add LogsService

    @Inject
    public Java_plugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Plugin initialized successfully.");

        // Initialize MongoDB client
        initializeMongoDB();

        // Initialize LogsService first since other services depend on it
        logsService = new LogsService(server, logger);

        // Initialize other services
        transferService = new TransferService(server, logger);
        queueListService = new QueueListService(server, logger);
        serverListService = new ServerListService(server, logger);
        broadcastService = new BroadcastService(server, logger);
        metricsService = new MetricsService(logger);
        serverManagementService = new ServerManagementService(server, mongoClient, logger);

        // Wire up LogsService dependencies
        transferService.setLogsService(logsService);
        queueListService.setLogsService(logsService);
        serverListService.setLogsService(logsService);
        broadcastService.setLogsService(logsService);
        metricsService.setLogsService(logsService);
        serverManagementService.setLogsService(logsService);

        // Register event listeners for both LogsService and QueueListService
        server.getEventManager().register(this, logsService);
        server.getEventManager().register(this, queueListService);

        logger.info("Loading servers from MongoDB...");
        serverManagementService.loadServersOnStartup();
        logger.info("Server loading completed.");

        logger.info("=== Event listeners registered for LogsService and QueueListService ===");

        restServer = new RestServer(
                transferService,
                queueListService,
                serverListService,
                broadcastService,
                metricsService,
                logsService, // Pass LogsService to RestServer
                serverManagementService,
                logger
        );

        restServer.start(8080);

        logger.info("MC-Kube plugin fully initialized with REST API on port 8080");
        logger.info("REST API endpoints registered:");
        logger.info("  GET  /health - Health check endpoint");
        logger.info("  GET  /server/list - Basic server list with minimal info");
        logger.info("  GET  /server/summary - Quick summary/overview");
        logger.info("  GET  /server/{name} - Detailed info for specific server");
        logger.info("  GET  /server/status - Server names and basic status (fastest)");
        logger.info("  GET  /server/players - Player counts across all servers");
        logger.info("  POST /transfer - Transfer players between servers");
        logger.info("  GET  /queue/list - Get queued player UUIDs");
        logger.info("  GET  /queue/count - Get queue count");
        logger.info("  POST /queue/remove - Remove player from queue");
        logger.info("  GET  /metrics/{server} - Fetch server metrics");
        logger.info("  GET  /cluster/logs - Fetch recent cluster events or plugin logs");
        logger.info("  GET  /cluster/logs/stats - Get log statistics");
        logger.info("  DELETE /cluster/logs - Clear all logs (admin)");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Plugin shutting down...");

        // Log shutdown event
        if (logsService != null) {
            logsService.logSystemEvent("Plugin shutdown initiated", null);
        }

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

        if (metricsService != null) {
            logger.debug("Metrics service cleanup completed");
        }

        if (logsService != null) {
            logger.debug("Logs service cleanup completed");
        }

        logger.info("Plugin shut down successfully.");
    }

    private void initializeMongoDB() {
    try {
        // For local MongoDB without authentication
        String mongoUri = "mongodb://localhost:27017/";
        
        mongoClient = MongoClients.create(mongoUri);
        
        // Test the connection
        mongoClient.getDatabase("mc_kube").runCommand(new org.bson.Document("ping", 1));
        mongoClient.getDatabase("mc_kube").listCollectionNames().first();
        logger.info("Successfully connected to MongoDB!");
        
        createDatabaseIndexes();

    } catch (Exception e) {
        logger.error("Failed to connect to MongoDB", e);
        throw new RuntimeException("MongoDB connection failed", e);
    }
    }

        // Add this to your plugin initialization:
    private void createDatabaseIndexes() {
        try {
            MongoDatabase database = mongoClient.getDatabase("mc_kube");
            
            // Create unique index on server name (prevents duplicates)
            database.getCollection("servers")
                    .createIndex(Indexes.ascending("name"), 
                        new IndexOptions().unique(true));
            
            // Index for enabled/disabled queries
            database.getCollection("servers")
                    .createIndex(Indexes.ascending("enabled"));
                    
            logger.info("Database indexes created successfully");
        } catch (Exception e) {
            logger.warn("Index creation failed (might already exist): {}", e.getMessage());
        }
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

    public BroadcastService getBroadcastService() {
        return broadcastService;
    }

    public MetricsService getMetricsService() {
        return metricsService;
    }

    public LogsService getLogsService() {
        return logsService;
    }

    public ServerManagementService getServerManagementService() {
        return serverManagementService;
    }

    public RestServer getRestServer() {
        return restServer;
    }
}