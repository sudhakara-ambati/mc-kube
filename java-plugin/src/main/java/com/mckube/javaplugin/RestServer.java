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
    private final LogsController logsController; // Add LogsController
    private final ServerManagementController serverManagementController; // Add ServerManagementController
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
        this.logsController = new LogsController(logsService, logger); // Initialize LogsController
        this.healthController = new HealthController();
    }

    public void start(int port) {
        app = Javalin.create(config -> config.http.defaultContentType = "application/json").start(port);

        transferController.registerRoutes(app);
        serverController.registerRoutes(app);
        queueController.registerRoutes(app);
        healthController.registerRoutes(app);
        broadcastController.registerRoutes(app);
        metricsController.registerRoutes(app);
        serverManagementController.registerRoutes(app); // Register ServerManagementController routes
        logsController.registerRoutes(app); // Register LogsController routes

        logger.info("REST API started on port {}", port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
            logger.info("REST API stopped");
        }
    }
}