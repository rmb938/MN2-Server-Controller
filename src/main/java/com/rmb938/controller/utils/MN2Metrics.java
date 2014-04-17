package com.rmb938.controller.utils;

import com.rmb938.controller.entity.Server;
import org.mcstats.Metrics;

import java.io.File;
import java.io.IOException;

public class MN2Metrics extends Metrics {

    public MN2Metrics(String pluginName, String pluginVersion) throws IOException {
        super(pluginName, pluginVersion);
    }

    @Override
    public String getFullServerVersion() {
        return "MN2-Network";
    }

    @Override
    public int getPlayersOnline() {
        return Server.getOnlinePlayers();
    }

    @Override
    public File getConfigFile() {
        return new File("metrics.yml");
    }
}
