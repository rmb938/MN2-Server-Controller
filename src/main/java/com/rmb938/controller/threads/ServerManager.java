package com.rmb938.controller.threads;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.controller.entity.RemoteController;
import com.rmb938.controller.entity.Server;
import com.rmb938.controller.entity.ServerInfo;
import com.rmb938.jedis.JedisManager;
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
                //TODO: check if can have more servers
                //TODO: find port for new server
                int port = -1;
                Server server = new Server(serverController, serverInfo, port);
                server.startServer();
                int finalSize = size -1;
                if (finalSize == 0) {
                    jedis.del(serverInfo.getServerName());
                } else {
                    jedis.set(serverInfo.getServerName(), finalSize+"");
                }
                jedis.del("lock."+serverInfo.getServerName());
                JedisManager.returnJedis(jedis);
            }

            //Start up more servers if less then min needed
            if (RemoteController.getMainController() != null) {
                if (RemoteController.getMainController().getIP().equalsIgnoreCase(serverController.getControllerIP())) {
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
