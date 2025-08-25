package com.mckube.javaplugin;

import com.mckube.javaplugin.services.TransferService;
import io.javalin.Javalin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

public class RestServer {

    private final TransferService transferService;
    private final Logger logger;
    private Javalin app;

    public RestServer(TransferService transferService, Logger logger) {
        this.transferService = transferService;
        this.logger = logger;
    }

    public void start(int port) {
        app = Javalin.create().start(port);

        app.post("/transfer", ctx -> {
            try {
                JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
                String playerName = body.get("player").getAsString();
                String serverName = body.get("server").getAsString();

                boolean success = transferService.transferPlayer(playerName, serverName);

                if (success) {
                    ctx.status(200).result("Transferred " + playerName + " to " + serverName);
                } else {
                    ctx.status(400).result("Player or server not found");
                }

            } catch (Exception e) {
                logger.error("Error handling transfer request", e);
                ctx.status(500).result("Internal error");
            }
        });

        logger.info("REST API started on port {}", port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }
}
