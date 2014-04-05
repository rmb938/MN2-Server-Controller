package com.rmb938.controller.config;


import net.cubespace.Yamler.Config.Comment;
import net.cubespace.Yamler.Config.Config;

import java.io.File;

public class MainConfig extends Config {

    public MainConfig() {
        CONFIG_HEADER = new String[]{"MN2 Server Controller Configuration File"};
        CONFIG_FILE = new File("config.yml");
    }

    @Comment("The maximum amount of servers that this controller can handle")
    public int serverAmount = 10;

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
