package cn.gtemc.craftengine;

import cn.gtemc.craftengine.hosts.ResourcePackHosts;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CraftengineHosts extends JavaPlugin {
    private static CraftengineHosts instance;

    @Override
    public void onLoad() {
        instance = this;
        ResourcePackHosts.init();
        getLogger().info("CraftEngine Hosts Extensions Loaded");
    }

    public Path dataFolderPath() {
        Path path = this.getDataFolder().toPath();
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (Exception e) {
                this.getLogger().warning("Failed to create data folder: " + e.getMessage());
            }
        }
        return path;
    }

    public static CraftengineHosts instance() {
        return instance;
    }
}
