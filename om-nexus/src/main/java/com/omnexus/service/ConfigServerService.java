package com.omnexus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnexus.model.ClusterConfig;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class ConfigServerService {
    private static final String CONFIG_DIR = "configs";
    private static final String CONFIG_FILE_SUFFIX = "-config.json";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ClusterConfig> configCache = new HashMap<>(); // In-memory cache for active configs

    public ConfigServerService() {
        // Ensure config directory exists
        createConfigDirectory();
    }
    public void createConfigDirectory(){
        try{
            Path configPath = Paths.get(CONFIG_DIR);
            if(!Files.exists(configPath)){
                Files.createDirectory(configPath);
                System.out.println("Created config directory at " + configPath.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to create config directory: " + e.getMessage());
        }
    }
    public boolean saveClusterConfig(ClusterConfig config){
        try{
            String fileName = config.getClusterId() + CONFIG_FILE_SUFFIX;
            File configFile = new File(CONFIG_DIR,fileName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);

            // Update cache
            configCache.put(config.getClusterId(),config);
            System.out.println("Saved cluster config: " + configFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            System.err.println("Failed to save cluster config: " + e.getMessage());
            return false;
        }
    }
    public ClusterConfig loadClusterConfig(String clusterId) {
        // Check cache first
        if (configCache.containsKey(clusterId)) {
            return configCache.get(clusterId);
        }

        try {
            String fileName = clusterId + CONFIG_FILE_SUFFIX;
            File configFile = new File(CONFIG_DIR, fileName);

            if (!configFile.exists()) {
                System.err.println("Config file not found: " + configFile.getAbsolutePath());
                return null;
            }

            ClusterConfig config = objectMapper.readValue(configFile, ClusterConfig.class);

            // Update cache
            configCache.put(clusterId, config);

            System.out.println("Loaded cluster config: " + configFile.getAbsolutePath());
            return config;

        } catch (IOException e) {
            System.err.println("Failed to load cluster config: " + e.getMessage());
            return null;
        }
    }
    public boolean deleteClusterConfig(String clusterId) {
        try {
            String fileName = clusterId + CONFIG_FILE_SUFFIX;
            File configFile = new File(CONFIG_DIR, fileName);

            boolean deleted = configFile.delete();

            if (deleted) {
                // Remove from cache
                configCache.remove(clusterId);
                System.out.println("Deleted cluster config: " + configFile.getAbsolutePath());
            }

            return deleted;

        } catch (Exception e) {
            System.err.println("Failed to delete cluster config: " + e.getMessage());
            return false;
        }
    }
    public boolean configExists(String clusterId) {
        String fileName = clusterId + CONFIG_FILE_SUFFIX;
        File configFile = new File(CONFIG_DIR, fileName);
        return configFile.exists();
    }
    public void updateConfigCache(ClusterConfig config) {
        configCache.put(config.getClusterId(), config);
    }
}
