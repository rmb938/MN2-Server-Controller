package com.rmb938.controller.database;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.controller.entity.Bungee;
import com.rmb938.controller.entity.Plugin;
import com.rmb938.controller.entity.ServerInfo;
import com.rmb938.controller.entity.World;
import com.rmb938.database.DatabaseAPI;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Map;

public class DatabaseServerInfo {

    private static final Logger logger = LogManager.getLogger(DatabaseServerInfo.class.getName());

    private final MN2ServerController serverController;

    public DatabaseServerInfo(MN2ServerController serverController) {
        this.serverController = serverController;
        createTable();
    }

    private void createTable() {
        if (DatabaseAPI.getMySQLDatabase().isTable("mn2_server_info") == false) {
            DatabaseAPI.getMySQLDatabase().createTable("CREATE TABLE IF NOT EXISTS `mn2_server_info` (" +
                    "`serverName` varchar(64) NOT NULL," +
                    "`maxPlayers` int(11) NOT NULL," +
                    "`minServers` int(11) NOT NULL," +
                    "`memory` int(11) NOT NULL," +
                    "PRIMARY KEY (`serverName`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=latin1;");
        }
        if (DatabaseAPI.getMySQLDatabase().isTable("mn2_server_info_plugins") == false) {
            DatabaseAPI.getMySQLDatabase().createTable("CREATE TABLE IF NOT EXISTS `mn2_server_info_plugins` (" +
                    "`serverName` varchar(64) NOT NULL," +
                    "`pluginName` varchar(64) NOT NULL," +
                    "UNIQUE KEY `serverName_2` (`serverName`,`pluginName`)," +
                    "KEY `serverName` (`serverName`)," +
                    "KEY `pluginName` (`pluginName`)," +
                    "FOREIGN KEY (`serverName`) REFERENCES `mn2_server_info` (`serverName`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=latin1;");
        }
        if (DatabaseAPI.getMySQLDatabase().isTable("mn2_bungee_plugins") == false) {
            DatabaseAPI.getMySQLDatabase().createTable("CREATE TABLE IF NOT EXISTS `mn2_bungee_plugins` (" +
                    "`pluginName` varchar(64) NOT NULL," +
                    "UNIQUE KEY `pluginId` (`pluginName`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=latin1;");
        }
        if (DatabaseAPI.getMySQLDatabase().isTable("mn2_server_info_worlds") == false) {
            DatabaseAPI.getMySQLDatabase().createTable("CREATE TABLE IF NOT EXISTS `mn2_server_info_worlds` (" +
                    "`serverName` varchar(64) NOT NULL," +
                    "`worldName` varchar(64) NOT NULL," +
                    "UNIQUE KEY `serverName_2` (`serverName`,`worldName`)," +
                    "KEY `serverName` (`serverName`)," +
                    "KEY `worldName` (`worldName`)," +
                    "FOREIGN KEY (`serverName`) REFERENCES `mn2_server_info` (`serverName`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=latin1;");
        }
    }

    public void loadServerInfo() {
        ServerInfo.getServerInfos().clear();
        ArrayList<Object> beans = DatabaseAPI.getMySQLDatabase().getBeansInfo("select serverName, maxPlayers, minServers, memory from `mn2_server_info`", new MapListHandler());
        for (Object obj : beans) {
            Map map = (Map) obj;
            String serverName = (String) map.get("serverName");
            int maxPlayers = (Integer) map.get("maxPlayers");
            int minServers = (Integer) map.get("minServers");
            int memory = (Integer) map.get("memory");
            ServerInfo serverInfo = new ServerInfo(serverName, maxPlayers, minServers, memory);
            ArrayList<Object> beans1 = DatabaseAPI.getMySQLDatabase().getBeansInfo("select pluginName from `mn2_server_info_plugins` where serverName='"+serverName+"'", new MapListHandler());
            for (Object obj1 : beans1) {
                Map map1 = (Map) obj1;
                String pluginName = (String) map1.get("pluginName");
                Plugin plugin = Plugin.getPlugins().get(pluginName);
                if (plugin == null) {
                    logger.warn("Could not load plugin "+pluginName+" into server plugin is null.");
                    continue;
                }
                serverInfo.getPlugins().add(plugin);
            }
            beans1 = DatabaseAPI.getMySQLDatabase().getBeansInfo("select worldName from `mn2_server_info_worlds` where serverName='"+serverName+"'", new MapListHandler());
            for (Object obj1 : beans1) {
                Map map1 = (Map) obj1;
                String worldName = (String) map1.get("worldName");
                World world = World.getWorlds().get(worldName);
                if (world == null) {
                    logger.warn("Could not load world "+worldName+" into server "+serverInfo.getServerName()+" world is null.");
                    continue;
                }
                serverInfo.getWorlds().add(world);
            }
            logger.info("Loaded "+serverName+" server info");
            ServerInfo.getServerInfos().put(serverName, serverInfo);
        }
    }

    public Bungee loadBungeeInfo() {
        Bungee bungee = new Bungee(serverController);
        ArrayList<Object> beans = DatabaseAPI.getMySQLDatabase().getBeansInfo("select pluginName from `mn2_bungee_plugins`", new MapListHandler());
        for (Object obj : beans) {
            Map map = (Map) obj;
            String pluginName = (String) map.get("pluginName");
            Plugin plugin = Plugin.getPlugins().get(pluginName);
            if (plugin == null) {
                logger.warn("Could not load plugin "+pluginName+" into bungee plugin is null.");
                continue;
            }
            bungee.getPlugins().add(plugin);
        }
        return bungee;
    }

}
