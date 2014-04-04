package com.rmb938.controller.threads;

import jline.TerminalSupport;
import jline.console.ConsoleReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

public class ConsoleInput implements Runnable {

    private static final Logger logger = LogManager.getLogger(ConsoleInput.class.getName());

    public void run() {
        try {
            ConsoleReader consoleReader = new ConsoleReader(new FileInputStream(FileDescriptor.in), System.out, new TerminalSupport(true) {
            });
            consoleReader.setPrompt("> ");
            String line;
            while ((line = consoleReader.readLine()) != null) {

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startConsoleInput() {
        logger.info("Starting Console Input");
        Thread consoleInput = new Thread(this);
        consoleInput.start();
    }

}
