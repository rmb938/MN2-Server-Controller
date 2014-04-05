package com.rmb938.controller.entity;

import java.util.HashMap;

public class World {

    private static HashMap<Integer, World> worlds = new HashMap<>();

    public static HashMap<Integer, World> getWorlds() {
        return worlds;
    }

    private int worldId;
    private String worldName;

    public int getWorldId() {
        return worldId;
    }

    public void setWorldId(int worldId) {
        this.worldId = worldId;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }
}
