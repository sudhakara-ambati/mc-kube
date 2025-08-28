package com.mckube.spigot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
public class MetricsService {

    private final JavaPlugin plugin;
    private final String serverIp;
    private final OperatingSystemMXBean osBean;
    private final RuntimeMXBean runtimeBean;

    public MetricsService(JavaPlugin plugin, String serverIp) {
        this.plugin = plugin;
        this.serverIp = serverIp;
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
    }

    public MetricsData collectMetrics() {
        double cpuPercent = getProcessCpuLoad() * 100.0;

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
                java.time.Instant.now().toString(),
                cpuPercent,
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

    private double getProcessCpuLoad() {
        try {
            var method = osBean.getClass().getMethod("getProcessCpuLoad");
            method.setAccessible(true);
            Object value = method.invoke(osBean);
            if (value instanceof Double) {
                double v = (Double) value;
                return v < 0 ? 0 : v; // -1.0 if not available
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    private double getSystemMemoryUsedGB() {
        try {
            var method = osBean.getClass().getMethod("getTotalPhysicalMemorySize");
            var freeMethod = osBean.getClass().getMethod("getFreePhysicalMemorySize");
            long total = (long) method.invoke(osBean);
            long free = (long) freeMethod.invoke(osBean);
            return bytesToGB(total - free);
        } catch (Exception ignored) {}
        return 0.0;
    }

    private double getSystemMemoryTotalGB() {
        try {
            var method = osBean.getClass().getMethod("getTotalPhysicalMemorySize");
            long total = (long) method.invoke(osBean);
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
        return Math.round((bytes / 1024.0 / 1024.0 / 1024.0) * 100.0) / 100.0;
    }

    public static record MetricsData(
            String serverIp,
            String timestamp,
            double cpuPercent,
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