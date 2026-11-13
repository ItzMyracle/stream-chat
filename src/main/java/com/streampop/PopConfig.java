package com.streampop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Saved to .minecraft/config/streampop.json */
public class PopConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("streampop.json");

    /** Twitch channel name, e.g. "shroud" */
    public String channel = "";
    public boolean autoConnect = false;

    public boolean hud = true;
    /** 0 = top left, 1 = top right, 2 = bottom left, 3 = bottom right */
    public int corner = 1;
    /** Black background strength, 15-255. Lower = more see-through. */
    public int opacity = 105;
    public int maxCards = 5;
    public int durationSeconds = 8;
    public int cardWidth = 170;
    public int maxLines = 4;
    public int marginX = 6;
    public int marginY = 6;

    public boolean badges = true;
    public boolean sound = false;
    public boolean hideCommands = true;

    public List<String> banwords = new ArrayList<>();
    public List<String> blockedUsers = new ArrayList<>();

    public static PopConfig load() {
        try {
            if (Files.exists(FILE)) {
                PopConfig c = GSON.fromJson(Files.readString(FILE), PopConfig.class);
                if (c != null) return c;
            }
        } catch (Exception e) {
            StreamPopMod.LOGGER.warn("Could not read config, using defaults", e);
        }
        PopConfig c = new PopConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this));
        } catch (Exception e) {
            StreamPopMod.LOGGER.warn("Could not save config", e);
        }
    }
}
