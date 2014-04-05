package com.rmb938.controller.database;

import com.rmb938.controller.MN2ServerController;
import com.rmb938.controller.entity.Bungee;
import com.rmb938.controller.entity.Plugin;
import com.rmb938.controller.entity.ServerInfo;
import com.rmb938.database.DatabaseAPI;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;

import java.util.ArrayList;
import java.util.Map;

public class DatabaseServerInfo {

    private final MN2ServerController serverController;

    public DatabaseServerInfo(MN2ServerController serverController) {
        this.serverController = serverController;
        createTable();
    }

    public void createTable() {
        if (DatabaseAPI.getMySQLDatabase().isTable("mn2_server_info") == false) {
            DatabaseAPI.getMySQLDatabase().createTable("CREATE TABLE IF NOT EXISTS `mn2_server_info` (" +
                    "`serverId` int(11) NOT NULL AUTO_INCREMENT," +
                    "`serverName` varchar(64) NOT NULL," +
                    "`maxPlayers` int(11) NOT NULL," +
                    "`minServers` int(11) NOT NULL," +
                    "PRIMARY KEY (`serverId`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=latin1 AUTO_INCREMENT=1 ;");
        }
        if (DatabaseAPI.getMySQLDatabase().isTable("mn2_server_plugins") == false) {
            DatabaseAPI.getMySQLDatabase().createTable("CREATE TABLE IF NOT EXISTS `mn2_server_plugins` (" +
                    "`pluginId` int(11) NOT NULL," +
                    "`pluginName` varchar(64) NOT NULL," +
                    "UNIQUE KEY `pluginName` (`pluginName`)," +
                    "KEY `pluginId` (`pluginId`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=latin1;");
        }
        if (DatabaseAPI.getMySQLDatabase().isTable("mn2_server_info_plugins") == false) {
            DatabaseAPI.getMySQLDatabase().createTable("CREATE TABLE IF NOT EXISTS `mn2_server_info_plugins` (" +
                    "`serverId` int(11) NOT NULL," +
                    "`pluginId` int(11) NOT NULL," +
                    "UNIQUE KEY `serverId_2` (`serverId`,`pluginId`)," +
                    "KEY `serverId` (`serverId`)," +
                    "KEY `pluginId` (`pluginId`)" +
                    "FOREIGN KEY (`pluginId`) REFERENCES `mn2_server_plugins` (`pluginId`)," +
                    "FOREIGN KEY (`serverId`) REFERENCES `mn2_server_info` (`serverId`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=latin1;");
        }
        if (DatabaseAPI.getMySQLDatabase().isTable("mn2_bungee_plugins") == false) {
            DatabaseAPI.getMySQLDatabase().createTable("CREATE TABLE IF NOT EXISTS `mn2_bungee_plugins` (" +
                    "`pluginId` int(11) NOT NULL," +
                    "UNIQUE KEY `pluginId` (`pluginId`)" +
                    "FOREIGN KEY (`pluginId`) REFERENCES `mn2_server_plugins` (`pluginId`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=latin1;");
        }
    }

    public void loadPlugins() {
        ArrayList<Object> beans = DatabaseAPI.getMySQLDatabase().getBeansInfo("select pluginId, pluginName from `mn2_server_plugins`", new BeanListHandler<>(Plugin.class));
        for (Object obj : beans) {
            Plugin plugin = (Plugin) obj;
            Plugin.getPlugins().put(plugin.getPluginId(), plugin);
        }
    }

    public void loadServerInfo() {
        ArrayList<Object> beans = DatabaseAPI.getMySQLDatabase().getBeansInfo("select serverId, serverName, maxPlayers, minServers from `mn2_server_info`", new MapListHandler());
        for (Object obj : beans) {
            Map map = (Map) obj;
            int serverId = (Integer) map.get("serverId");
            String serverName = (String) map.get("serverName");
            int maxPlayers = (Integer) map.get("maxPlayers");
            int minServers = (Integer) map.get("minServers");
            ServerInfo serverInfo = new ServerInfo(serverName, maxPlayers, minServers);
            ArrayList<Object> beans1 = DatabaseAPI.getMySQLDatabase().getBeansInfo("select pluginId from `mn2_server_info_plugins` where serverId='"+serverId+"'", new MapListHandler());
            for (Object obj1 : beans1) {
                Map map1 = (Map) obj1;
                int pluginId = (Integer) map1.get("pluginId");
                serverInfo.getPlugins().add(Plugin.getPlugins().get(pluginId));
            }
            ServerInfo.getServerInfos().put(serverName, serverInfo);
        }
    }

    public Bungee loadBungeeInfo() {
        Bungee bungee = new Bungee(serverController);
        ArrayList<Object> beans = DatabaseAPI.getMySQLDatabase().getBeansInfo("select pluginId from `mn2_bungee_plugins`", new MapListHandler());
        for (Object obj : beans) {
            Map map = (Map) obj;
            int pluginId = (Integer) map.get("pluginId");
            bungee.getPlugins().add(Plugin.getPlugins().get(pluginId));
        }
        return bungee;
    }

}
