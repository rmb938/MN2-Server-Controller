package com.rmb938.controller.threads;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.controller.entity.RemoteController;
import com.rmb938.controller.entity.Server;
import com.rmb938.controller.entity.ServerInfo;
import com.rmb938.jedis.JedisManager;
import com.rmb938.jedis.net.command.servercontroller.NetCommandSCTS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.ArrayList;

public class ServerManager implements Runnable {

    private static final Logger logger = LogManager.getLogger(ServerManager.class.getName());

    private final MN2ServerController serverController;

    public ServerManager(MN2ServerController serverController) {
        this.serverController = serverController;
    }

    public void run() {
        while (Thread.interrupted() == false) {
            Jedis jedis = null;
            try {
                jedis = JedisManager.getJedis();
                if (serverController.getBungee().getLastHeartBeat() + 60000 < System.currentTimeMillis() && serverController.getBungee().getLastHeartBeat() > 0) {
                    logger.info("Restarting Bungee Instance. Must of crashed.");
                    serverController.getBungee().startBungee();
                    serverController.getBungee().setLastHeartBeat(-1);
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
                    int size = Integer.parseInt(jedis.get(serverInfo.getServerName()));
                    if (size > 0) {
                        int needMake = canMake >= size ? size : size - canMake;
                        logger.info("Making " + needMake + " " + serverInfo.getServerName());
                        int failedMake = 0;
                        for (int i = 0; i < needMake; i++) {
                            int port = 25566;
                            while (Server.getLocalServer(port, serverController) == true) {
                                port += 1;
                            }
                            boolean success = Server.startServer(serverInfo, serverController, port);
                            if (success == false) {
                                logger.info("Failed to make " + serverInfo.getServerName());
                                failedMake += 1;
                            }
                        }
                        size -= needMake;
                        size += failedMake;
                        if (size == 0) {
                            jedis.setex(serverInfo.getServerName(), 30, size + "");
                        } else {
                            jedis.set(serverInfo.getServerName(), size + "");
                        }
                    }
                    jedis.del("lock." + serverInfo.getServerName());
                }

                if (RemoteController.getMainController() != null) {
                    if (RemoteController.getMainController().getControllerID().compareTo(serverController.getControllerId()) == 0) {
                        //Start up more servers if less then min needed
                        for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                            int size = jedis.keys("server." + serverInfo.getServerName() + ".*").size();
                            logger.info("Currently " + size + " of " + serverInfo.getServerName());
                            if (jedis.exists(serverInfo.getServerName())) {
                                int jedisSize = Integer.parseInt(jedis.get(serverInfo.getServerName()));
                                if (jedisSize != 0) {
                                    logger.warn("Waiting to create " + jedisSize + " servers of " + serverInfo.getServerName() + " if this continues please check server status.");
                                }
                                continue;
                            }
                            if (serverInfo.getMinServers() > size) {
                                int need = serverInfo.getMinServers() - size;

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

                                jedis.set(serverInfo.getServerName(), need + "");
                                jedis.del("lock." + serverInfo.getServerName());
                            }
                        }

                        //Add more servers if needed
                        if (serverController.getMainConfig().controller_loadBalance) {
                            for (ServerInfo serverInfo : Server.get75Full()) {
                                logger.info("Checking Load Balance " + serverInfo.getServerName());
                                if (jedis.exists(serverInfo.getServerName())) {
                                    int size = Integer.parseInt(jedis.get(serverInfo.getServerName()));
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
                                jedis.set(serverInfo.getServerName(), "3");
                                jedis.del("lock." + serverInfo.getServerName());
                            }

                            //Remove Empty Servers
                            for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                                ArrayList<String> servers = Server.getServers(serverInfo);
                                int size = servers.size();
                                if (size <= serverInfo.getMinServers()) {
                                    continue;
                                }
                                ArrayList<String> localEmpty = Server.getLocalEmpty(serverController, 120);

                                int maxRemove = size - serverInfo.getMinServers();
                                int toRemove;
                                if (localEmpty.size() > maxRemove) {
                                    toRemove = localEmpty.size() - maxRemove;
                                } else {
                                    toRemove = maxRemove - localEmpty.size();
                                }
                                for (int i = 0; i < toRemove; i++) {
                                    logger.info("Load Balance remove " + serverInfo.getServerName());
                                    NetCommandSCTS netCommandSCTS = new NetCommandSCTS("shutdown", serverController.getMainConfig().privateIP, localEmpty.get(i));
                                    netCommandSCTS.flush();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (e instanceof JedisConnectionException) {
                    logger.error("Unable to contact Redis in server manager loop.");
                } else {
                    logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
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
