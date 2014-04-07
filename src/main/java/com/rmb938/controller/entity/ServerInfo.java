package com.rmb938.controller.entity;

import java.util.ArrayList;
import java.util.HashMap;

public class ServerInfo {

    private static HashMap<String, ServerInfo> serverInfos = new HashMap<>();

    public static HashMap<String, ServerInfo> getServerInfos() {
        return serverInfos;
    }

    private ArrayList<World> worlds = new ArrayList<>();
    private ArrayList<Plugin> plugins = new ArrayList<>();

    private final String serverName;
    private final int maxPlayers;
    private final int minServers;
    private final int memory;

    public ServerInfo(String serverName, int maxPlayers, int minServers, int memory) {
        this.serverName = serverName;
        this.maxPlayers = maxPlayers;
        this.minServers = minServers;
        this.memory = memory;
    }

    public int getMemory() {
        return memory;
    }

    public int getMinServers() {
        return minServers;
    }

    public ArrayList<World> getWorlds() {
        return worlds;
    }

    public ArrayList<Plugin> getPlugins() {
        return plugins;
    }

    public String getServerName() {
        return serverName;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

}
