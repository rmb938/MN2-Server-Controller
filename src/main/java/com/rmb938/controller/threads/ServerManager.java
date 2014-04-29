package com.rmb938.controller.threads;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.controller.entity.RemoteController;
import com.rmb938.controller.entity.Server;
import com.rmb938.controller.entity.ServerInfo;
import com.rmb938.jedis.JedisManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

public class ServerManager implements Runnable {

    private static final Logger logger = LogManager.getLogger(ServerManager.class.getName());

    private final MN2ServerController serverController;

    public ServerManager(MN2ServerController serverController) {
        this.serverController = serverController;
    }

    private RemoteController findLowestRemote(ServerInfo serverInfo) {
        ArrayList<RemoteController> controllers = new ArrayList<>();
        for (RemoteController remoteController : RemoteController.getRemoteControllers().values()) {
            if (remoteController.getLastHeartbeat() + 60000 < System.currentTimeMillis()) {
                continue;
            }
            int freeRam = remoteController.getRam() - remoteController.getUsedRam();
            if (freeRam >= serverInfo.getMemory()) {
                controllers.add(remoteController);
            }
        }
        if (controllers.isEmpty()) {
            return null;
        }
        Collections.sort(controllers, new Comparator<RemoteController>() {
            @Override
            public int compare(RemoteController o1, RemoteController o2) {
                if (o1.getUsedRam() < o2.getUsedRam()) {
                    return -1;
                }
                if (o1.getUsedRam() > o2.getUsedRam()) {
                    return 1;
                }
                return 0;
            }
        });
        return controllers.get(0);
    }

