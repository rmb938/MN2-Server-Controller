package com.rmb938.controller.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Bungee implements Runnable {

    private static final Logger logger = LogManager.getLogger(Bungee.class.getName());

    private ArrayList<Plugin> plugins = new ArrayList<>();

    public void run() {
        try {
            Runtime runtime = Runtime.getRuntime();

            Process process = runtime.exec(new String[]{"rm", "-rf", "./runningServers/bungee"});
            process.waitFor();

            process = runtime.exec(new String[]{"mkdir", "./runningServers/bungee"});
            process.waitFor();

            process = runtime.exec(new String[]{"rsync", "-a", "./server/spigot/", "./runningServers/bungee/"});
            process.waitFor();

            for (Plugin plugin : plugins) {
                process = runtime.exec(new String[]{"rsync", "-a", "./plugins/"+plugin.getPluginName()+"/", "./runningServers/bungee/plugins"});
                process.waitFor();
            }

            //TODO: edit server.properties with port, IP and maxPlayers
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return;
        }

        ProcessBuilder builder = new ProcessBuilder("screen", "-S", "bungee", "./runningServers/bungee/start.sh");
        builder.directory(new File("./runningServers/bungee"));//sets working directory

        logger.info("Running Bungee Process");
        try {
            builder.start();
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Unable to start bungee");
        }
        Thread.yield();
    }

    public void startBungee() {
        Thread server = new Thread(this);
        server.start();
    }

}
