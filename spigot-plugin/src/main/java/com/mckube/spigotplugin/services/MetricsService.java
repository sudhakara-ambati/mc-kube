package com.mckube.spigotplugin.services;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import com.sun.management.OperatingSystemMXBean;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

public class MetricsService {
    private final JavaPlugin plugin;
    private final String serverIp;
    private final OperatingSystemMXBean osBean;
    private final RuntimeMXBean runtimeBean;

    private final SystemInfo systemInfo = new SystemInfo();
    private final CentralProcessor processor = systemInfo.getHardware().getProcessor();
    private final OperatingSystem os = systemInfo.getOperatingSystem();
    private final int pid = os.getProcessId();

    private long[] prevSystemTicks = processor.getSystemCpuLoadTicks();
    private long prevProcessCpuTime = getProcessCpuTime();
    private long prevSampleTime = System.nanoTime();

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
        long[] systemTicks = processor.getSystemCpuLoadTicks();
        double systemCpuPercent = processor.getSystemCpuLoadBetweenTicks(prevSystemTicks) * 100.0;
        prevSystemTicks = systemTicks;
        return Math.round(systemCpuPercent * 100.0) / 100.0;
    }

    public double getProcessCpuUsage() {
        long processCpuTime = getProcessCpuTime();
        long now = System.nanoTime();
        long elapsedNanos = now - prevSampleTime;
        double processCpuPercent = 0.0;
        if (elapsedNanos > 0) {
            processCpuPercent = ((double)(processCpuTime - prevProcessCpuTime) / elapsedNanos) * 100.0 * processor.getLogicalProcessorCount();
        }
        prevProcessCpuTime = processCpuTime;
        prevSampleTime = now;
        return Math.round(processCpuPercent * 100.0) / 100.0;
    }

    private long getProcessCpuTime() {
        OSProcess proc = os.getProcess(pid);
        if (proc == null) return 0;
        return proc.getKernelTime() + proc.getUserTime();
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
        return Math.round((bytes / 1024.0 / 1024.0 / 1024.0) * 100.0) / 100.0;
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