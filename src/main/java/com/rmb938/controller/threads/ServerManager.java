package com.rmb938.controller.threads;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.controller.entity.RemoteController;
import com.rmb938.controller.entity.Server;
import com.rmb938.controller.entity.ServerInfo;
import com.rmb938.jedis.JedisManager;
import com.rmb938.jedis.net.command.servercontroller.NetCommandSCTB;
import com.rmb938.jedis.net.command.servercontroller.NetCommandSCTS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class ServerManager implements Runnable {

    private static final Logger logger = LogManager.getLogger(ServerManager.class.getName());

    private final MN2ServerController serverController;

    public ServerManager(MN2ServerController serverController) {
        this.serverController = serverController;
    }

    public void run() {
        while (true) {
            Jedis jedis = null;
            try {
                jedis = JedisManager.getJedis();//connect first to make sure redis is alive. If not ignore
                if (serverController.getBungee().getLastHeartBeat() + 60000 < System.currentTimeMillis() && serverController.getBungee().getLastHeartBeat() > 0) {
                    logger.info("Restarting Bungee Instance. Must of crashed.");
                    serverController.getBungee().startBungee();
                    serverController.getBungee().setLastHeartBeat(-1);
                }

                //Remove timed out servers
                ArrayList<Server> toRemove = new ArrayList<>();
                for (String serverUUID : Server.getServers().keySet()) {
                    Server server = Server.getServers().get(serverUUID);
                    if (server.getLastHeartbeat() + 60000 < System.currentTimeMillis() && server.getLastHeartbeat() > 0) {
                        toRemove.add(server);
                    }
                }
                for (Server server : toRemove) {
                    NetCommandSCTB netCommandHandlerSCTB = new NetCommandSCTB("removeServer", serverController.getMainConfig().privateIP, serverController.getMainConfig().privateIP);
                    netCommandHandlerSCTB.addArg("serverUUID", server.getServerUUID());
                    netCommandHandlerSCTB.flush();
                    while (jedis.setnx("lock." + server.getServerInfo().getServerName() + ".key", System.currentTimeMillis() + 30000 + "") == 0) {
                        String lock = jedis.get("lock." + server.getServerInfo().getServerName() + ".key");
                        long time = Long.parseLong(lock != null ? lock : "0");
                        if (System.currentTimeMillis() > time) {
                            time = Long.parseLong(jedis.getSet("lock." + server.getServerInfo().getServerName() + ".key", System.currentTimeMillis() + 30000 + ""));
                            if (System.currentTimeMillis() < time) {
                                continue;
                            }
                        } else {
                            continue;
                        }
                        break;
                    }
                    Set<String> keys = jedis.keys("server." + server.getServerInfo().getServerName() + ".*");
                    String keyToDel = null;
                    for (String key : keys) {
                        String uuid = jedis.get(key);
                        if (uuid.equals(server.getServerUUID())) {
                            keyToDel = key;
                            break;
                        }
                    }
                    if (keyToDel != null) {
                        jedis.del(keyToDel);
                    }
                    jedis.del("lock." + server.getServerInfo().getServerName() + ".key");
                    Server.getServers().remove(server.getServerUUID());
                }

                for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                    if (jedis.exists(serverInfo.getServerName()) == false) {
                        continue;
                    }
                    if (jedis.setnx("lock." + serverInfo.getServerName(), System.currentTimeMillis() + 30000 + "") == 0) {
                        String lock = jedis.get("lock." + serverInfo.getServerName());
                        long time = Long.parseLong(lock != null ? lock : "0");
                        if (System.currentTimeMillis() > time) {
                            time = Long.parseLong(jedis.getSet("lock." + serverInfo.getServerName(), System.currentTimeMillis() + 30000 + ""));
                            if (System.currentTimeMillis() < time) {
                                continue;
                            }
                        } else {
                            continue;
                        }
                    }
                    int size = Integer.parseInt(jedis.get(serverInfo.getServerName()));
                    if (size > 0) {
                        int canMake = serverController.getMainConfig().controller_serverAmount - Server.getLocalServersNonClose().size();
                        if (canMake > 0) {
                            int needMake = canMake >= size ? size : size - canMake;
                            logger.info("Making " + needMake + " " + serverInfo.getServerName());
                            int failedMake = 0;
                            for (int i = 0; i < needMake; i++) {
                                int port = 25566;
                                while (Server.getLocalServer(port) != null) {
                                    port += 1;
                                }
                                Server server = new Server(serverController, serverInfo, UUID.randomUUID().toString(), port);
                                boolean success = server.startServer();
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
                    }
                    jedis.del("lock." + serverInfo.getServerName());
                }

                if (RemoteController.getMainController() != null) {
                    if (RemoteController.getMainController().getControllerID().compareTo(serverController.getControllerId()) == 0) {
                        //Start up more servers if less then min needed
                        for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                            int size = Server.getServers(serverInfo).size();
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
                                        time = Long.parseLong(jedis.getSet("lock." + serverInfo.getServerName(), System.currentTimeMillis() + 30000 + ""));
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
                                        time = Long.parseLong(jedis.getSet("lock." + serverInfo.getServerName(), System.currentTimeMillis() + 30000 + ""));
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
                                ArrayList<Server> servers = Server.getServers(serverInfo);
                                if (servers.size() <= serverInfo.getMinServers()) {
                                    continue;
                                }
                                for (Server server : servers) {
                                    if (server.getBeatsEmpty() >= 24 && server.getLastHeartbeat() > 0) {
                                        logger.info("Load Balance remove " + serverInfo.getServerName());
                                        NetCommandSCTS netCommandSCTS = new NetCommandSCTS("shutdown", serverController.getMainConfig().privateIP, server.getServerUUID());
                                        netCommandSCTS.flush();
                                        server.setLastHeartbeat(-2);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (e instanceof JedisConnectionException) {
                    logger.error("Unable to contact Redis in server manager loop.");
                    serverController.getBungee().setLastHeartBeat(System.currentTimeMillis());
                    for (Server server : Server.getServers().values()) {
                        if (server.getLastHeartbeat() > 0) {
                            server.setLastHeartbeat(System.currentTimeMillis());
                        }
                    }
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
                logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
            }
        }
    }

    public void startServerManager() {
        Thread serverManager = new Thread(this);
        serverManager.start();
    }

}
