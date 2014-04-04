package com.rmb938.controller.entity;

import java.util.concurrent.ConcurrentHashMap;

public class RemoteController {

    private static ConcurrentHashMap<String, RemoteController> remoteControllers = new ConcurrentHashMap<>();
    private static RemoteController mainController = null;

    public static RemoteController getMainController() {
        return mainController;
    }

    public static void setMainController(RemoteController mainController) {
        RemoteController.mainController = mainController;
    }

    public static ConcurrentHashMap<String, RemoteController> getRemoteControllers() {
        return remoteControllers;
    }

    private final String IP;
    private long lastHeartbeat = -1;

    public RemoteController(String IP) {
        this.IP = IP;
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

}
