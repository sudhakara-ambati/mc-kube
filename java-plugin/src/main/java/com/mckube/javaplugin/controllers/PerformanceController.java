package com.mckube.javaplugin.controllers;

import com.mckube.javaplugin.services.ServerListService;
import com.mckube.javaplugin.utils.ControllerUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;

public class PerformanceController {

    private final Logger logger;
    private final ServerListService serverListService;

    public PerformanceController(Logger logger, ServerListService serverListService) {
        this.logger = logger;
        this.serverListService = serverListService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/performance/stats", this::handlePerformanceStats);
        app.post("/performance/cache/clear", this::handleCacheClear);
    }

    private void handlePerformanceStats(Context ctx) {
        try {
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

            Map<String, Object> stats = new HashMap<>();
            
            
            stats.put("uptime_ms", runtimeBean.getUptime());
            stats.put("uptime_hours", runtimeBean.getUptime() / (1000.0 * 60 * 60));
            
            
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
            stats.put("heap_used_mb", usedMemory / (1024 * 1024));
            stats.put("heap_max_mb", maxMemory / (1024 * 1024));
            stats.put("heap_usage_percent", (double) usedMemory / maxMemory * 100.0);
            
            
            stats.put("thread_count", ManagementFactory.getThreadMXBean().getThreadCount());
            
            
            stats.put("available_processors", Runtime.getRuntime().availableProcessors());
            
            Map<String, Object> response = ControllerUtils.createSuccessResponse("Performance stats retrieved successfully");
            response.put("stats", stats);
            
            ctx.status(200).json(response);
        } catch (Exception e) {
            logger.error("Error retrieving performance stats", e);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to retrieve performance stats"));
        }
    }

    private void handleCacheClear(Context ctx) {
        try {
            serverListService.clearCache();
            logger.info("Server list cache cleared manually");
            
            Map<String, Object> response = ControllerUtils.createSuccessResponse("Cache cleared successfully");
            ctx.status(200).json(response);
        } catch (Exception e) {
            logger.error("Error clearing cache", e);
            ctx.status(500).json(ControllerUtils.createErrorResponse("Failed to clear cache"));
        }
    }
}
