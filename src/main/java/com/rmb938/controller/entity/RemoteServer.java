package com.rmb938.controller.entity;

import com.rmb938.controller.MN2ServerController;

public class RemoteServer extends Server {

    public RemoteServer(MN2ServerController serverController, int port, String name, String serverUUID) {
        super(serverController, ServerInfo.getServerInfos().get(name), serverUUID, port);
    }
}
