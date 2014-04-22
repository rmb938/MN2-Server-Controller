package com.rmb938.controller.entity;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.jedis.JedisManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Bungee implements Runnable {

    private static final Logger logger = LogManager.getLogger(Bungee.class.getName());
    private MN2ServerController serverController;
    private ArrayList<Plugin> plugins = new ArrayList<>();

    public Bungee(MN2ServerController serverController) {
        this.serverController = serverController;
    }

    public ArrayList<Plugin> getPlugins() {
        return plugins;
    }

    public void run() {
        if (getPlugins().isEmpty()) {
            logger.error("There are no plugins set for bungee FIX THIS!");
            return;
        }
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
        } catch (IOException | InterruptedException e) {
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
            return;
        }

        ProcessBuilder builder = new ProcessBuilder("screen", "-dmS", "bungee", "./start.sh");
        builder.directory(new File("./runningServers/bungee"));//sets working directory

        Jedis jedis = JedisManager.getJedis();
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("privateIP", serverController.getMainConfig().privateIP);
            jsonObject.put("currentPlayers", 0);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        jedis.set(serverController.getMainConfig().publicIP+":bungee", jsonObject.toString());
        jedis.expire(serverController.getMainConfig().publicIP+":bungee", 120);
        JedisManager.returnJedis(jedis);

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
