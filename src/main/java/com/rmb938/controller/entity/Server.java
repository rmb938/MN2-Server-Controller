package com.rmb938.controller.entity;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.jedis.JedisManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

import java.io.*;
import java.util.*;

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
                        JedisManager.returnJedis(jedis);
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
        final Jedis jedis = JedisManager.getJedis();
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

    public static ArrayList<String> getServerKeys(ServerInfo serverInfo) {
        final Jedis jedis = JedisManager.getJedis();
        Set<String> keys = jedis.keys("server." + serverInfo.getServerName() + ".*");
        ArrayList<String> servers = new ArrayList<>();
        for (String key : keys) {
            String data = jedis.get(key);
            if (data != null) {
                servers.add(key);
            }
        }
        Collections.sort(servers, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                int id1 = Integer.parseInt(o1.split("\\.")[2]);
                int id2 = Integer.parseInt(o2.split("\\.")[2]);

                if (id1 < id2) {
                    return -1;
                }

                if (id1 > id2) {
                    return 1;
                }

                return 0;
            }
        });
        JedisManager.returnJedis(jedis);
        return servers;
    }

    public static ArrayList<ServerInfo> get50Full() {
        ArrayList<ServerInfo> infos = new ArrayList<>();
        for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
            int max = Server.getMaxPlayers(serverInfo);
            int online = Server.getOnlinePlayers(serverInfo);
            if (max <= 0) {
                continue;
            }
            double percent = (online / (max * 1.0)) * 100;
            logger.info(serverInfo.getServerName() + " Online: " + online + " Max: " + max + " Percent: " + percent);
            if (percent >= 50) {
                infos.add(serverInfo);
            }
        }
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

    public static int getOnlinePlayers(ServerInfo serverInfo) {
        int online = 0;
        Jedis jedis = JedisManager.getJedis();
        Set<String> keys = jedis.keys("server." + serverInfo.getServerName() + ".*");
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

    public static int getMaxPlayers(ServerInfo serverInfo) {
        int max = 0;
        Jedis jedis = JedisManager.getJedis();
        Set<String> keys = jedis.keys("server." + serverInfo.getServerName() + ".*");
        for (String key : keys) {
            String data = jedis.get(key);
            if (data != null) {
                try {
                    JSONObject jsonObject = new JSONObject(data);
                    max += jsonObject.getInt("maxPlayers");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        JedisManager.returnJedis(jedis);
        return max;
    }

    public static boolean startServer(ServerInfo serverInfo, MN2ServerController serverController, int port) {
        try {
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

                String oldName = null;

                Process process = runtime.exec(new String[] {"./killOnPort.sh", port+""});
                process.waitFor();

                if ((new File("./runningServers/" + port + "/server.properties").exists() == true)) {
                    Scanner scanner = new Scanner(new FileInputStream(new File("./runningServers/" + port + "/server.properties")));
                    while (scanner.hasNext()) {
                        String line = scanner.nextLine();
                        if (line.contains("server-name=")) {
                            oldName = line.replace("server-name=", "");
                            break;
                        }
                    }
                }

                if (oldName != null) {
                    process = runtime.exec(new String[]{"mv", "./runningServers/" + port + "/logs/latest.log", serverController.getLogsFolder().getAbsolutePath() + "/" + oldName + ".log"});
                    process.waitFor();
                }

                process = runtime.exec(new String[]{"rm", "-rf", "./runningServers/" + port});
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
                jsonObject.put("maxPlayers", serverInfo.getMaxPlayers());
                jsonObject.put("currentPlayers", 0);
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
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    }

}
