package com.rmb938.controller;


import com.rmb938.controller.entity.Bungee;
import com.rmb938.controller.jedis.NetCommandHandlerSCTSC;
import com.rmb938.controller.threads.ConsoleInput;
import com.rmb938.controller.threads.ServerManager;
import com.rmb938.jedis.JedisManager;
import com.rmb938.jedis.net.command.servercontroller.NetCommandSCTSC;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;


public class MN2ServerController {

    private static final Logger logger = LogManager.getLogger(MN2ServerController.class.getName());
    private static MN2ServerController serverController;

    public static MN2ServerController getServerController() {
        return serverController;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            logger.info("Missing internal IP argument.");
            logger.info("Usage: java -jar MN2ServerController.jar [internalIP] [redisServerIP]");
            logger.info("Example: java -jar MN2ServerController.jar 192.168.1.2 10.0.0.1");
            return;
        }
        logger.info("Starting Server Controller");

        JedisManager.connectToRedis("");
        JedisManager.setUpDelegates();

        serverController = new MN2ServerController(args[0]);

        new NetCommandHandlerSCTSC(serverController);
    }

    private final String controllerIP;
    private final UUID controllerId;

    public MN2ServerController(final String controllerIP) {
        this.controllerIP = controllerIP;
        this.controllerId = UUID.randomUUID();

        //TODO: connect to mysql and read servers

        logger.info("Starting Heartbeat");
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    NetCommandSCTSC netCommandSCTSC = new NetCommandSCTSC("heartbeat", controllerIP, "*");
                    netCommandSCTSC.addArg("id", controllerId);
                    netCommandSCTSC.flush();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();

        logger.info("Sleeping for 20 seconds to reconnect to network");
        try {
            Thread.sleep(20000);//sleep for 20 seconds waiting for servers and controllers to reconnect
        } catch (InterruptedException e) {
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
        }

        new ConsoleInput();
        new ServerManager(this);


        Bungee bungee = new Bungee();
        bungee.startBungee();
    }

    public UUID getControllerId() {
        return controllerId;
    }

    public String getControllerIP() {
        return controllerIP;
    }

}
