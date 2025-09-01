package com.mckube.spigotplugin.controllers;

import com.mckube.spigotplugin.services.MetricsService;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class MetricsController {
    private final JavaPlugin plugin;
    private final MetricsService metricsService;
    private final String velocityApiUrl;
    private int taskId = -1;

    public MetricsController(JavaPlugin plugin, MetricsService metricsService, String velocityApiUrl) {
        this.plugin = plugin;
        this.metricsService = metricsService;
        this.velocityApiUrl = velocityApiUrl;
    }

    public void registerRoutes() {
        //
    }

    public void start() {
        taskId = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::postMetrics, 40, 40).getTaskId();
        plugin.getLogger().info("MetricsController started and posting metrics to " + velocityApiUrl + metricsService.collectMetrics().serverIp());
    }

    public void stop() {
        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
        plugin.getLogger().info("MetricsController stopped.");
    }

    private void postMetrics() {
        try {
            MetricsService.MetricsData data = metricsService.collectMetrics();
            String serverIp = data.serverIp();
            String json = serialize(data);

            URL url = new URL(velocityApiUrl + serverIp);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

            int code = conn.getResponseCode();
            if (code != 200) {
                plugin.getLogger().log(Level.WARNING, "Failed to post metrics: HTTP " + code);
            }
            conn.disconnect();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error posting metrics: " + e.getMessage());
        }
    }

    private String serialize(MetricsService.MetricsData d) {
        return String.format(
                "{\"serverIp\":\"%s\",\"timestamp\":\"%s\",\"systemCpuPercent\":%.2f,\"processCpuPercent\":%.2f,\"memoryUsedGB\":%.2f,\"memoryMaxGB\":%.2f,\"memoryPercent\":%.2f,\"systemMemoryUsedGB\":%.2f,\"systemMemoryTotalGB\":%.2f,\"systemMemoryPercent\":%.2f,\"tps\":%.2f,\"tpsPercent\":%.2f}",
                d.serverIp(), d.timestamp(), d.systemCpuPercent(), d.processCpuPercent(), d.memoryUsedGB(), d.memoryMaxGB(), d.memoryPercent(),
                d.systemMemoryUsedGB(), d.systemMemoryTotalGB(), d.systemMemoryPercent(),
                d.tps(), d.tpsPercent()
        );
    }
}