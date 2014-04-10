package com.rmb938.controller.jedis;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.controller.entity.ClosingServer;
import com.rmb938.controller.entity.RemoteServer;
import com.rmb938.controller.entity.Server;
import com.rmb938.controller.entity.ServerInfo;
import com.rmb938.jedis.net.NetChannel;
import com.rmb938.jedis.net.NetCommandHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class NetCommandHandlerSTSC extends NetCommandHandler {

    private static final Logger logger = LogManager.getLogger(NetCommandHandlerSCTSC.class.getName());
    private final MN2ServerController serverController;

    public NetCommandHandlerSTSC(MN2ServerController serverController) {
        NetCommandHandler.addHandler(NetChannel.SERVER_TO_SERVER_CONTROLLER, this);
        this.serverController = serverController;
    }

    @Override
    public void handle(JSONObject jsonObject) {
        try {
            int fromServer = jsonObject.getInt("from");
            String toServerController = jsonObject.getString("to");

            String command = jsonObject.getString("command");
            HashMap<String, Object> objectHashMap = objectToHashMap(jsonObject.getJSONObject("data"));
            switch (command) {
                case "heartbeat":
                    String serverName = (String) objectHashMap.get("serverName");
                    String serverUUID = (String) objectHashMap.get("serverUUID");
                    int currentPlayers = (Integer) objectHashMap.get("currentPlayers");
                    Server server;
                    if (toServerController.equalsIgnoreCase(serverController.getMainConfig().privateIP)) {
                        server = Server.getServers().get(serverUUID);
                        if (server == null) {
                            server = new Server(serverController, ServerInfo.getServerInfos().get(serverName), serverUUID, fromServer);
                            Server.getServers().put(serverUUID, server);
                        }
                    } else {
                        server = Server.getServers().get(serverUUID);
                        if (server == null) {
                            server = new RemoteServer(serverController, fromServer, serverName, serverUUID);
                            Server.getServers().put(serverUUID, server);
                        }
                    }

                    server.setCurrentPlayers(currentPlayers);
                    server.setLastHeartbeat(System.currentTimeMillis());

                    if (server.getCurrentPlayers() == 0) {
                        server.setBeatsEmpty(server.getBeatsEmpty() + 1);
                    } else {
                        server.setBeatsEmpty(0);
                    }
                    break;
                case "removeServer":
                    serverUUID = (String) objectHashMap.get("serverUUID");
                    server = Server.getServers().get(serverUUID);
                    logger.info("Removing server " + server.getPort());
                    if (server != null) {
                        if (server instanceof RemoteServer) {
                            Server.getServers().remove(serverUUID);
                        } else {
                            ClosingServer closingServer = new ClosingServer(serverController, server.getServerInfo(), serverUUID, server.getPort());
                            closingServer.setLastHeartbeat(System.currentTimeMillis());
                            Server.getServers().put(serverUUID, closingServer);
                        }
                    }
                    break;
                default:
                    break;
            }
        } catch (JSONException e) {
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
        }
    }
}
