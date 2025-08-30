package com.mckube.javaplugin.services;

public record MetricsData(
        String serverIp,
        String timestamp,
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