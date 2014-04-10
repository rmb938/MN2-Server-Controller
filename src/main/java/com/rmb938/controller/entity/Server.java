package com.rmb938.controller.entity;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.jedis.JedisManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;

import java.io.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final Logger logger = LogManager.getLogger(Server.class.getName());
    private static final ConcurrentHashMap<String, Server> servers = new ConcurrentHashMap<>();

    public static ConcurrentHashMap<String, Server> getServers() {
        synchronized (servers) {
            return servers;
        }
    }

    public static Server getLocalServer(int port) {
        for (Server server : getLocalServers()) {
            if (server.getPort() == port) {
                return server;
            }
        }
        return null;
    }

    public static ArrayList<Server> getServers(ServerInfo serverInfo) {
        ArrayList<Server> servers1 = new ArrayList<>();
        for (Server server : servers.values()) {
            if (server.getServerInfo() == serverInfo) {
                if (server instanceof ClosingServer) {
                    continue;
                }
                servers1.add(server);
            }
        }
        return servers1;
    }

    public static ArrayList<ServerInfo> get75Full() {
        ArrayList<ServerInfo> infos = new ArrayList<>();
        for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
            int full = 0;
            ArrayList<Server> servers = getServers(serverInfo);
            for (Server server : servers) {
                if (server instanceof ClosingServer) {
                    continue;
                }
                int percent = ((server.getCurrentPlayers()/server.getServerInfo().getMaxPlayers())*100);
                if (percent >= 75) {
                    full += 1;
                }
            }
            if (full >= serverInfo.getMinServers()) {
                infos.add(serverInfo);
            }
        }
        return infos;
    }

    public static ArrayList<Server> getLocalServers() {
        ArrayList<Server> localServers = new ArrayList<>();
        for (Server server : servers.values()) {
            if (server instanceof RemoteServer) {
                continue;
            }
            localServers.add(server);
        }
        return localServers;
    }

    public static ArrayList<Server> getLocalServersNonClose() {
        ArrayList<Server> localServers = new ArrayList<>();
        for (Server server : servers.values()) {
            if (server instanceof RemoteServer) {
                continue;
            }
            if (server instanceof ClosingServer) {
                continue;
            }
            localServers.add(server);
        }
        return localServers;
    }

    private final ServerInfo serverInfo;
    private final MN2ServerController serverController;
    private final String serverUUID;
    private final int port;
    private int currentPlayers;
    private long lastHeartbeat;
    private int beatsEmpty;

    public Server(MN2ServerController serverController, ServerInfo serverInfo, String serverUUID, int port) {
        this.serverController = serverController;
        this.serverInfo = serverInfo;
        this.serverUUID = serverUUID;
        this.port = port;
        currentPlayers = 0;
        lastHeartbeat = -1;//starting up
        beatsEmpty = 0;
    }

    public String getServerUUID() {
        return serverUUID;
    }

    public int getPort() {
        return port;
    }

    public int getBeatsEmpty() {
        return beatsEmpty;
    }

    public void setBeatsEmpty(int beatsEmpty) {
        this.beatsEmpty = beatsEmpty;
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

    public boolean startServer() {
        if (serverInfo.getWorlds().isEmpty()) {
            logger.error("There are no worlds set for "+serverInfo.getServerName()+" FIX THIS!");
            return false;
        }
        if (serverInfo.getPlugins().isEmpty()) {
            logger.error("There are no plugins set for "+serverInfo.getServerName()+" FIX THIS!");
            return false;
        }
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

            PrintWriter writer = new PrintWriter(new FileOutputStream(new File("./runningServers/"+port+"/server.properties"), true));
            writer.println("server-port="+port);
            writer.println("server-ip="+serverController.getMainConfig().privateIP);
            writer.println("max-players=" + serverInfo.getMaxPlayers());

            World mainWorld = null;
            for (World world : serverInfo.getWorlds()) {
                if (world.getWorldConfig().mainWorld == true) {
                    mainWorld = world;
                    break;
                }
            }

            if (mainWorld == null) {
                logger.error("Could not find main world for server " + serverInfo.getServerName());
                return false;
            }

            writer.println("level-name=" + mainWorld.getWorldName());
            writer.flush();
            writer.close();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        Jedis jedis = JedisManager.getJedis();

        while (jedis.setnx("lock." + serverInfo.getServerName()+".key", System.currentTimeMillis() + 30000 + "") == 0) {
            String lock = jedis.get("lock." + serverInfo.getServerName()+".key");
            long time = Long.parseLong(lock != null ? lock : "0");
            if (System.currentTimeMillis() > time) {
                try {
                    time = Long.parseLong(jedis.getSet("lock." + serverInfo.getServerName() + ".key", System.currentTimeMillis() + 30000 + ""));
                } catch (Exception ex) {
                    time = 0;
                }
                if (System.currentTimeMillis() < time) {
                    continue;
                }
            } else {
                continue;
            }
            break;
        }

        Set<String> keys = jedis.keys("server." + serverInfo.getServerName() + ".*");
        ArrayList<Integer> ids = new ArrayList<>();
        int startId = 1;
        for (String keyName : keys) {
            int id = Integer.parseInt(keyName.split("\\.")[2]);
            ids.add(id);
        }

        while (ids.contains(startId)) {
            startId += 1;
        }

        int serverNumber = startId;

        jedis.set("server."+serverInfo.getServerName()+"."+serverNumber, serverUUID);
        jedis.del("lock." + serverInfo.getServerName()+".key");
        JedisManager.returnJedis(jedis);

        try {
            PrintWriter writer = new PrintWriter(new FileOutputStream(new File("./runningServers/"+port+"/server.properties"), true));
            writer.println("server-name="+serverInfo.getServerName()+"."+serverNumber);
            writer.flush();
            writer.close();
        } catch (FileNotFoundException e) {
            jedis.del("server."+serverInfo.getServerName()+"."+serverNumber);
            e.printStackTrace();
            return false;
        }

        jedis.set(serverController.getMainConfig().privateIP+"."+port, serverInfo.getServerName());
        jedis.set(serverController.getMainConfig().privateIP+"."+port+".uuid", serverUUID);

        logger.info("Set Name: "+jedis.get(serverController.getMainConfig().privateIP+"."+port)+" Number: "+serverNumber+" UUID: "+jedis.get(serverController.getMainConfig().privateIP+"."+port+".uuid"));


        ProcessBuilder builder = new ProcessBuilder("screen", "-dmS", serverInfo.getServerName()+"."+port, "./start.sh", serverInfo.getMemory()+"");
        builder.directory(new File("./runningServers/"+port));//sets working directory

        logger.info("Running Server Process for " + port);
        try {
            lastHeartbeat = System.currentTimeMillis()+60000;
            builder.start();
            Server.getServers().put(serverUUID, this);
        } catch (IOException e) {
            jedis.del(serverController.getMainConfig().privateIP+"."+port);
            jedis.del(serverController.getMainConfig().privateIP+"."+port+".uuid");
            jedis.del("server."+serverInfo.getServerName()+"."+serverNumber);
            logger.error("Unable to start server "+serverInfo.getServerName()+" with port "+port);
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
            return false;
        }
        return true;
    }

}
