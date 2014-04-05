package com.rmb938.controller.entity;

import java.util.HashMap;

public class Plugin {

    private static HashMap<Integer, Plugin> plugins = new HashMap<>();

    public static HashMap<Integer, Plugin> getPlugins() {
        return plugins;
    }

    private int pluginId;
    private String pluginName;

    public int getPluginId() {
        return pluginId;
    }

    public void setPluginId(int pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }
}
