package com.rmb938.controller.entity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteController {

    private static ConcurrentHashMap<String, RemoteController> remoteControllers = new ConcurrentHashMap<>();

    public static RemoteController getMainController() {
        RemoteController controller = null;
        UUID minId = null;
        for (RemoteController remoteController : getRemoteControllers().values()) {
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
    private final UUID controllerID;

    public RemoteController(String IP, UUID controllerID) {
        this.IP = IP;
        this.controllerID = controllerID;
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

}
