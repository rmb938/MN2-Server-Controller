package com.rmb938.controller;


import com.rmb938.controller.config.MainConfig;
import com.rmb938.controller.database.DatabaseServerInfo;
import com.rmb938.controller.entity.Bungee;
import com.rmb938.controller.jedis.NetCommandHandlerSCTSC;
import com.rmb938.controller.threads.ConsoleInput;
import com.rmb938.controller.threads.ServerManager;
import com.rmb938.database.DatabaseAPI;
import com.rmb938.jedis.JedisManager;
import com.rmb938.jedis.net.command.servercontroller.NetCommandSCTSC;
import net.cubespace.Yamler.Config.InvalidConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;


public class MN2ServerController {

    private static final Logger logger = LogManager.getLogger(MN2ServerController.class.getName());

    public static void main(String[] args) {
        logger.info("Starting Server Controller");
        new MN2ServerController();

    }

    private final UUID controllerId;
    private final MainConfig mainConfig;

    public MN2ServerController() {
        this.controllerId = UUID.randomUUID();

        mainConfig = new MainConfig();
        try {
            mainConfig.init();
        } catch (InvalidConfigurationException e) {
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
            return;
        }

        logger.info("Connecting to Redis");
        JedisManager.connectToRedis(mainConfig.redis_address);
        JedisManager.setUpDelegates();
        new NetCommandHandlerSCTSC(this);

        logger.info("Connecting to MySQL");
        DatabaseAPI.initializeMySQL(mainConfig.mySQL_userName, mainConfig.mySQL_password, mainConfig.mySQL_database, mainConfig.mySQL_address, mainConfig.mySQL_port);
        logger.info("Loading Server Info");
        DatabaseServerInfo databaseServerInfo = new DatabaseServerInfo(this);
        databaseServerInfo.loadPlugins();
        databaseServerInfo.loadServerInfo();
        Bungee bungee = databaseServerInfo.loadBungeeInfo();

        logger.info("Starting Heartbeat");
        heartbeat();

        logger.info("Sleeping for 20 seconds to reconnect to network");
        try {
            Thread.sleep(20000);//sleep for 20 seconds waiting for servers and controllers to reconnect
        } catch (InterruptedException e) {
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
        }

        logger.info("Starting Bungee Instance");
        bungee.startBungee();

        logger.info("Starting Console Input");
        new ConsoleInput();

        logger.info("Starting Server Manager");
        new ServerManager(this);
    }

    public MainConfig getMainConfig() {
        return mainConfig;
    }

    public UUID getControllerId() {
        return controllerId;
    }

    private void heartbeat() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    NetCommandSCTSC netCommandSCTSC = new NetCommandSCTSC("heartbeat", mainConfig.privateIP, "*");
                    netCommandSCTSC.addArg("id", controllerId);
                    netCommandSCTSC.flush();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
                    }
                }
            }
        }).start();
    }

}
