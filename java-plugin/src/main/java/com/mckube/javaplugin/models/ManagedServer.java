package com.mckube.javaplugin.models;

import java.time.Instant;

public class ManagedServer {
    public String name;
    public String ip;
    public int port;
    public int maxPlayers;
    public boolean enabled;
    public String createdAt;
    public String lastModified;

    public ManagedServer() {
        this.createdAt = Instant.now().toString();
        this.lastModified = Instant.now().toString();
    }

    public ManagedServer(String name, String ip, int port, int maxPlayers, boolean enabled) {
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.enabled = enabled;
        this.createdAt = Instant.now().toString();
        this.lastModified = Instant.now().toString();
    }
}