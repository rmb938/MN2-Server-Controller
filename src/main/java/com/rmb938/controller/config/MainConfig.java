package com.rmb938.controller.config;


import net.cubespace.Yamler.Config.Comment;
import net.cubespace.Yamler.Config.Config;

import java.io.File;

public class MainConfig extends Config {

    public MainConfig(String fileLocation) {
        CONFIG_HEADER = new String[]{"MN2 Server Controller Configuration File"};
        CONFIG_FILE = new File(fileLocation);
    }

    @Comment("The total amount of ram(MB) dedicated to servers running on this node.")
    public int controller_serverRam = 6144;
    @Comment("If the server controller should make more servers based on network load")
    public boolean controller_loadBalance = true;

    @Comment("The public IP address of the controller")
    public String publicIP = "127.0.0.1";
    @Comment("The private IP address of the controller")
    public String privateIP = "192.168.1.2";

    @Comment("The IP address for the redis server")
    public String redis_address = "127.0.0.1";

    @Comment("The IP address from the mySQL server")
    public String mySQL_address = "127.0.0.1";
    @Comment("The port for the mySQL server")
    public int mySQL_port = 3306;
    @Comment("The username for the mySQL server")
    public String mySQL_userName = "userName";
    @Comment("The password for the mySQL server")
    public String mySQL_password = "password";
    @Comment("The database name for the mySQL server")
    public String mySQL_database = "database";

}
