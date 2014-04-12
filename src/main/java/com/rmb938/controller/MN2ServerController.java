package com.rmb938.controller;


import com.rmb938.controller.config.MainConfig;
import com.rmb938.controller.config.WorldConfig;
import com.rmb938.controller.database.DatabaseServerInfo;
import com.rmb938.controller.entity.Bungee;
import com.rmb938.controller.entity.Plugin;
import com.rmb938.controller.entity.World;
import com.rmb938.controller.jedis.NetCommandHandlerBTSC;
import com.rmb938.controller.jedis.NetCommandHandlerSCTSC;
import com.rmb938.controller.jedis.NetCommandHandlerSTSC;
import com.rmb938.controller.threads.ServerManager;
import com.rmb938.database.DatabaseAPI;
import com.rmb938.jedis.JedisManager;
import com.rmb938.jedis.net.command.servercontroller.NetCommandSCTSC;
import net.cubespace.Yamler.Config.InvalidConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MN2ServerController {

    private static final Logger logger = LogManager.getLogger(MN2ServerController.class.getName());

    public static void main(String[] args) {
        logger.info("Starting Server Controller");
        new MN2ServerController();
    }

    private final UUID controllerId;
    private final MainConfig mainConfig;
    private final ExecutorService executorService;
    private Bungee bungee;

    public MN2ServerController() {
        this.controllerId = UUID.randomUUID();
        executorService = Executors.newCachedThreadPool();

        mainConfig = new MainConfig("config.yml");
        try {
            mainConfig.init();
            mainConfig.save();
        } catch (InvalidConfigurationException e) {
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
            return;
        }

        if (mainConfig.controller_serverRam == 0) {
            logger.error("Please set the amount of memory before running the controller.");
            return;
        }

        logger.info("Checking Plugins");
        File pluginsFolder = new File("./plugins");
        if (pluginsFolder.exists() == false) {
            logger.error("There is no plugins directory! FIX THIS");
            return;
        }
        if (pluginsFolder.isDirectory() == false) {
            logger.error("There is no plugins directory! FIX THIS");
            return;
        }
        if (pluginsFolder.listFiles().length == 0) {
            logger.error("There are no plugins to load. FIX THIS!");
            return;
        }

        logger.info("Checking Spigot");
        File spigotFolder = new File("./server/spigot");
        if (spigotFolder.exists() == false) {
            logger.error("There is no spigot directory! FIX THIS");
            return;
        }
        if (spigotFolder.isDirectory() == false) {
            logger.error("There is no spigot directory! FIX THIS");
            return;
        }
        if (spigotFolder.listFiles().length == 0) {
            logger.error("There are no spigot files. FIX THIS!");
            return;
        }

        logger.info("Checking Bungee");
        File bungeeFolder = new File("./server/bungee");
        if (bungeeFolder.exists() == false) {
            logger.error("There is no bungee directory! FIX THIS");
            return;
        }
        if (bungeeFolder.isDirectory() == false) {
            logger.error("There is no bungee directory! FIX THIS");
            return;
        }
        if (bungeeFolder.listFiles().length == 0) {
            logger.error("There are no bungee files. FIX THIS!");
            return;
        }

        logger.info("Checking Worlds");
        File worldsFolder = new File("./worlds");
        if (worldsFolder.exists() == false) {
            logger.error("There is no worlds directory! FIX THIS");
            return;
        }
        if (worldsFolder.isDirectory() == false) {
            logger.error("There is no worldsFolder directory! FIX THIS");
            return;
        }
        if (worldsFolder.listFiles().length == 0) {
            logger.error("There are no worlds to load. FIX THIS!");
            return;
        }

        logger.info("Loading Worlds");
        for (File worldFolder : worldsFolder.listFiles()) {
            if (worldFolder.isDirectory() == false) {
                continue;
            }
            File worldConfigFile = new File(worldFolder, "config.yml");
            WorldConfig worldConfig = new WorldConfig(worldConfigFile, worldFolder.getName());
            try {
                worldConfig.init();
            } catch (InvalidConfigurationException e) {
                logger.error("Error loading world config for " + worldConfigFile.getName());
                logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
                continue;
            }
            World world = new World(worldFolder.getName(), worldConfig);
            logger.info("Loaded World "+world.getWorldName());
            World.getWorlds().put(world.getWorldName(), world);
        }
        if (World.getWorlds().size() == 0) {
            logger.error("No worlds were loaded. FIX THIS!");
            return;
        }

        logger.info("Loading Plugins");
        for (File pluginFolder : pluginsFolder.listFiles()) {
            if (pluginFolder.isDirectory() == false) {
                continue;
            }
            if (pluginFolder.listFiles().length == 0) {
                logger.warn("Cannot load plugin "+pluginFolder.getName()+" no plugin files.");
                continue;
            }
            Plugin plugin = new Plugin(pluginFolder.getName());
            Plugin.getPlugins().put(plugin.getPluginName(), plugin);
        }

        logger.info("Connecting to MySQL");
        DatabaseAPI.initializeMySQL(mainConfig.mySQL_userName, mainConfig.mySQL_password, mainConfig.mySQL_database, mainConfig.mySQL_address, mainConfig.mySQL_port);
        logger.info("Loading Server Info");
        DatabaseServerInfo databaseServerInfo = new DatabaseServerInfo(this);
        databaseServerInfo.loadServerInfo();
        bungee = databaseServerInfo.loadBungeeInfo();

        logger.info("Connecting to Redis");
        JedisManager.connectToRedis(mainConfig.redis_address);
        JedisManager.setUpDelegates();
        try {
            JedisManager.returnJedis(JedisManager.getJedis());
        } catch (Exception ex) {
            logger.error("Unable to connect to redis. Shutting down");
            return;
        }
        new NetCommandHandlerSCTSC(this);
        new NetCommandHandlerSTSC(this);
        new NetCommandHandlerBTSC(this);

        logger.info("Starting Heartbeat");
        executorService.submit(heartbeat());

        logger.warn("--------------------------------------------------");
        logger.warn("Multi-Node Minecraft Network is under the Creative Commons");
        logger.warn("Attribution-NonCommercial 4.0 International Public License");
        logger.warn("If you are using this in a commercial environment you MUST");
        logger.warn("obtain written permission.");
        logger.warn("--------------------------------------------------");

        logger.info("Sleeping for 20 seconds to reconnect to network");
        try {
            Thread.sleep(20000);//sleep for 20 seconds waiting for servers and controllers to reconnect
        } catch (InterruptedException e) {
            logger.error(logger.getMessageFactory().newMessage(e.getMessage()), e.fillInStackTrace());
        }

        if (bungee.getLastHeartBeat() == -1) {
            logger.info("Starting Bungee Instance");
            bungee.startBungee();
        }

        logger.info("Starting Server Manager");
        ServerManager serverManager = new ServerManager(this);
        executorService.submit(serverManager);
    }

    public Bungee getBungee() {
        return bungee;
    }

    public MainConfig getMainConfig() {
        return mainConfig;
    }

    public UUID getControllerId() {
        return controllerId;
    }

    private Runnable heartbeat() {
        return new Runnable() {
            @Override
            public void run() {
                while (Thread.interrupted() == false) {
                    logger.info("Sending beat");
                    NetCommandSCTSC netCommandSCTSC = new NetCommandSCTSC("heartbeat", mainConfig.privateIP, "*");
                    netCommandSCTSC.addArg("id", controllerId.toString());
                    netCommandSCTSC.flush();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        };
    }

    public void stop() {
        executorService.shutdownNow();
        JedisManager.shutDown();
        logger.info("Shutting Down...");
    }

}
