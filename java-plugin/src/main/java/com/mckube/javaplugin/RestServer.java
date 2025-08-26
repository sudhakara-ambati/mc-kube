package com.mckube.javaplugin;

import com.mckube.javaplugin.controllers.HealthController;
import com.mckube.javaplugin.controllers.QueueController;
import com.mckube.javaplugin.controllers.ServerController;
import com.mckube.javaplugin.controllers.TransferController;
import com.mckube.javaplugin.services.QueueListService;
import com.mckube.javaplugin.services.ServerListService;
import com.mckube.javaplugin.services.TransferService;
import io.javalin.Javalin;
import org.slf4j.Logger;

public class RestServer {

    private final TransferController transferController;
    private final ServerController serverController;
    private final QueueController queueController;
    private final HealthController healthController;
    private final Logger logger;
    private Javalin app;

    public RestServer(TransferService transferService, QueueListService queueListService,
                      ServerListService serverListService, Logger logger) {
        this.logger = logger;
        this.transferController = new TransferController(transferService, logger);
        this.serverController = new ServerController(serverListService, logger);
        this.queueController = new QueueController(queueListService, logger);
        this.healthController = new HealthController();
    }

    public void start(int port) {
        app = Javalin.create(config -> config.http.defaultContentType = "application/json").start(port);

        transferController.registerRoutes(app);
        serverController.registerRoutes(app);
        queueController.registerRoutes(app);
        healthController.registerRoutes(app);

        logger.info("REST API started on port {}", port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
            logger.info("REST API stopped");
        }
    }
}