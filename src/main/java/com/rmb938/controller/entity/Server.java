package com.rmb938.controller.entity;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.jedis.JedisManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

import java.io.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class Server {

    private static final Logger logger = LogManager.getLogger(Server.class.getName());

    public static boolean getLocalServer(int port, MN2ServerController serverController) {
        Jedis jedis = JedisManager.getJedis();
        Set<String> keys = jedis.keys("server.*.*");
        for (String key : keys) {
            String data = jedis.get(key);
            if (data != null) {
                try {
                    JSONObject jsonObject = new JSONObject(data);
                    String ip = jsonObject.getString("serverIP");
                    if (ip.equalsIgnoreCase(serverController.getMainConfig().privateIP) == false) {
                        continue;
                    }
                    int p = jsonObject.getInt("serverPort");
                    if (p == port) {
                        return true;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        JedisManager.returnJedis(jedis);
        return false;
    }

    public static ArrayList<String> getServers(ServerInfo serverInfo) {
        Jedis jedis = JedisManager.getJedis();
        Set<String> keys = jedis.keys("server." + serverInfo.getServerName() + ".*");
        ArrayList<String> servers = new ArrayList<>();
        for (String key : keys) {
            String data = jedis.get(key);
            if (data != null) {
                try {
                    JSONObject jsonObject = new JSONObject(data);
                    String uuid = jsonObject.getString("uuid");
                    servers.add(uuid);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        JedisManager.returnJedis(jedis);
        return servers;
    }

    public static ArrayList<ServerInfo> get75Full() {
        ArrayList<ServerInfo> infos = new ArrayList<>();
        Jedis jedis = JedisManager.getJedis();
        for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
            Set<String> keys = jedis.keys("server." + serverInfo.getServerName() + ".*");
            ArrayList<ServerInfo> serverInfos = new ArrayList<>();
            for (String key : keys) {
                String data = jedis.get(key);
                if (data != null) {
                    try {
                        JSONObject jsonObject = new JSONObject(data);
                        int currentPlayers = jsonObject.getInt("currentPlayers");
                        double full = (currentPlayers / serverInfo.getMaxPlayers()) * 100.0;
                        if (full >= 75) {
                            serverInfos.add(serverInfo);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (serverInfos.size() >= keys.size() && serverInfos.isEmpty() == false) {
                infos.add(serverInfo);
            }
        }
        JedisManager.returnJedis(jedis);
        return infos;
    }

    public static ArrayList<String> getLocalServers(MN2ServerController serverController) {
        ArrayList<String> localServers = new ArrayList<>();
        Jedis jedis = JedisManager.getJedis();
        Set<String> keys = jedis.keys("server.*.*");
        for (String key : keys) {
            String data = jedis.get(key);
            if (data != null) {
                try {
                    JSONObject jsonObject = new JSONObject(data);
                    String uuid = jsonObject.getString("uuid");
                    String ip = jsonObject.getString("serverIP");
                    if (ip.equalsIgnoreCase(serverController.getMainConfig().privateIP) == false) {
                        continue;
                    }
                    localServers.add(uuid);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        JedisManager.returnJedis(jedis);
        return localServers;
    }

    public static ArrayList<String> getLocalEmpty(MN2ServerController serverController, int seconds) {
        ArrayList<String> empty = new ArrayList<>();
        Jedis jedis = JedisManager.getJedis();
        Set<String> keys = jedis.keys("server.*.*");
        for (String key : keys) {
            String data = jedis.get(key);
            if (data != null) {
                try {
                    JSONObject jsonObject = new JSONObject(data);
                    String uuid = jsonObject.getString("uuid");
                    String ip = jsonObject.getString("serverIP");
                    int timeEmpty = jsonObject.getInt("timeEmpty");
                    if (ip.equalsIgnoreCase(serverController.getMainConfig().privateIP) == false) {
                        continue;
                    }
                    if (timeEmpty >= seconds) {
                        empty.add(uuid);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        JedisManager.returnJedis(jedis);
        return empty;
    }

    public static int getOnlinePlayers() {
        int online = 0;
        Jedis jedis = JedisManager.getJedis();
        Set<String> keys = jedis.keys("server.*.*");
        for (String key : keys) {
            String data = jedis.get(key);
            if (data != null) {
                try {
                    JSONObject jsonObject = new JSONObject(data);
                    online += jsonObject.getInt("currentPlayers");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        JedisManager.returnJedis(jedis);
        return online;
    }

    public static boolean startServer(ServerInfo serverInfo, MN2ServerController serverController, int port) {
        if (serverInfo.getWorlds().isEmpty()) {
            logger.error("There are no worlds set for " + serverInfo.getServerName() + " FIX THIS!");
            return false;
        }
        if (serverInfo.getPlugins().isEmpty()) {
            logger.error("There are no plugins set for " + serverInfo.getServerName() + " FIX THIS!");
            return false;
        }
        try {
            Runtime runtime = Runtime.getRuntime();

            Process process = runtime.exec(new String[]{"rm", "-rf", "./runningServers/" + port});
            process.waitFor();

            process = runtime.exec(new String[]{"mkdir", "./runningServers/" + port});
            process.waitFor();

            process = runtime.exec(new String[]{"rsync", "-a", "./server/spigot/", "./runningServers/" + port + "/"});
            process.waitFor();

            for (World world : serverInfo.getWorlds()) {
                process = runtime.exec(new String[]{"rsync", "-a", "./worlds/" + world.getWorldName(), "./runningServers/" + port + "/worlds"});
                process.waitFor();
            }

            for (Plugin plugin : serverInfo.getPlugins()) {
                process = runtime.exec(new String[]{"rsync", "-a", "./plugins/" + plugin.getPluginName() + "/", "./runningServers/" + port + "/plugins"});
                process.waitFor();
            }

            PrintWriter writer = new PrintWriter(new FileOutputStream(new File("./runningServers/" + port + "/server.properties"), true));
            writer.println("server-port=" + port);
            writer.println("server-ip=" + serverController.getMainConfig().privateIP);
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

        while (jedis.setnx("lock." + serverInfo.getServerName() + ".key", System.currentTimeMillis() + 30000 + "") == 0) {
            String lock = jedis.get("lock." + serverInfo.getServerName() + ".key");
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
        String serverUUID = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("uuid", serverUUID);
            jsonObject.put("serverIP", serverController.getMainConfig().privateIP);
            jsonObject.put("serverPort", port);
            jsonObject.put("serverName", serverInfo.getServerName());
            jsonObject.put("serverId", serverNumber);
            jsonObject.put("currentPlayers", 0);
            jsonObject.put("timeEmpty", 0);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        jedis.set("server." + serverInfo.getServerName() + "." + serverNumber, jsonObject.toString());
        jedis.expire("server." + serverInfo.getServerName() + "." + serverNumber, 120);
        jedis.del("lock." + serverInfo.getServerName() + ".key");

        JedisManager.returnJedis(jedis);

        try {
            PrintWriter writer = new PrintWriter(new FileOutputStream(new File("./runningServers/" + port + "/server.properties"), true));
            writer.println("server-name=" + serverInfo.getServerName() + "." + serverNumber);
            writer.flush();
            writer.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        logger.info("Set Name: " + serverInfo.getServerName() + " Number: " + serverNumber + " UUID: " + serverUUID);


        ProcessBuilder builder = new ProcessBuilder("screen", "-dmS", serverInfo.getServerName() + "." + serverNumber, "./start.sh", serverInfo.getMemory() + "");
        builder.directory(new File("./runningServers/" + port));//sets working directory

        logger.info("Running Server Process for " + port);
        try {
            builder.start();
        } catch (IOException e) {
            logger.error("Unable to start server " + serverInfo.getServerName() + " with port " + port);
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
            return false;
        }
        return true;
    }

}
