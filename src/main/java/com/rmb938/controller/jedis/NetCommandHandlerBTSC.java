package com.rmb938.controller.jedis;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.jedis.net.NetChannel;
import com.rmb938.jedis.net.NetCommandHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class NetCommandHandlerBTSC extends NetCommandHandler {

    private static final Logger logger = LogManager.getLogger(NetCommandHandlerSCTSC.class.getName());
    private final MN2ServerController serverController;

    public NetCommandHandlerBTSC(MN2ServerController serverController) {
        NetCommandHandler.addHandler(NetChannel.BUNGEE_TO_SERVER_CONTROLLER, this);
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
                    serverController.getBungee().setLastHeartBeat(System.currentTimeMillis());
                    break;
                default:
                    logger.info("Unknown SCTCS Command MN2ServerController " + command);
            }
        } catch (JSONException e) {
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
        }
    }
}
