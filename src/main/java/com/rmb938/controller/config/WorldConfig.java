package com.rmb938.controller.config;

import net.cubespace.Yamler.Config.Comment;
import net.cubespace.Yamler.Config.Comments;
import net.cubespace.Yamler.Config.Config;

import java.io.File;

public class WorldConfig extends Config {

    public WorldConfig(File file, String worldName) {
        CONFIG_HEADER = new String[]{worldName+" Configuration File"};
        CONFIG_FILE = file;
    }

    @Comment("If this world should be the main world of the server")
    public boolean mainWorld = false;

    @Comment("The proper world name for the world. This is the name players may see.")
    public String worldName = "some world";

    @Comments({"The environment for the world",
            "Options: NORMAL, NETHER, THE_END"})
    public String environment = "NORMAL";

}
