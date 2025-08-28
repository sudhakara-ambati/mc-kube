package com.mckube.spigotplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import com.mckube.spigot.MetricsService;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class Spigot_plugin extends JavaPlugin {

    private MetricsService metricsService;
    private String velocityApiUrl = "http://26.40.23.207:8080/metrics/";

    @Override
    public void onEnable() {
        String serverIp = Bukkit.getServer().getIp();
        if (serverIp == null || serverIp.isEmpty()) {
            serverIp = getServerIpFromEnvOrConfig();
        }
        if (serverIp == null || serverIp.isEmpty()) {
            serverIp = getLocalHostIp();
        }
        if (serverIp == null || serverIp.isEmpty()) {
            serverIp = "unknown";
            getLogger().warning("Unable to determine a unique server IP! Metrics may overwrite other servers. Set MCSERVER_IP env or server.properties 'server-ip'.");
        }
        metricsService = new MetricsService(this, serverIp);
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::postMetrics, 20 * 10, 20 * 10); // every 10 seconds
        getLogger().info("Spigot_plugin enabled and posting metrics to " + velocityApiUrl + serverIp);
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
                getLogger().log(Level.WARNING, "Failed to post metrics: HTTP " + code);
            }
            conn.disconnect();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error posting metrics: " + e.getMessage());
        }
    }

    private String serialize(MetricsService.MetricsData d) {
        return String.format(
                "{\"serverIp\":\"%s\",\"timestamp\":\"%s\",\"cpuPercent\":%.2f,\"memoryUsedGB\":%.2f,\"memoryMaxGB\":%.2f,\"memoryPercent\":%.2f,\"systemMemoryUsedGB\":%.2f,\"systemMemoryTotalGB\":%.2f,\"systemMemoryPercent\":%.2f,\"tps\":%.2f,\"tpsPercent\":%.2f}",
                d.serverIp(), d.timestamp(), d.cpuPercent(), d.memoryUsedGB(), d.memoryMaxGB(), d.memoryPercent(),
                d.systemMemoryUsedGB(), d.systemMemoryTotalGB(), d.systemMemoryPercent(),
                d.tps(), d.tpsPercent()
        );
    }

    private String getServerIpFromEnvOrConfig() {
        String envIp = System.getenv("MCSERVER_IP");
        if (envIp != null && !envIp.isEmpty()) return envIp;
        return "";
    }

    private String getLocalHostIp() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            String ip = localhost.getHostAddress();
            if (ip != null && !ip.isEmpty()) return ip;
        } catch (Exception ignored) {}
        return "";
    }
}