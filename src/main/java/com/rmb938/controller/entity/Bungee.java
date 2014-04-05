package com.rmb938.controller.entity;

import com.rmb938.controller.MN2ServerController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Bungee implements Runnable {

    private static final Logger logger = LogManager.getLogger(Bungee.class.getName());
    private MN2ServerController serverController;
    private long lastHeartBeat;
    private ArrayList<Plugin> plugins = new ArrayList<>();

    public Bungee(MN2ServerController serverController) {
        this.serverController = serverController;
        this.lastHeartBeat = -1;
    }

    public long getLastHeartBeat() {
        return lastHeartBeat;
    }

    public void setLastHeartBeat(long lastHeartBeat) {
        this.lastHeartBeat = lastHeartBeat;
    }

    public ArrayList<Plugin> getPlugins() {
        return plugins;
    }

    public void run() {
        try {
            Runtime runtime = Runtime.getRuntime();

            Process process = runtime.exec(new String[]{"rm", "-rf", "./runningServers/bungee"});
            process.waitFor();

            process = runtime.exec(new String[]{"mkdir", "./runningServers/bungee"});
            process.waitFor();

            process = runtime.exec(new String[]{"rsync", "-a", "./server/bungee/", "./runningServers/bungee/"});
            process.waitFor();

            for (Plugin plugin : plugins) {
                process = runtime.exec(new String[]{"rsync", "-a", "./plugins/"+plugin.getPluginName()+"/", "./runningServers/bungee/plugins"});
                process.waitFor();
            }

            process = runtime.exec(new String[] {"sed", "-i", "s/a.a.a.a/"+serverController.getMainConfig().publicIP+"/", "./runningServers/bungee/config.yml"});
            process.waitFor();

            process = runtime.exec(new String[] {"sed", "-i", "s/b.b.b.b/"+serverController.getMainConfig().privateIP+"/", "./runningServers/bungee/config.yml"});
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
            return;
        }

        ProcessBuilder builder = new ProcessBuilder("screen", "-dmS", "bungee", "./start.sh");
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
