package com.mckube.javaplugin;

import com.google.inject.Inject;
import com.mckube.javaplugin.services.TransferService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
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

    @Inject
    public Java_plugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Plugin initialized successfully.");

        // Create service layer
        transferService = new TransferService(server, logger);

        // Start REST server
        restServer = new RestServer(transferService, logger);
        restServer.start(8080);

        // (optional) Register the in-game /transfer command as well
        transferService.registerCommand();
    }
}
