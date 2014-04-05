package com.rmb938.controller.entity;

import com.rmb938.controller.config.WorldConfig;

import java.util.HashMap;

public class World {

    private static HashMap<String, World> worlds = new HashMap<>();

    public static HashMap<String, World> getWorlds() {
        return worlds;
    }

    private final String worldName;
    private final WorldConfig worldConfig;

    public World(String worldName, WorldConfig worldConfig) {
        this.worldName = worldName;
        this.worldConfig = worldConfig;
    }

    public String getWorldName() {
        return worldName;
    }

    public WorldConfig getWorldConfig() {
        return worldConfig;
    }
}
