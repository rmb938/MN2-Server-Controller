package com.rmb938.controller.entity;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.jedis.JedisManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class Server implements Runnable {

    private static final Logger logger = LogManager.getLogger(Server.class.getName());
    private static ConcurrentHashMap<String, Server> servers = new ConcurrentHashMap<>();

    public static ConcurrentHashMap<String, Server> getServers() {
        return servers;
    }

    public static ArrayList<Server> getServers(ServerInfo serverInfo) {
        ArrayList<Server> servers1 = new ArrayList<>();
        for (Server server : servers.values()) {
            if (server.getServerInfo() == serverInfo) {
                servers1.add(server);
            }
        }
        return servers1;
    }

    private final ServerInfo serverInfo;
    private final MN2ServerController serverController;
    private final int port;
    private int currentPlayers;
    private long lastHeartbeat;

    public Server(MN2ServerController serverController, ServerInfo serverInfo, int port) {
        this.serverController = serverController;
        this.serverInfo = serverInfo;
        this.port = port;
        currentPlayers = 0;
        lastHeartbeat = -1;//starting up
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public int getCurrentPlayers() {
        return currentPlayers;
    }

    public void setCurrentPlayers(int currentPlayers) {
        this.currentPlayers = currentPlayers;
    }

    public void run() {
        try {
            Runtime runtime = Runtime.getRuntime();

            Process process = runtime.exec(new String[]{"rm", "-rf", "./runningServers/"+port});
            process.waitFor();

            process = runtime.exec(new String[]{"mkdir", "./runningServers/"+port});
            process.waitFor();

            process = runtime.exec(new String[]{"rsync", "-a", "./server/spigot/", "./runningServers/"+port+"/"});
            process.waitFor();

            for (World world : serverInfo.getWorlds()) {
                process = runtime.exec(new String[]{"rsync", "-a", "./worlds/"+world.getWorldName(), "./runningServers/"+port+"/worlds"});
                process.waitFor();
            }

            for (Plugin plugin : serverInfo.getPlugins()) {
                process = runtime.exec(new String[]{"rsync", "-a", "./plugins/"+plugin.getPluginName()+"/", "./runningServers/"+port+"/plugins"});
                process.waitFor();
            }

            //TODO: edit server.properties with port, IP and maxPlayers
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return;
        }

        Jedis jedis = JedisManager.getJedis();
        jedis.set(MN2ServerController.getServerController().getControllerIP()+"."+port, serverInfo.getServerName());
        JedisManager.returnJedis(jedis);

        ProcessBuilder builder = new ProcessBuilder("screen", "-S", serverInfo.getServerName()+"."+port, "./runningServers/"+port+"/start.sh");
        builder.directory(new File("./runningServers/"+port));//sets working directory

        logger.info("Running Server Process for " + port);
        try {
            builder.start();
            Server.getServers().put(serverController.getControllerIP()+"."+port, this);
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Unable to start server "+serverInfo.getServerName()+" with port "+port);
        }
        Thread.yield();
    }

    public void startServer() {
        Thread server = new Thread(this);
        server.start();
    }

}
