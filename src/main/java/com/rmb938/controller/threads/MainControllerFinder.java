package com.rmb938.controller.threads;

import com.rmb938.controller.entity.RemoteController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainControllerFinder implements Runnable {

    private static final Logger logger = LogManager.getLogger(ConsoleInput.class.getName());

    public void run() {
        while (true) {
            if (RemoteController.getMainController() == null) {
                int minIP = Integer.MAX_VALUE;
                for (String IP : RemoteController.getRemoteControllers().keySet()) {
                    RemoteController remoteController = RemoteController.getRemoteControllers().get(IP);
                    if (remoteController.getLastHeartbeat() + 60000 < System.currentTimeMillis() && remoteController.getLastHeartbeat() > 0) {
                        continue;
                    }
                }
            } else if (RemoteController.getMainController().getLastHeartbeat() + 60000 < System.currentTimeMillis()) {
                RemoteController.setMainController(null);
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void startControllerFinder() {
        logger.info("Starting Main Controller Finder");
        Thread controllerFinder = new Thread(this);
        controllerFinder.start();
    }

}
