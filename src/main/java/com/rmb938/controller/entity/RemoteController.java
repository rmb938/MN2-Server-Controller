package com.rmb938.controller.entity;

import com.rmb938.jedis.JedisManager;
import org.json.JSONException;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteController {

    private static ConcurrentHashMap<String, RemoteController> remoteControllers = new ConcurrentHashMap<>();

    public static RemoteController getMainController() {
        RemoteController controller = null;
        UUID minId = null;
        for (RemoteController remoteController : RemoteController.getRemoteControllers().values()) {
            if (remoteController.getLastHeartbeat() + 60000 < System.currentTimeMillis()) {
                continue;
            }
            if (minId == null) {
                minId = remoteController.getControllerID();
            } else {
                minId = minId.compareTo(remoteController.getControllerID()) == -1 ? minId : remoteController.getControllerID();
            }
            if (minId == remoteController.getControllerID()) {
                controller = remoteController;
            }
        }
        return controller;
    }

    public static ConcurrentHashMap<String, RemoteController> getRemoteControllers() {
        return remoteControllers;
    }

    private final String IP;
    private long lastHeartbeat = -1;
    private UUID controllerID;
    private final int ram;

    public RemoteController(String IP, UUID controllerID, int ram) {
        this.IP = IP;
        this.controllerID = controllerID;
        this.ram = ram;
    }

    public void setControllerID(UUID controllerID) {
        this.controllerID = controllerID;
    }

    public int getRam() {
        return ram;
    }

    public UUID getControllerID() {
        return controllerID;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public String getIP() {
        return IP;
    }

    public int getUsedRam() {
        int usedRam = 0;
        Jedis jedis = JedisManager.getJedis();
        Set<String> keys = jedis.keys("server.*.*");
        for (String key : keys) {
            String data = jedis.get(key);
            if (data != null) {
                try {
                    JSONObject jsonObject = new JSONObject(data);
                    String ip = jsonObject.getString("serverIP");
                    String serverName = jsonObject.getString("serverName");
                    if (ip.equalsIgnoreCase(IP) == false) {
                        continue;
                    }
                    ServerInfo serverInfo = ServerInfo.getServerInfos().get(serverName);
                    usedRam += serverInfo.getMemory();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        JedisManager.returnJedis(jedis);
        return usedRam;
    }

}