    public void run() {
        while (Thread.interrupted() == false) {
            Jedis jedis = null;
            try {
                jedis = JedisManager.getJedis();
                if (jedis.exists(serverController.getMainConfig().publicIP+":bungee") == false) {
                    logger.info("Restarting Bungee Instance. Must of crashed.");
                    serverController.getBungee().startBungee();
                }
                logger.info("Checking Make");
                for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                    int usedRam = 0;
                    usedRam += serverInfo.getMemory() * Server.getLocalServers(serverController).size();
                    int freeRam = serverController.getMainConfig().controller_serverRam - usedRam;
                    int canMake = 0;
                    if (freeRam != 0) {
                        canMake = freeRam / serverInfo.getMemory();
                    }
                    if (canMake == 0) {
                        continue;
                    }
                    if (jedis.exists(serverInfo.getServerName()) == false) {
                        continue;
                    }
                    JSONObject jsonObject = new JSONObject(jedis.get(serverInfo.getServerName()));
                    String toIP = jsonObject.getString("to");
                    if (toIP.equalsIgnoreCase(serverController.getMainConfig().privateIP) == false) {
                        continue;
                    }
                    if (jedis.setnx("lock." + serverInfo.getServerName(), System.currentTimeMillis() + 30000 + "") == 0) {
                        String lock = jedis.get("lock." + serverInfo.getServerName());
                        long time = Long.parseLong(lock != null ? lock : "0");
                        if (System.currentTimeMillis() > time) {
                            try {
                                time = Long.parseLong(jedis.getSet("lock." + serverInfo.getServerName(), System.currentTimeMillis() + 30000 + ""));
                            } catch (Exception ex) {
                                time = 0;
                            }
                            if (System.currentTimeMillis() < time) {
                                continue;
                            }
                        } else {
                            continue;
                        }
                    }
                    String data = jedis.get(serverInfo.getServerName());
                    if (data != null) {
                        jsonObject = new JSONObject(data);
                        toIP = jsonObject.getString("to");
                        if (toIP.equalsIgnoreCase(serverController.getMainConfig().privateIP) == true) {
                            int size = jsonObject.getInt("need");
                            if (size > 0) {
                                int needMake = canMake >= size ? size : canMake;
                                logger.info("Making " + needMake + " of " + size + " "  + serverInfo.getServerName());
                                int made = 0;
                                for (int i = 0; i < needMake; i++) {
                                    int port = 25566;
                                    while (true) {
                                        if (Server.getLocalServer(port, serverController) == true) {
                                            port += 1;
                                            continue;
                                        }
                                        break;
                                    }
                                    boolean success = Server.startServer(serverInfo, serverController, port);
                                    if (success == false) {
                                        logger.info("Failed to make " + serverInfo.getServerName());
                                    } else {
                                        made += 1;
                                    }
                                    RemoteController remoteController = findLowestRemote(serverInfo);
                                    if (size - made != 0) {
                                        if (remoteController != null && remoteController.getIP().equalsIgnoreCase(serverController.getMainConfig().privateIP) == false) {
                                            logger.info("Controller " + remoteController.getIP() + " has less load.");
                                            break;
                                        }
                                    }
                                }
                                size -= made;
                                jsonObject.put("need", size);
                                if (size == 0) {
                                    jedis.setex(serverInfo.getServerName(), 30, jsonObject.toString());
                                } else {
                                    RemoteController remoteController = findLowestRemote(serverInfo);
                                    if (remoteController == null) {
                                        logger.error("Unable to make more " + serverInfo.getServerName() + " network capacity reached!");
                                        jedis.setex(serverInfo.getServerName(), 30, jsonObject.toString());
                                    } else {
                                        logger.info("Sending Extra to "+remoteController.getIP());
                                        jsonObject.put("to", remoteController.getIP());
                                        jedis.setex(serverInfo.getServerName(), 60, jsonObject.toString());
                                    }
                                }
                            }
                        }
                    }
                    jedis.del("lock." + serverInfo.getServerName());
                }

                if (RemoteController.getMainController() != null) {
                    if (RemoteController.getMainController().getControllerID().compareTo(serverController.getControllerId()) == 0) {
                        //Start up more servers if less then min needed
                        for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                            int max = serverInfo.getMinServers();
                            Set<String> keys = jedis.keys("server." + serverInfo.getServerName() + ".*");
                            int size = keys.size();
                            logger.info("Currently " + size + " of " + serverInfo.getServerName());
                            if (jedis.exists(serverInfo.getServerName())) {
                                JSONObject jsonObject = new JSONObject(jedis.get(serverInfo.getServerName()));
                                int jedisSize = jsonObject.getInt("need");
                                if (jedisSize != 0) {
                                    logger.warn("Waiting to create " + jedisSize + " servers of " + serverInfo.getServerName() + " if this continues please check server status.");
                                }
                                continue;
                            }
                            int foundLower = 0;
                            for (String key : keys) {
                                int id = Integer.parseInt(key.split("\\.")[2]);
                                if (id <= max) {
                                    foundLower += 1;
                                }
                            }
                            if (serverInfo.getMinServers() > foundLower) {
                                int need = serverInfo.getMinServers() - foundLower;
                                if (jedis.setnx("lock." + serverInfo.getServerName(), System.currentTimeMillis() + 30000 + "") == 0) {
                                    String lock = jedis.get("lock." + serverInfo.getServerName());
                                    long time = Long.parseLong(lock != null ? lock : "0");
                                    if (System.currentTimeMillis() > time) {
                                        try {
                                            time = Long.parseLong(jedis.getSet("lock." + serverInfo.getServerName(), System.currentTimeMillis() + 30000 + ""));
                                        } catch (Exception ex) {
                                            time = 0;
                                        }
                                        if (System.currentTimeMillis() < time) {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                }
                                RemoteController remoteController = findLowestRemote(serverInfo);
                                if (remoteController == null) {
                                    logger.error("Unable to make more "+serverInfo.getServerName()+" network capacity reached!");
                                } else {
                                    JSONObject jsonObject = new JSONObject();
                                    jsonObject.put("need", need);
                                    jsonObject.put("to", remoteController.getIP());
                                    jedis.setex(serverInfo.getServerName(), 60, jsonObject.toString());
                                }
                                jedis.del("lock." + serverInfo.getServerName());
                            }
                        }

                        //Add more servers if needed
                        if (serverController.getMainConfig().controller_loadBalance) {
                            for (ServerInfo serverInfo : Server.get50Full()) {
                                logger.info("Checking Load Balance " + serverInfo.getServerName());
                                if (jedis.exists(serverInfo.getServerName())) {
                                    JSONObject jsonObject = new JSONObject(jedis.get(serverInfo.getServerName()));
                                    int size = jsonObject.getInt("need");
                                    if (size != 0) {
                                        logger.warn("Waiting to create " + size + " servers of " + serverInfo.getServerName() + " if this continues please check server status.");
                                    }
                                    continue;
                                }
                                logger.info("Load Balance more " + serverInfo.getServerName());
                                if (jedis.setnx("lock." + serverInfo.getServerName(), System.currentTimeMillis() + 30000 + "") == 0) {
                                    String lock = jedis.get("lock." + serverInfo.getServerName());
                                    long time = Long.parseLong(lock != null ? lock : "0");
                                    if (System.currentTimeMillis() > time) {
                                        try {
                                            time = Long.parseLong(jedis.getSet("lock." + serverInfo.getServerName(), System.currentTimeMillis() + 30000 + ""));
                                        } catch (Exception ex) {
                                            time = 0;
                                        }
                                        if (System.currentTimeMillis() < time) {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                }
                                RemoteController remoteController = findLowestRemote(serverInfo);
                                if (remoteController == null) {
                                    logger.error("Unable to make more "+serverInfo.getServerName()+" network capacity reached!");
                                } else {
                                    JSONObject jsonObject = new JSONObject();
                                    jsonObject.put("need", 1);
                                    jsonObject.put("to", remoteController.getIP());
                                    jedis.setex(serverInfo.getServerName(), 60, jsonObject.toString());
                                }
                                jedis.del("lock." + serverInfo.getServerName());
                            }

                            //Remove Empty Servers
                            /*for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                                ArrayList<String> servers = Server.getServerKeys(serverInfo);
                                int size = servers.size();
                                if (size <= serverInfo.getMinServers()) {
                                    continue;
                                }
                                int percent = (Server.getOnlinePlayers(serverInfo)/Server.getMaxPlayers(serverInfo)) * 100;
                                if (percent < 70) {
                                    String uuid = null;
                                    while (uuid == null) {
                                        String server = servers.get(servers.size()-1);
                                        String data = jedis.get(server);
                                        if (data == null) {
                                            servers.remove(server);
                                            continue;
                                        }
                                        JSONObject jsonObject = new JSONObject(data);
                                        uuid = jsonObject.getString("uuid");
                                    }
                                    NetCommandSCTS netCommandHandlerSCTS = new NetCommandSCTS("shutdown", serverController.getMainConfig().privateIP, uuid);
                                    netCommandHandlerSCTS.flush();
                                }
                            }*/
                        }
                    }
                }
            } catch (Exception e) {
                if (e instanceof JedisConnectionException) {
                    logger.error("Unable to contact Redis in server manager loop.");
                } else {
                    e.printStackTrace();
                }
            } finally {
                if (jedis != null) {
                    JedisManager.returnJedis(jedis);
                }
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

}
