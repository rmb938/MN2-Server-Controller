package com.rmb938.controller.threads;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.controller.entity.RemoteController;
import com.rmb938.controller.entity.RemoteServer;
import com.rmb938.controller.entity.Server;
import com.rmb938.controller.entity.ServerInfo;
import com.rmb938.jedis.JedisManager;
import com.rmb938.jedis.net.command.servercontroller.NetCommandSCTB;
import com.rmb938.jedis.net.command.servercontroller.NetCommandSCTS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;

public class ServerManager implements Runnable {

    private static final Logger logger = LogManager.getLogger(ServerManager.class.getName());

    private final MN2ServerController serverController;

    public ServerManager(MN2ServerController serverController) {
        this.serverController = serverController;
    }

    public void run() {
        while (true) {
            //Remove timed out servers
            ArrayList<String> toRemove = new ArrayList<>();
            for (String serverName : Server.getServers().keySet()) {
                Server server = Server.getServers().get(serverName);
                if (server.getLastHeartbeat()+60000 < System.currentTimeMillis() && server.getLastHeartbeat() > 0) {
                    toRemove.add(serverName);
                }
            }
            for (String serverName : toRemove) {
                Server.getServers().remove(serverName);
            }

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
                            return;
                        }
                    } else {
                        JedisManager.returnJedis(jedis);
                        return;
                    }
                }
                int size = Integer.parseInt(jedis.get(serverInfo.getServerName()));
                if (size > 0) {
                    //TODO: check if can have more servers
                    //TODO: find port for new server
                    int port = -1;
                    Server server = new Server(serverController, serverInfo, port);
                    server.startServer();
                    int finalSize = size - 1;
                    if (finalSize == 0) {
                        jedis.set(serverInfo.getServerName(), finalSize + "");
                        jedis.expire(serverInfo.getServerName(), 30000);
                    } else {
                        jedis.set(serverInfo.getServerName(), finalSize + "");
                    }
                }
                jedis.del("lock."+serverInfo.getServerName());
                JedisManager.returnJedis(jedis);
            }

            if (RemoteController.getMainController() != null) {
                if (RemoteController.getMainController().getIP().equalsIgnoreCase(serverController.getControllerIP())) {
                    //Start up more servers if less then min needed
                    for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                        Jedis jedis = JedisManager.getJedis();
                        if (jedis.exists(serverInfo.getServerName())) {
                            int size = Integer.parseInt(jedis.get(serverInfo.getServerName()));
                            logger.warn("Waiting to create "+size+" servers of "+serverInfo.getServerName()+" if this continues please check server status.");
                            continue;
                        }
                        int size = Server.getServers(serverInfo).size();
                        if (serverInfo.getMinServers() > size) {
                            jedis.set(serverInfo.getServerName(), serverInfo.getMinServers() - size+"");
                        }
                        JedisManager.returnJedis(jedis);
                    }

                    //Add more servers if needed
                    for (ServerInfo serverInfo : Server.get75Full()) {
                        Jedis jedis = JedisManager.getJedis();
                        if (jedis.exists(serverInfo.getServerName())) {
                            int size = Integer.parseInt(jedis.get(serverInfo.getServerName()));
                            logger.warn("Waiting to create "+size+" servers of "+serverInfo.getServerName()+" if this continues please check server status.");
                            continue;
                        }
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
                                String IP = serverController.getControllerIP();
                                if (server instanceof RemoteServer) {
                                    IP = ((RemoteServer)server).getRemoteController().getIP();
                                }
                                NetCommandSCTS netCommandSCTS = new NetCommandSCTS("shutdown", serverController.getControllerIP(), IP+"."+serverInfo.getServerName()+"."+server.getPort());
                                netCommandSCTS.flush();
                                server.setLastHeartbeat(-2);

                                NetCommandSCTB netCommandSCTB = new NetCommandSCTB("removeServer", serverController.getControllerIP(), "*");
                                netCommandSCTB.addArg("serverInfo", IP+"."+serverInfo.getServerName()+"."+server.getPort());
                                netCommandSCTB.flush();
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
