package com.rmb938.controller.entity;

import java.util.HashMap;

public class World {

    private static HashMap<String, World> worlds = new HashMap<>();

    public static HashMap<String, World> getWorlds() {
        return worlds;
    }

    private String worldName;

    public World(String worldName) {
        this.worldName = worldName;
    }

    public String getWorldName() {
        return worldName;
    }
}
