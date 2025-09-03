package com.mckube.javaplugin;

import com.mckube.javaplugin.controllers.*;
import com.mckube.javaplugin.services.BroadcastService;
import com.mckube.javaplugin.services.QueueListService;
import com.mckube.javaplugin.services.ServerListService;
import com.mckube.javaplugin.services.ServerManagementService;
import com.mckube.javaplugin.services.TransferService;
import com.mckube.javaplugin.services.MetricsService;
import com.mckube.javaplugin.services.LogsService;
import io.javalin.Javalin;
import org.slf4j.Logger;

public class RestServer {

    private final TransferController transferController;
    private final ServerController serverController;
    private final QueueController queueController;
    private final BroadcastController broadcastController;
    private final HealthController healthController;
    private final MetricsController metricsController;
    private final LogsController logsController;
    private final ServerManagementController serverManagementController;
    private final PerformanceController performanceController;
    private final Logger logger;
    private Javalin app;

    public RestServer(TransferService transferService, QueueListService queueListService,
            ServerListService serverListService, BroadcastService broadcastService,
            MetricsService metricsService,
            LogsService logsService,
            ServerManagementService serverManagementService,
            Logger logger) {
        this.logger = logger;
        this.transferController = new TransferController(transferService, logger);
        this.serverController = new ServerController(serverListService, logger);
        this.queueController = new QueueController(queueListService, logger);
        this.broadcastController = new BroadcastController(broadcastService, logger);
        this.metricsController = new MetricsController(metricsService, logger);
        this.serverManagementController = new ServerManagementController(serverManagementService, logger);
        this.logsController = new LogsController(logsService, logger);
        this.healthController = new HealthController();
        this.performanceController = new PerformanceController(logger, serverListService);
    }

    public void start(int port) {
        app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> {
                    it.anyHost();
                    it.allowCredentials = false;
                });
            });
        }).start(port);

        transferController.registerRoutes(app);
        serverController.registerRoutes(app);
        queueController.registerRoutes(app);
        healthController.registerRoutes(app);
        broadcastController.registerRoutes(app);
        metricsController.registerRoutes(app);
        serverManagementController.registerRoutes(app);
        logsController.registerRoutes(app);
        performanceController.registerRoutes(app);

        logger.info("REST API started on port {}", port);
        
        startCacheCleanupScheduler();
        logger.info("Performance optimizations active: Cache cleanup scheduler started");
    }
    
    private void startCacheCleanupScheduler() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(300000);
                    metricsController.cleanupCache();
                    queueController.cleanupCache();
                    serverManagementController.cleanupRateLimitCache();
                    logger.debug("Performed periodic cache cleanup - freed expired entries");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.warn("Error during cache cleanup: {}", e.getMessage());
                }
            }
        });
        cleanupThread.setName("MC-Kube-Cache-Cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    public void stop() {
        if (app != null) {
            app.stop();
            logger.info("REST API stopped");
        }
    }
}