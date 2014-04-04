package com.rmb938.controller.threads;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.controller.entity.RemoteController;
import com.rmb938.controller.entity.Server;
import com.rmb938.controller.entity.ServerInfo;

import java.util.ArrayList;

public class ServerManager implements Runnable {

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

            //Start up more servers if less then min needed
            if (RemoteController.getMainController() != null) {
                if (RemoteController.getMainController().getIP().equalsIgnoreCase(serverController.getControllerIP())) {
                    for (ServerInfo serverInfo : ServerInfo.getServerInfos().values()) {
                        int size = Server.getServers(serverInfo).size();
                        if (serverInfo.getMinServers() < size) {

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
