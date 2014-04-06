package com.rmb938.controller.entity;

import com.rmb938.controller.MN2ServerController;

public class ClosingServer extends Server {

    public ClosingServer(MN2ServerController serverController, ServerInfo serverInfo, String serverUUID, int port) {
        super(serverController, serverInfo, serverUUID, port);
    }
}
