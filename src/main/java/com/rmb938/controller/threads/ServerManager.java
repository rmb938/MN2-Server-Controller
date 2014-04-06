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

import java.util.ArrayList;
import java.util.UUID;

public class ServerManager implements Runnable {

    private static final Logger logger = LogManager.getLogger(ServerManager.class.getName());

    private final MN2ServerController serverController;

    public ServerManager(MN2ServerController serverController) {
        this.serverController = serverController;
    }

    public void run() {
        while (true) {
            if (serverController.getBungee().getLastHeartBeat() + 60000 < System.currentTimeMillis() && serverController.getBungee().getLastHeartBeat() > 0) {
                logger.info("Restarting Bungee Instance. Must of crashed.");
                serverController.getBungee().startBungee();
                serverController.getBungee().setLastHeartBeat(-1);
            }

            //Remove timed out servers
            logger.info("Checking Timed Out Servers");
            ArrayList<String> toRemove = new ArrayList<>();
            for (String serverUUID : Server.getServers().keySet()) {
                Server server = Server.getServers().get(serverUUID);
                if (server.getLastHeartbeat()+60000 < System.currentTimeMillis() && server.getLastHeartbeat() > 0) {
                    toRemove.add(serverUUID);
                }
            }
            for (String serverName : toRemove) {
                Server.getServers().remove(serverName);
            }

            logger.info("Checking if Servers need to be created");
            for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                Jedis jedis = JedisManager.getJedis();
                if (jedis.exists(serverInfo.getServerName()) == false) {
                    continue;
                }
                if (jedis.setnx("lock."+serverInfo.getServerName(), System.currentTimeMillis()+30000+"") == 0) {
                    long time = Long.parseLong(jedis.get("lock." + serverInfo.getServerName()));
                    if (System.currentTimeMillis() > time) {
                        time = Long.parseLong(jedis.getSet("lock."+serverInfo.getServerName(), System.currentTimeMillis()+30000+""));
                        if (System.currentTimeMillis() < time) {
                            JedisManager.returnJedis(jedis);
                            continue;
                        }
                    } else {
                        JedisManager.returnJedis(jedis);
                        continue;
                    }
                }
                int size = Integer.parseInt(jedis.get(serverInfo.getServerName()));
                if (size > 0) {
                    int canMake = serverController.getMainConfig().serverAmount - Server.getLocalServers().size();
                    if (canMake > 0) {
                        int needMake = canMake >= size ? size : size - canMake;
                        logger.info("Making "+needMake+" "+serverInfo.getServerName());
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
                            jedis.set(serverInfo.getServerName(), size + "");
                            jedis.expire(serverInfo.getServerName(), 30000);
                        } else {
                            jedis.set(serverInfo.getServerName(), size + "");
                        }
                    }
                }
                jedis.del("lock."+serverInfo.getServerName());
                JedisManager.returnJedis(jedis);
            }

            logger.info("Checking Main Controller");
            if (RemoteController.getMainController() != null) {
                if (RemoteController.getMainController().getControllerID().compareTo(serverController.getControllerId()) == 0) {
                    logger.info("Main Controller Management");
                    //Start up more servers if less then min needed
                    for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                        Jedis jedis = JedisManager.getJedis();
                        if (jedis.exists(serverInfo.getServerName())) {
                            int size = Integer.parseInt(jedis.get(serverInfo.getServerName()));
                            if (size != 0) {
                                logger.warn("Waiting to create " + size + " servers of " + serverInfo.getServerName() + " if this continues please check server status.");
                            }
                            continue;
                        }
                        int size = Server.getServers(serverInfo).size();
                        logger.info("Currently "+size+" of "+serverInfo.getServerName());
                        if (serverInfo.getMinServers() > size) {
                            int need = serverInfo.getMinServers() - size;
                            logger.info(need+" of "+serverInfo.getServerName());
                            jedis.set(serverInfo.getServerName(), need+"");
                        }
                        JedisManager.returnJedis(jedis);
                    }

                    //Add more servers if needed
                    for (ServerInfo serverInfo : Server.get75Full()) {
                        Jedis jedis = JedisManager.getJedis();
                        if (jedis.exists(serverInfo.getServerName())) {
                            int size = Integer.parseInt(jedis.get(serverInfo.getServerName()));
                            if (size != 0) {
                                logger.warn("Waiting to create " + size + " servers of " + serverInfo.getServerName() + " if this continues please check server status.");
                            }
                            continue;
                        }
                        logger.info("Load Balance more "+serverInfo.getServerName());
                        jedis.set(serverInfo.getServerName(), "3");
                        JedisManager.returnJedis(jedis);
                    }

                    //Remove Empty Servers
                    for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                        ArrayList<Server> servers = Server.getServers(serverInfo);
                        if (servers.size() < serverInfo.getMinServers()) {
                            continue;
                        }
                        for (Server server : servers) {
                            if (server.getBeatsEmpty() >= 24 && server.getLastHeartbeat() > 0) {
                                logger.info("Load Balance remove "+serverInfo.getServerName());
                                NetCommandSCTS netCommandSCTS = new NetCommandSCTS("shutdown", serverController.getMainConfig().privateIP, server.getServerUUID());
                                netCommandSCTS.flush();
                                server.setLastHeartbeat(-2);
                            }
                        }
                    }
                }
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void startServerManager() {
        Thread serverManager = new Thread(this);
        serverManager.start();
    }

}
