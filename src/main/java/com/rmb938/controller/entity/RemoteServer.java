package com.rmb938.controller.entity;

import com.rmb938.controller.MN2ServerController;

import java.util.ArrayList;

public class RemoteServer extends Server {

    public ArrayList<RemoteServer> getServers(RemoteController remoteController) {
        ArrayList<RemoteServer> servers = new ArrayList<>();
        for (Server server : Server.getServers().values()) {
            if (server instanceof RemoteServer) {
                RemoteServer remoteServer = (RemoteServer) server;
                servers.add(remoteServer);
            }
        }
        return servers;
    }

    private final RemoteController remoteController;

    public RemoteServer(MN2ServerController serverController, RemoteController remoteController, int port, String name, String serverUUID) {
        super(serverController, ServerInfo.getServerInfos().get(name), serverUUID, port);
        this.remoteController = remoteController;
    }

    public RemoteController getRemoteController() {
        return remoteController;
    }
}
