package com.mckube.spigotplugin;

import org.bukkit.plugin.java.JavaPlugin;
import com.mckube.spigotplugin.services.MetricsService;
import com.mckube.spigotplugin.controllers.MetricsController;
import java.net.InetAddress;

public class Spigot_plugin extends JavaPlugin {
    private MetricsController metricsController;
    private final String velocityApiUrl = "http://26.40.23.207:8080/metrics/";

    @Override
    public void onEnable() {
        String serverIp = determineServerIp();
        MetricsService metricsService = new MetricsService(this, serverIp);

        metricsController = new MetricsController(this, metricsService, velocityApiUrl);
        metricsController.registerRoutes();
        metricsController.start();

        getLogger().info("Spigot_plugin enabled with MetricsController for server: " + serverIp);
    }

    @Override
    public void onDisable() {
        if (metricsController != null) metricsController.stop();
        getLogger().info("Spigot_plugin disabled.");
    }

    private String determineServerIp() {
        String ip = getServer().getIp();
        if (ip == null || ip.isEmpty()) ip = System.getenv("MCSERVER_IP");
        if (ip == null || ip.isEmpty()) {
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ignored) {}
        }
        return ip != null && !ip.isEmpty() ? ip : "unknown";
    }
}