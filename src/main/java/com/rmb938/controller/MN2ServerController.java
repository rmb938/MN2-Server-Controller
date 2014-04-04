package com.rmb938.controller;


import com.rmb938.controller.jedis.NetCommandHandlerSCTSC;
import com.rmb938.controller.threads.ConsoleInput;
import com.rmb938.controller.threads.MainControllerFinder;
import com.rmb938.jedis.JedisManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


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

    public MN2ServerController(String controllerIP) {
        this.controllerIP = controllerIP;

        //TODO: connect to mysql and read servers

        logger.info("Sleeping for 20 seconds to reconnect to network");
        try {
            Thread.sleep(20000);//sleep for 20 seconds waiting for servers and controllers to reconnect
        } catch (InterruptedException e) {
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
        }

        new ConsoleInput();
        new MainControllerFinder();
    }

    public String getControllerIP() {
        return controllerIP;
    }

}
