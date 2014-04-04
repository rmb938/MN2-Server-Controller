package com.rmb938.controller.jedis;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.controller.entity.RemoteController;
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

            RemoteController remoteController = RemoteController.getRemoteControllers().get(toServerController);
            if (remoteController == null) {
                remoteController = new RemoteController(toServerController);
                RemoteController.getRemoteControllers().put(toServerController, remoteController);
            }

            String command = jsonObject.getString("command");
            HashMap<String, Object> objectHashMap = objectToHashMap(jsonObject.getJSONObject("data"));
            switch (command) {
                case "heartbeat":
                    String serverName = (String) objectHashMap.get("serverName");
                    int currentPlayers = (Integer) objectHashMap.get("currentPlayers");
                    Server server = null;
                    if (toServerController.equalsIgnoreCase(serverController.getControllerIP())) {
                        server = Server.getServers().get(toServerController + "." + fromServer);
                        if (server == null) {
                            server = new Server(serverController, ServerInfo.getServerInfos().get(serverName), fromServer);
                            Server.getServers().put(toServerController + "." + fromServer, server);
                        }
                    } else {
                        server = Server.getServers().get(toServerController + "." + fromServer);
                        if (server == null) {
                            server = new RemoteServer(serverController, remoteController, fromServer, serverName);
                            Server.getServers().put(toServerController + "." + fromServer, server);
                        }
                    }
                    if (server.getLastHeartbeat() == -2) {
                        return;
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
                    Server.getServers().remove(toServerController + "." + fromServer);
                    break;
                default:
                    logger.info("Unknown STCS Command MN2ServerController " + command);
            }
        } catch (JSONException e) {
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
        }
    }
}
