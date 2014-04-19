package com.rmb938.controller.jedis;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.controller.entity.RemoteController;
import com.rmb938.jedis.net.NetChannel;
import com.rmb938.jedis.net.NetCommandHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.UUID;

public class NetCommandHandlerSCTSC extends NetCommandHandler {

    private static final Logger logger = LogManager.getLogger(NetCommandHandlerSCTSC.class.getName());
    private final MN2ServerController serverController;

    public NetCommandHandlerSCTSC(MN2ServerController serverController) {
        NetCommandHandler.addHandler(NetChannel.SERVER_CONTROLLER_TO_SERVER_CONTROLLER, this);
        this.serverController = serverController;
    }

    @Override
    public void handle(JSONObject jsonObject) {
        try {
            String fromServerController = jsonObject.getString("from");
            String toServerController = jsonObject.getString("to");

            if (toServerController.equalsIgnoreCase("*") == false) {
                if (toServerController.equalsIgnoreCase(serverController.getMainConfig().privateIP) == false) {
                    return;
                }
            }

            String command = jsonObject.getString("command");
            HashMap<String, Object> objectHashMap = objectToHashMap(jsonObject.getJSONObject("data"));
            switch (command) {
                case "heartbeat":
                    UUID id = UUID.fromString((String) objectHashMap.get("id"));
                    int ram = (Integer) objectHashMap.get("ram");
                    RemoteController remoteController = RemoteController.getRemoteControllers().get(fromServerController);
                    if (remoteController == null) {
                        remoteController = new RemoteController(fromServerController, id, ram);
                        remoteController.setLastHeartbeat(System.currentTimeMillis());
                        RemoteController.getRemoteControllers().put(fromServerController,remoteController);
                        logger.info("Adding "+remoteController.getIP()+" to the cloud.");
                        logger.info("Main Controller: "+RemoteController.getMainController().getIP());
                    } else {
                        remoteController.setLastHeartbeat(System.currentTimeMillis());
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
