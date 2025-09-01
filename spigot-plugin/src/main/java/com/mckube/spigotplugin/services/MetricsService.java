package com.mckube.spigotplugin.services;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import com.sun.management.OperatingSystemMXBean;

public class MetricsService {
    private final JavaPlugin plugin;
    private final String serverIp;
    private final OperatingSystemMXBean osBean;
    private final RuntimeMXBean runtimeBean;

    private double lastSystemCpuPercent = 0.0;
    private double lastProcessCpuPercent = 0.0;

    public MetricsService(JavaPlugin plugin, String serverIp) {
        this.plugin = plugin;
        this.serverIp = serverIp;
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
    }

    public MetricsData collectMetrics() {
        double systemCpuPercent = getSystemCpuUsage();
        double processCpuPercent = getProcessCpuUsage();

        long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long maxMem = Runtime.getRuntime().maxMemory();
        double memoryUsedGB = bytesToGB(usedMem);
        double memoryMaxGB = bytesToGB(maxMem);
        double memoryPercent = memoryMaxGB > 0 ? (memoryUsedGB / memoryMaxGB) * 100.0 : 0;

        double sysUsedGB = getSystemMemoryUsedGB();
        double sysTotalGB = getSystemMemoryTotalGB();
        double sysMemPercent = sysTotalGB > 0 ? (sysUsedGB / sysTotalGB) * 100.0 : 0;

        double tps = getTPS();
        double tpsPercent = Math.min((tps / 20.0) * 100.0, 100.0);

        return new MetricsData(
                serverIp,
                Instant.now(),
                systemCpuPercent,
                processCpuPercent,
                memoryUsedGB,
                memoryMaxGB,
                memoryPercent,
                sysUsedGB,
                sysTotalGB,
                sysMemPercent,
                tps,
                tpsPercent
        );
    }

    public double getSystemCpuUsage() {
        double val = osBean.getSystemCpuLoad();
        if (val >= 0.0) {
            lastSystemCpuPercent = round2(val * 100.0);
        }
        return lastSystemCpuPercent;
    }

    public double getProcessCpuUsage() {
        double val = osBean.getProcessCpuLoad();
        if (val >= 0.0) {
            lastProcessCpuPercent = round2(val * 100.0);
        }
        return lastProcessCpuPercent;
    }

    private double getSystemMemoryUsedGB() {
        try {
            long total = osBean.getTotalPhysicalMemorySize();
            long free = osBean.getFreePhysicalMemorySize();
            return bytesToGB(total - free);
        } catch (Exception ignored) {}
        return 0.0;
    }

    private double getSystemMemoryTotalGB() {
        try {
            long total = osBean.getTotalPhysicalMemorySize();
            return bytesToGB(total);
        } catch (Exception ignored) {}
        return 0.0;
    }

    private double getTPS() {
        try {
            Object mcServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            double[] tps = (double[]) mcServer.getClass().getField("recentTps").get(mcServer);
            return tps.length > 0 ? tps[0] : 20.0;
        } catch (Exception ignored) {}
        return 20.0;
    }

    private double bytesToGB(long bytes) {
        return round2(bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static record MetricsData(
            String serverIp,
            Instant timestamp,
            double systemCpuPercent,
            double processCpuPercent,
            double memoryUsedGB,
            double memoryMaxGB,
            double memoryPercent,
            double systemMemoryUsedGB,
            double systemMemoryTotalGB,
            double systemMemoryPercent,
            double tps,
            double tpsPercent
    ) {}
}