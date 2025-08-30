package com.mckube.javaplugin.services;

import org.slf4j.Logger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.mckube.javaplugin.services.MetricsData;

public class MetricsService {

    private final Map<String, MetricsData> metricsMap = new ConcurrentHashMap<>();
    private final Logger logger;
    private LogsService logsService;

    public MetricsService(Logger logger) {
        this.logger = logger;
    }

    public void setLogsService(LogsService logsService) {
        this.logsService = logsService;
    }

    public void putMetrics(String serverIp, MetricsData data) {
        try {
            metricsMap.put(serverIp, data);
            logger.debug("Updated metrics for server: {}", serverIp);

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("server_ip", serverIp);
                metadata.put("system_cpu_percent", data.systemCpuPercent());
                metadata.put("process_cpu_percent", data.processCpuPercent());
                metadata.put("memory_used_gb", data.memoryUsedGB());
                metadata.put("memory_max_gb", data.memoryMaxGB());
                metadata.put("memory_percent", data.memoryPercent());
                metadata.put("system_memory_used_gb", data.systemMemoryUsedGB());
                metadata.put("system_memory_total_gb", data.systemMemoryTotalGB());
                metadata.put("system_memory_percent", data.systemMemoryPercent());
                metadata.put("tps", data.tps());
                metadata.put("tps_percent", data.tpsPercent());
                metadata.put("collection_time", data.timestamp().toString());
                metadata.put("total_servers_tracked", metricsMap.size());

                logsService.logMetricsCollected("Metrics collected and stored for server", serverIp, metadata);
            }

        } catch (Exception e) {
            logger.error("Error storing metrics for server: {}", serverIp, e);

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("server_ip", serverIp);
                metadata.put("error", e.getMessage());
                metadata.put("exception_class", e.getClass().getSimpleName());
                metadata.put("attempted_timestamp", Instant.now().toString());

                logsService.logMetricsFailed("Failed to store metrics for server", serverIp, metadata);
            }

            throw e;
        }
    }

    public MetricsData getMetrics(String serverIp) {
        try {
            MetricsData data = metricsMap.get(serverIp);

            if (data == null) {
                logger.debug("No metrics found for server: {}", serverIp);

                if (logsService != null) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("server_ip", serverIp);
                    metadata.put("available_servers", metricsMap.keySet());
                    metadata.put("total_servers_tracked", metricsMap.size());
                    metadata.put("request_time", Instant.now().toString());

                    logsService.logMetricsFailed("Metrics retrieval failed - server not found", serverIp, metadata);
                }
            } else {
                logger.debug("Retrieved metrics for server: {}", serverIp);

                if (logsService != null) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("server_ip", serverIp);
                    //metadata.put("data_age_seconds", Instant.now().getEpochSecond() - data.timestamp());
                    metadata.put("system_cpu_percent", data.systemCpuPercent());
                    metadata.put("process_cpu_percent", data.processCpuPercent());
                    metadata.put("memory_percent", data.memoryPercent());
                    metadata.put("tps", data.tps());
                    metadata.put("retrieval_time", Instant.now().toString());

                    logsService.logMetricsCollected("Metrics retrieved for server", serverIp, metadata);
                }
            }

            return data;

        } catch (Exception e) {
            logger.error("Error retrieving metrics for server: {}", serverIp, e);

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("server_ip", serverIp);
                metadata.put("error", e.getMessage());
                metadata.put("exception_class", e.getClass().getSimpleName());
                metadata.put("request_time", Instant.now().toString());

                logsService.logMetricsFailed("Error retrieving metrics for server", serverIp, metadata);
            }

            throw e;
        }
    }

    public Map<String, MetricsData> getAllMetrics() {
        return new HashMap<>(metricsMap);
    }

    public boolean removeMetrics(String serverIp) {
        try {
            MetricsData removed = metricsMap.remove(serverIp);
            boolean wasRemoved = removed != null;

            if (wasRemoved) {
                logger.info("Removed metrics for server: {}", serverIp);

                if (logsService != null) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("server_ip", serverIp);
                    metadata.put("last_update", removed.timestamp().toString());
                    metadata.put("removal_time", Instant.now().toString());
                    metadata.put("remaining_servers", metricsMap.size());

                    logsService.logSystemEvent("Metrics data removed for server", null, null, serverIp, metadata);
                }
            } else {
                logger.debug("No metrics to remove for server: {}", serverIp);
            }

            return wasRemoved;

        } catch (Exception e) {
            logger.error("Error removing metrics for server: {}", serverIp, e);

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("server_ip", serverIp);
                metadata.put("error", e.getMessage());
                metadata.put("exception_class", e.getClass().getSimpleName());

                logsService.logError("Error removing metrics for server: " + serverIp, "MetricsService.removeMetrics", e);
            }

            return false;
        }
    }

    public void clearAllMetrics() {
        try {
            int clearedCount = metricsMap.size();
            metricsMap.clear();
            logger.info("Cleared all metrics data ({} servers)", clearedCount);

            if (logsService != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("servers_cleared", clearedCount);
                metadata.put("clear_time", Instant.now().toString());

                logsService.logSystemEvent("All metrics data cleared", metadata);
            }

        } catch (Exception e) {
            logger.error("Error clearing all metrics", e);

            if (logsService != null) {
                logsService.logError("Error clearing all metrics data", "MetricsService.clearAllMetrics", e);
            }
        }
    }
}