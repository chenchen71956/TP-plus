package org.example.tp.tpPlus.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public class ConfigManager {
    private static final String CONFIG_DIR = "config/null_city/TP-plus";
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("null_city").resolve("TP-plus");
    
    public static void init() {
        createDirectories();
    }
    
    public static Path getConfigPath() {
        return CONFIG_PATH;
    }
    
    public static File getConfigFile(String filename) {
        return CONFIG_PATH.resolve(filename).toFile();
    }
    
    private static void createDirectories() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                Files.createDirectories(CONFIG_PATH);
            }
        } catch (IOException e) {
            System.err.println("无法创建配置目录: " + e.getMessage());
        }
    }
} 