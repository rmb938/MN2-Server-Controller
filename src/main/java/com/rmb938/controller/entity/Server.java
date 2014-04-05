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

public class Server {

    private static final Logger logger = LogManager.getLogger(Server.class.getName());
    private static ConcurrentHashMap<String, Server> servers = new ConcurrentHashMap<>();

    public static ConcurrentHashMap<String, Server> getServers() {
        return servers;
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
                if (((server.getCurrentPlayers()/server.getServerInfo().getMaxPlayers())*100) >= 75) {
                    full += 1;
                }
            }
            if (full >= servers.size()) {
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

            process = runtime.exec(new String[]{"echo", "server-port="+port, ">>", "./runningServers/"+port+"/server.properties"});
            process.waitFor();

            process = runtime.exec(new String[]{"echo", "server-ip="+serverController.getMainConfig().privateIP, ">>", "./runningServers/"+port+"/server.properties"});
            process.waitFor();

            process = runtime.exec(new String[]{"echo", "max-players="+serverInfo.getMaxPlayers(), ">>", "./runningServers/"+port+"/server.properties"});
            process.waitFor();

            World mainWorld = serverInfo.getWorlds().get(0);
            for (World world : serverInfo.getWorlds()) {
                if (world.getWorldConfig().mainWorld == true) {
                    mainWorld = world;
                    break;
                }
            }

            process = runtime.exec(new String[]{"echo", "level-name="+mainWorld.getWorldName(), ">>", "./runningServers/"+port+"/server.properties"});
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        Jedis jedis = JedisManager.getJedis();
        jedis.set(serverController.getMainConfig().privateIP+"."+port, serverInfo.getServerName());
        jedis.set(serverController.getMainConfig().privateIP+"."+port+".uuid", serverUUID);
        JedisManager.returnJedis(jedis);

        ProcessBuilder builder = new ProcessBuilder("screen", "-dmS", serverInfo.getServerName()+"."+port, "./start.sh");
        builder.directory(new File("./runningServers/"+port));//sets working directory

        logger.info("Running Server Process for " + port);
        try {
            builder.start();
            Server.getServers().put(serverController.getMainConfig().privateIP+"."+port, this);
        } catch (IOException e) {
            logger.error("Unable to start server "+serverInfo.getServerName()+" with port "+port);
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
            return false;
        }
        return true;
    }

}
