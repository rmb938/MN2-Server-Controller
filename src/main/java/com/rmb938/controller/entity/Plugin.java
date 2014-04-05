package com.rmb938.controller.entity;

import java.util.HashMap;

public class Plugin {

    private static HashMap<String, Plugin> plugins = new HashMap<>();

    public static HashMap<String, Plugin> getPlugins() {
        return plugins;
    }

    private final String pluginName;

    public Plugin(String pluginName) {
        this.pluginName = pluginName;
    }

    public String getPluginName() {
        return pluginName;
    }
}
