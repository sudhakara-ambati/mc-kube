package com.mckube.javaplugin.controllers;

import io.javalin.Javalin;

import java.util.HashMap;
import java.util.Map;

public class HealthController {

    public void registerRoutes(Javalin app) {
        app.get("/health", ctx -> {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "healthy");
            health.put("timestamp", java.time.Instant.now().toString());
            health.put("uptime", System.currentTimeMillis());
            ctx.status(200).json(health);
        });
    }
}